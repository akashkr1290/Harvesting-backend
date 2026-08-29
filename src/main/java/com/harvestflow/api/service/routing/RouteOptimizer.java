package com.harvestflow.api.service.routing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, stateless route optimization. No persistence, no HTTP, no
 * external mapping provider — an isolated abstraction (per the Phase 3
 * "AI service architecture" requirement) so the optimization strategy
 * can be swapped out later (e.g. for a real routing API) without
 * touching {@code RouteOptimizationService} or any controller.
 *
 * Algorithm: nearest-neighbour construction from the pickup, followed by
 * 2-opt local search to remove crossing/inefficient segments. Both are
 * standard, well-understood heuristics — deliberately not an exact
 * solver (TSP is NP-hard), which is the right trade-off for an MVP with
 * a handful of stops and no need for provably-optimal routes.
 */
@Component
public class RouteOptimizer {

    /** Above this many stops, 2-opt's O(n^2) passes are skipped and the nearest-neighbour order is kept as-is. */
    private static final int TWO_OPT_STOP_LIMIT = 25;

    public record Point(String name, double lat, double lng) {
    }

    public record Result(List<Point> orderedStops, double totalDistanceKm) {
    }

    /** Distance of the stops in the order given, with no reordering — used as the "original" baseline for comparison. */
    public double totalDistanceInGivenOrder(List<Point> stops) {
        return totalDistance(stops);
    }

    /** Optimizes visiting order for everything after index 0 (the pickup, which always stays first). */
    public Result optimize(List<Point> stops) {
        if (stops.size() <= 2) {
            return new Result(stops, totalDistance(stops));
        }

        List<Point> ordered = nearestNeighbourOrder(stops);
        if (stops.size() <= TWO_OPT_STOP_LIMIT) {
            ordered = twoOpt(ordered);
        }
        return new Result(ordered, totalDistance(ordered));
    }

    private List<Point> nearestNeighbourOrder(List<Point> stops) {
        List<Point> remaining = new ArrayList<>(stops.subList(1, stops.size()));
        List<Point> ordered = new ArrayList<>();
        Point current = stops.get(0);
        ordered.add(current);

        while (!remaining.isEmpty()) {
            Point nearest = null;
            double bestDist = Double.MAX_VALUE;
            for (Point candidate : remaining) {
                double d = haversineKm(current, candidate);
                if (d < bestDist) {
                    bestDist = d;
                    nearest = candidate;
                }
            }
            ordered.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }
        return ordered;
    }

    /** Classic 2-opt: repeatedly reverse a segment if doing so shortens the total route, until no improvement is found. */
    private List<Point> twoOpt(List<Point> route) {
        List<Point> best = new ArrayList<>(route);
        boolean improved = true;

        while (improved) {
            improved = false;
            for (int i = 1; i < best.size() - 1; i++) {
                for (int j = i + 1; j < best.size(); j++) {
                    List<Point> candidate = twoOptSwap(best, i, j);
                    if (totalDistance(candidate) < totalDistance(best) - 1e-9) {
                        best = candidate;
                        improved = true;
                    }
                }
            }
        }
        return best;
    }

    private List<Point> twoOptSwap(List<Point> route, int i, int j) {
        List<Point> result = new ArrayList<>(route.subList(0, i));
        List<Point> reversed = new ArrayList<>(route.subList(i, j + 1));
        java.util.Collections.reverse(reversed);
        result.addAll(reversed);
        result.addAll(route.subList(j + 1, route.size()));
        return result;
    }

    private double totalDistance(List<Point> stops) {
        double total = 0.0;
        for (int i = 0; i < stops.size() - 1; i++) {
            total += haversineKm(stops.get(i), stops.get(i + 1));
        }
        return total;
    }

    /** Great-circle distance in km — good enough for an MVP road-distance proxy without a mapping provider. */
    public double haversineKm(Point a, Point b) {
        final double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        return earthRadiusKm * c;
    }
}
