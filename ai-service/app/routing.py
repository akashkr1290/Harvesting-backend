"""Vehicle route optimisation with Google OR-Tools.

Solves a capacitated vehicle routing problem with optional time windows and
optional pickup-and-delivery pairs (collect at a farm, drop at a buyer, same
truck, farm first). Several trucks share the work; stops that cannot be served
(too heavy, outside every time window) are reported instead of failing.

Distances: an OSRM server when OSRM_URL is set (real road km), otherwise
straight-line distance multiplied by ROAD_FACTOR.
"""
from __future__ import annotations

import logging
import math
from dataclasses import dataclass, field

from .config import settings

log = logging.getLogger(__name__)

SCALE = 1000  # OR-Tools works in integers: metres and kg*1000 would overflow nothing at our sizes
DROP_PENALTY = 10_000_000


@dataclass
class Stop:
    id: str
    lat: float
    lng: float
    name: str = ""
    demand_kg: float = 0.0
    service_min: int = 10
    window: tuple[int, int] | None = None  # minutes from route start
    deliver_to: str | None = None


@dataclass
class Vehicle:
    id: str
    capacity_kg: float


@dataclass
class RoutePlan:
    vehicle_id: str
    stop_ids: list[str]
    distance_km: float
    duration_min: float
    load_kg: float


@dataclass
class RoutingResult:
    engine: str
    distance_source: str
    routes: list[RoutePlan]
    unassigned: list[str]
    total_km: float
    baseline_km: float
    saved_km: float
    saved_percent: float
    notes: list[str] = field(default_factory=list)


def haversine_km(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lng1 = map(math.radians, a)
    lat2, lng2 = map(math.radians, b)
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lng2 - lng1) / 2) ** 2
    return 6371.0 * 2 * math.atan2(math.sqrt(h), math.sqrt(1 - h))


def distance_matrix_km(points: list[tuple[float, float]]) -> tuple[list[list[float]], str]:
    """Road km between every pair. OSRM when configured, straight line x ROAD_FACTOR otherwise."""
    if settings.osrm_url and len(points) <= 100:
        try:
            import httpx

            coords = ";".join(f"{lng:.6f},{lat:.6f}" for lat, lng in points)
            url = f"{settings.osrm_url.rstrip('/')}/table/v1/driving/{coords}?annotations=distance"
            r = httpx.get(url, timeout=8.0)
            r.raise_for_status()
            data = r.json()
            if data.get("code") == "Ok" and data.get("distances"):
                m = [[(d if d is not None else 0.0) / 1000.0 for d in row] for row in data["distances"]]
                return m, "osrm-road"
        except Exception as e:
            log.warning("OSRM distance lookup failed (%s); using straight-line estimate", e)
    m = [[haversine_km(a, b) * (settings.road_factor if a != b else 0.0) for b in points] for a in points]
    return m, f"straight-line x{settings.road_factor}"


def _route_km(order: list[int], dist: list[list[float]]) -> float:
    return sum(dist[a][b] for a, b in zip(order, order[1:]))


def _baseline_km(stops: list[Stop], vehicles: list[Vehicle], dist, return_to_depot: bool) -> float:
    """Unoptimised plan: stops in the order given, each truck filled in turn until it is full."""
    total = 0.0
    caps = [v.capacity_kg for v in vehicles] or [float("inf")]
    vi, load, route = 0, 0.0, [0]
    for idx, s in enumerate(stops, start=1):
        if load + max(s.demand_kg, 0) > caps[min(vi, len(caps) - 1)] and len(route) > 1:
            total += _route_km(route + ([0] if return_to_depot else []), dist)
            vi, load, route = vi + 1, 0.0, [0]
        route.append(idx)
        load += max(s.demand_kg, 0)
    if len(route) > 1:
        total += _route_km(route + ([0] if return_to_depot else []), dist)
    return total


def optimise(depot: Stop, stops: list[Stop], vehicles: list[Vehicle], return_to_depot: bool = True,
             time_limit_s: int | None = None) -> RoutingResult:
    from ortools.constraint_solver import pywrapcp, routing_enums_pb2

    if not vehicles:
        vehicles = [Vehicle("vehicle-1", sum(max(s.demand_kg, 0) for s in stops) or 1.0)]
    notes: list[str] = []
    points = [(depot.lat, depot.lng)] + [(s.lat, s.lng) for s in stops]
    dist, source = distance_matrix_km(points)
    n = len(points)
    # Open routes end at a virtual node that costs nothing to reach.
    end_node = n if not return_to_depot else 0
    size = n + (0 if return_to_depot else 1)

    def km(i: int, j: int) -> float:
        if i >= n or j >= n:
            return 0.0
        return dist[i][j]

    id_to_index = {s.id: i for i, s in enumerate(stops, start=1)}
    demands = [0.0] + [s.demand_kg for s in stops] + ([0.0] if not return_to_depot else [])
    for s in stops:
        if s.deliver_to and s.deliver_to in id_to_index:
            j = id_to_index[s.deliver_to]
            if demands[j] == 0:
                demands[j] = -abs(s.demand_kg)

    manager = pywrapcp.RoutingIndexManager(size, len(vehicles), [0] * len(vehicles), [end_node] * len(vehicles))
    routing = pywrapcp.RoutingModel(manager)

    def dist_cb(fi, ti):
        return int(km(manager.IndexToNode(fi), manager.IndexToNode(ti)) * SCALE)

    transit = routing.RegisterTransitCallback(dist_cb)
    routing.SetArcCostEvaluatorOfAllVehicles(transit)
    routing.AddDimension(transit, 0, 10_000 * SCALE, True, "Distance")

    def demand_cb(fi):
        node = manager.IndexToNode(fi)
        return int(round(demands[node] * SCALE)) if node < len(demands) else 0

    dem = routing.RegisterUnaryTransitCallback(demand_cb)
    routing.AddDimensionWithVehicleCapacity(
        dem, 0, [int(v.capacity_kg * SCALE) for v in vehicles], True, "Capacity")

    speed = max(settings.avg_speed_kmph, 1.0)
    service = [0] + [s.service_min for s in stops] + ([0] if not return_to_depot else [])

    def time_cb(fi, ti):
        a, b = manager.IndexToNode(fi), manager.IndexToNode(ti)
        return int(km(a, b) / speed * 60) + (service[a] if a < len(service) else 0)

    time_transit = routing.RegisterTransitCallback(time_cb)
    horizon = 24 * 60
    routing.AddDimension(time_transit, 24 * 60, horizon, False, "Time")
    time_dim = routing.GetDimensionOrDie("Time")
    for i, s in enumerate(stops, start=1):
        if s.window:
            start, end = max(0, int(s.window[0])), min(horizon, int(s.window[1]))
            if start <= end:
                time_dim.CumulVar(manager.NodeToIndex(i)).SetRange(start, end)

    dist_dim = routing.GetDimensionOrDie("Distance")
    for s in stops:
        if s.deliver_to and s.deliver_to in id_to_index:
            p = manager.NodeToIndex(id_to_index[s.id])
            d = manager.NodeToIndex(id_to_index[s.deliver_to])
            routing.AddPickupAndDelivery(p, d)
            routing.solver().Add(routing.VehicleVar(p) == routing.VehicleVar(d))
            routing.solver().Add(dist_dim.CumulVar(p) <= dist_dim.CumulVar(d))

    for i in range(1, n):
        routing.AddDisjunction([manager.NodeToIndex(i)], DROP_PENALTY)

    params = pywrapcp.DefaultRoutingSearchParameters()
    params.first_solution_strategy = routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC
    params.local_search_metaheuristic = routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
    params.time_limit.FromSeconds(max(1, int(time_limit_s or settings.route_time_limit_s)))
    solution = routing.SolveWithParameters(params)
    if solution is None:
        raise RuntimeError("No route found for these stops and vehicles")

    routes: list[RoutePlan] = []
    visited: set[int] = set()
    for v_idx, v in enumerate(vehicles):
        index = routing.Start(v_idx)
        order = [0]
        load = 0.0
        while not routing.IsEnd(index):
            index = solution.Value(routing.NextVar(index))
            node = manager.IndexToNode(index)
            if 0 < node < n:
                order.append(node)
                visited.add(node)
                load += max(demands[node], 0.0)
        if return_to_depot:
            order.append(0)
        if len(order) <= (2 if return_to_depot else 1):
            continue
        km_total = _route_km(order, dist)
        minutes = km_total / speed * 60 + sum(service[i] for i in order[1:-1] if i < len(service))
        routes.append(RoutePlan(
            vehicle_id=v.id,
            stop_ids=[stops[i - 1].id for i in order if 0 < i < n],
            distance_km=round(km_total, 2),
            duration_min=round(minutes, 1),
            load_kg=round(load, 2),
        ))

    unassigned = [stops[i - 1].id for i in range(1, n) if i not in visited]
    if unassigned:
        notes.append(f"{len(unassigned)} stop(s) could not fit any vehicle's capacity or time window.")
    total = round(sum(r.distance_km for r in routes), 2)
    baseline = round(_baseline_km(stops, vehicles, dist, return_to_depot), 2)
    saved = round(max(0.0, baseline - total), 2) if not unassigned else 0.0
    pct = round(saved / baseline * 100, 1) if baseline > 0 else 0.0
    return RoutingResult(engine="ortools-cvrp", distance_source=source, routes=routes, unassigned=unassigned,
                         total_km=total, baseline_km=baseline, saved_km=saved, saved_percent=pct, notes=notes)
