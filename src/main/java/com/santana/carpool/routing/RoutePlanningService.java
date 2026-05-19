package com.santana.carpool.routing;

import com.santana.carpool.api.dto.TripType;
import com.santana.carpool.domain.Stop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoutePlanningService {
    private static final Logger log = LoggerFactory.getLogger(RoutePlanningService.class);
    private final GoogleRoutesService routesService;
    private final NearestNeighborRoutePlanner routePlanner;

    @Value("${googleMaps.fallbackKmh:35.0}")
    private double fallbackKmh;

    public RoutePlanningService(GoogleRoutesService routesService, NearestNeighborRoutePlanner routePlanner) {
        this.routesService = routesService;
        this.routePlanner = routePlanner;
    }

    public PlannedRoute planRoute(TripType tripType, Stop driver, Stop office, List<Stop> pickups) {
        log.info("Planning {} route with {} pickups", tripType, pickups.size());
        Stop routeStart = tripType == TripType.EVENING_TO_HOME ? office : driver;
        Stop routeEnd = tripType == TripType.EVENING_TO_HOME ? driver : office;

        Map<String, RouteLegMetrics> legCache = new HashMap<>();
        List<Stop> ordered = optimizePickupOrderRoadAware(routeStart, pickups, routeEnd, legCache);
        RouteTotals totals = calculateTotalsRoadAware(routeStart, ordered, routeEnd, legCache);
        log.info("Route planned: {}km, {}s total", totals.distanceKm(), totals.durationSeconds());
        return new PlannedRoute(ordered, totals.distanceKm(), totals.durationSeconds());
    }

    private List<Stop> optimizePickupOrderRoadAware(
            Stop driverStart,
            List<Stop> pickups,
            Stop office,
            Map<String, RouteLegMetrics> legCache
    ) {
        // Algorithm selection based on problem size.
        // With 5-seater vehicles, max 4 pickups = 24 permutations (always exhaustive, <100ms).
        // Greedy fallback for >8 pickups kept for future extensibility (larger vehicle fleet,
        // charter services, corporate shuttles), but rarely triggered in practice.
        if (pickups.size() <= 8) {
            log.debug("Using exhaustive search for {} pickups", pickups.size());
            return optimizeByExhaustiveSearch(driverStart, pickups, office, legCache);
        }
        log.debug("Using greedy fallback for {} pickups (>8)", pickups.size());
        return planPickupOrderGreedyRoadAware(driverStart, pickups, legCache);
    }

    private List<Stop> optimizeByExhaustiveSearch(
            Stop driverStart,
            List<Stop> pickups,
            Stop office,
            Map<String, RouteLegMetrics> legCache
    ) {
        // Work on a copy so caller order is never mutated by recursive swaps.
        List<Stop> working = new ArrayList<>(pickups);
        // Holds the best pickup order discovered so far.
        List<Stop> bestOrder = new ArrayList<>();
        // Start with an effectively "infinite" best duration so first full route always wins initially.
        BestRoute best = new BestRoute(Double.MAX_VALUE);

        permuteAndEvaluate(driverStart, office, working, 0, legCache, best, bestOrder);
        return bestOrder;
    }

    private void permuteAndEvaluate(
            Stop driverStart,
            Stop office,
            List<Stop> pickups,
            int index,
            Map<String, RouteLegMetrics> legCache,
            BestRoute best,
            List<Stop> bestOrder
    ) {
        // Base case: index reached the end, so current list is one complete pickup order.
        if (index == pickups.size()) {
            RouteTotals totals = calculateTotalsRoadAware(driverStart, pickups, office, legCache);
            if (totals.durationSeconds() < best.objective) {
                best.objective = totals.durationSeconds();
                bestOrder.clear();
                // Copy current full permutation as the new best route.
                bestOrder.addAll(pickups);
            }
            return;
        }

        // Try every candidate stop in the current index position.
        for (int i = index; i < pickups.size(); i++) {
            // Choose: place candidate i at current index.
            swap(pickups, index, i);
            // Explore: fill the next index recursively.
            permuteAndEvaluate(driverStart, office, pickups, index + 1, legCache, best, bestOrder);
            // Un-choose: restore previous order before trying next candidate.
            swap(pickups, index, i);
        }
    }

    private void swap(List<Stop> list, int i, int j) {
        // In-place swap used by recursion to generate permutations.
        Stop temp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, temp);
    }

    private List<Stop> planPickupOrderGreedyRoadAware(
            Stop driverStart,
            List<Stop> pickups,
            Map<String, RouteLegMetrics> legCache
    ) {
        // Greedy strategy: repeatedly choose the next stop with shortest driving duration.
        List<Stop> remaining = new ArrayList<>(pickups);
        List<Stop> ordered = new ArrayList<>();

        Stop current = driverStart;
        while (!remaining.isEmpty()) {
            Stop next = findNearestByDrivingDuration(current, remaining, legCache);
            ordered.add(next);
            remaining.remove(next);
            current = next;
        }

        return ordered;
    }

    private Stop findNearestByDrivingDuration(
            Stop from,
            List<Stop> candidates,
            Map<String, RouteLegMetrics> legCache
    ) {
        Stop bestStop = null;
        long bestDuration = Long.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;

        for (Stop candidate : candidates) {
            RouteLegMetrics leg = getLegMetrics(from, candidate, legCache);
            // Tie-break on distance to keep ordering stable when durations are equal.
            if (leg.durationSeconds() < bestDuration
                    || (leg.durationSeconds() == bestDuration && leg.distanceKm() < bestDistance)) {
                bestStop = candidate;
                bestDuration = leg.durationSeconds();
                bestDistance = leg.distanceKm();
            }
        }

        return bestStop;
    }

    private RouteTotals calculateTotalsRoadAware(
            Stop start,
            List<Stop> orderedPickups,
            Stop office,
            Map<String, RouteLegMetrics> legCache
    ) {
        // Sum all route legs: start -> pickups... -> final destination.
        double totalDistanceKm = 0.0;
        long totalDurationSeconds = 0L;
        Stop current = start;

        for (Stop pickup : orderedPickups) {
            RouteLegMetrics leg = getLegMetrics(current, pickup, legCache);
            totalDistanceKm += leg.distanceKm();
            totalDurationSeconds += leg.durationSeconds();
            current = pickup;
        }

        RouteLegMetrics finalLeg = getLegMetrics(current, office, legCache);
        totalDistanceKm += finalLeg.distanceKm();
        totalDurationSeconds += finalLeg.durationSeconds();

        return new RouteTotals(totalDistanceKm, totalDurationSeconds);
    }

    private RouteLegMetrics getLegMetrics(Stop from, Stop to, Map<String, RouteLegMetrics> legCache) {
        String key = from.id() + "->" + to.id();
        RouteLegMetrics cached = legCache.get(key);
        if (cached != null) {
            return cached;
        }

        RouteLegMetrics value;
        try {
            value = routesService.computeDrivingLeg(from.coordinates(), to.coordinates());
        } catch (RuntimeException ex) {
            double distanceKm = routePlanner.haversineKm(from.coordinates(), to.coordinates());
            long durationSeconds = Math.max(1L, Math.round((distanceKm / fallbackKmh) * 3600.0));
            log.warn("Routes API failed for {} -> {}, using Haversine fallback ({}km)", from.id(), to.id(), distanceKm, ex);
            value = new RouteLegMetrics(distanceKm, durationSeconds);
        }

        legCache.put(key, value);
        return value;
    }

    public record PlannedRoute(List<Stop> orderedStops, double totalDistanceKm, long totalDurationSeconds) {
    }

    private static final class BestRoute {
        private double objective;

        private BestRoute(double objective) {
            this.objective = objective;
        }
    }

    private record RouteTotals(double distanceKm, long durationSeconds) {
    }
}
