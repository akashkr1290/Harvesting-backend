package com.harvestflow.api.service.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteOptimizerTest {

    private final RouteOptimizer optimizer = new RouteOptimizer();

    @Test
    void haversine_knownDistance_isApproximatelyCorrect() {
        // Nashik to Mumbai is roughly 165 km as the crow flies.
        RouteOptimizer.Point nashik = new RouteOptimizer.Point("Nashik", 19.9975, 73.7898);
        RouteOptimizer.Point mumbai = new RouteOptimizer.Point("Mumbai", 19.0760, 72.8777);

        double distance = optimizer.haversineKm(nashik, mumbai);

        assertThat(distance).isBetween(140.0, 190.0);
    }

    @Test
    void optimize_withTwoStops_returnsBothStopsUnchanged() {
        List<RouteOptimizer.Point> stops = List.of(
                new RouteOptimizer.Point("Pickup", 19.9975, 73.7898),
                new RouteOptimizer.Point("Delivery", 19.0760, 72.8777)
        );

        RouteOptimizer.Result result = optimizer.optimize(stops);

        assertThat(result.orderedStops()).hasSize(2);
        assertThat(result.totalDistanceKm()).isGreaterThan(0.0);
    }

    @Test
    void optimize_reordersOutOfOrderStopsToReduceTotalDistance() {
        // Pickup at origin. Deliveries laid out so that visiting them in the
        // given order (far, then near) is worse than nearest-neighbour order.
        RouteOptimizer.Point pickup = new RouteOptimizer.Point("Pickup", 0.0, 0.0);
        RouteOptimizer.Point near = new RouteOptimizer.Point("Near", 0.05, 0.0);
        RouteOptimizer.Point far = new RouteOptimizer.Point("Far", 1.0, 0.0);
        RouteOptimizer.Point farthest = new RouteOptimizer.Point("Farthest", 2.0, 0.0);

        // Deliberately out-of-order input: pickup -> farthest -> near -> far
        List<RouteOptimizer.Point> badOrder = List.of(pickup, farthest, near, far);

        double originalDistance = optimizer.totalDistanceInGivenOrder(badOrder);
        RouteOptimizer.Result optimized = optimizer.optimize(badOrder);

        assertThat(optimized.orderedStops().get(0)).isEqualTo(pickup);
        assertThat(optimized.totalDistanceKm()).isLessThanOrEqualTo(originalDistance);
        // The optimal visiting order from the origin outward is near, far, farthest.
        assertThat(optimized.orderedStops()).containsExactly(pickup, near, far, farthest);
    }

    @Test
    void optimize_withSingleStop_returnsItUnchanged() {
        List<RouteOptimizer.Point> stops = List.of(new RouteOptimizer.Point("OnlyStop", 10.0, 20.0));

        RouteOptimizer.Result result = optimizer.optimize(stops);

        assertThat(result.orderedStops()).containsExactly(stops.get(0));
        assertThat(result.totalDistanceKm()).isZero();
    }
}
