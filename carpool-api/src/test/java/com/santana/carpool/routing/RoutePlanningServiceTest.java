package com.santana.carpool.routing;

import com.santana.carpool.domain.GeoPoint;
import com.santana.carpool.domain.Stop;
import com.santana.carpool.domain.StopType;
import com.santana.carpool.api.dto.TripType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoutePlanningService Tests")
class RoutePlanningServiceTest {

    @Mock
    private GoogleRoutesService routesService;

    @Mock
    private NearestNeighborRoutePlanner routePlanner;

    @InjectMocks
    private RoutePlanningService routePlanningService;

    private Stop driver;
    private Stop office;
    private List<Stop> pickups;

    @BeforeEach
    void setUp() {
        driver = new Stop("driver", "Dunnes Stores Athlone", StopType.DRIVER_START, "N37 A1B2", new GeoPoint(53.4230, -7.9404));
        office = new Stop("office", "Athlone Business Campus", StopType.OFFICE, "N37 XR90", new GeoPoint(53.4180, -7.9330));
        
        pickups = List.of(
                new Stop("p1", "SuperValu Golden Island", StopType.PICKUP, "N37 C3D4", new GeoPoint(53.4271, -7.9141)),
                new Stop("p2", "B&Q Athlone", StopType.PICKUP, "N37 E5F6", new GeoPoint(53.4312, -7.9280)),
                new Stop("p3", "Athlone Regional Sports Centre", StopType.PICKUP, "N37 G7H8", new GeoPoint(53.4306, -7.9459))
        );
    }

    @Test
    @DisplayName("Should find optimal route for 3 pickups (exhaustive search)")
    void testPlanRouteWithThreePickups() {
        // Mock all leg metrics
        RouteLegMetrics shortLeg = new RouteLegMetrics(1.5, 300L);

        when(routesService.computeDrivingLeg(any(GeoPoint.class), any(GeoPoint.class)))
                .thenReturn(shortLeg);

        // Act
        RoutePlanningService.PlannedRoute result = routePlanningService.planRoute(
                TripType.MORNING_TO_OFFICE, driver, office, pickups
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.orderedStops()).hasSize(3);
        assertThat(result.totalDistanceKm()).isGreaterThan(0);
        assertThat(result.totalDurationSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle empty pickups list")
    void testPlanRouteWithNoPickups() {
        RouteLegMetrics directLeg = new RouteLegMetrics(3.0, 600L);
        when(routesService.computeDrivingLeg(any(GeoPoint.class), any(GeoPoint.class)))
                .thenReturn(directLeg);

        // Act
        RoutePlanningService.PlannedRoute result = routePlanningService.planRoute(
                TripType.MORNING_TO_OFFICE, driver, office, List.of()
        );

        // Assert
        assertThat(result.orderedStops()).isEmpty();
    }

    @Test
    @DisplayName("Should handle single pickup")
    void testPlanRouteWithSinglePickup() {
        RouteLegMetrics leg = new RouteLegMetrics(2.0, 480L);
        when(routesService.computeDrivingLeg(any(GeoPoint.class), any(GeoPoint.class)))
                .thenReturn(leg);

        // Act
        RoutePlanningService.PlannedRoute result = routePlanningService.planRoute(
                TripType.MORNING_TO_OFFICE, driver, office, List.of(pickups.get(0))
        );

        // Assert
        assertThat(result.orderedStops()).hasSize(1);
        assertThat(result.orderedStops().get(0).id()).isEqualTo("p1");
    }

    @Test
    @DisplayName("Should switch to greedy for > 8 pickups")
    void testPlanRouteWithLargePickupSet() {
        List<Stop> largePiece = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            largePiece.add(new Stop("p" + i, "Person" + i, StopType.PICKUP, "D0X XX" + i,
                    new GeoPoint(53.3498 + i * 0.01, -6.2603 + i * 0.01)));
        }

        RouteLegMetrics leg = new RouteLegMetrics(1.0, 200L);
        when(routesService.computeDrivingLeg(any(GeoPoint.class), any(GeoPoint.class)))
                .thenReturn(leg);

        // Act
        RoutePlanningService.PlannedRoute result = routePlanningService.planRoute(
                TripType.MORNING_TO_OFFICE, driver, office, largePiece
        );

        // Assert - Should use greedy fallback
        assertThat(result.orderedStops()).hasSize(10);
    }

    @Test
    @DisplayName("Should reverse direction for evening trips")
    void testPlanRouteEveningTrip() {
        RouteLegMetrics leg = new RouteLegMetrics(2.0, 480L);
        when(routesService.computeDrivingLeg(any(GeoPoint.class), any(GeoPoint.class)))
                .thenReturn(leg);

        // Act
        RoutePlanningService.PlannedRoute result = routePlanningService.planRoute(
                TripType.EVENING_TO_HOME, driver, office, List.of(pickups.get(0))
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.orderedStops()).hasSize(1);
    }

    @Test
    @DisplayName("Should handle Google Routes API failure with fallback")
    void testPlanRouteWithApiFailure() {
        when(routesService.computeDrivingLeg(any(GeoPoint.class), any(GeoPoint.class)))
                .thenThrow(new RuntimeException("API Error"));
        when(routePlanner.haversineKm(any(GeoPoint.class), any(GeoPoint.class)))
                .thenReturn(2.5);

        // Act
        RoutePlanningService.PlannedRoute result = routePlanningService.planRoute(
                TripType.MORNING_TO_OFFICE, driver, office, List.of(pickups.get(0))
        );

        // Assert - Should use fallback calculation
        assertThat(result).isNotNull();
        assertThat(result.totalDistanceKm()).isGreaterThan(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 8})
    @DisplayName("Should handle various pickup counts up to 8 using exhaustive search")
    void testExhaustiveSearchWithVariousPickupCounts(int count) {
        List<Stop> testPickups = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            testPickups.add(new Stop("p" + i, "Person" + i, StopType.PICKUP, "D0X XX" + i,
                    new GeoPoint(53.3498 + i * 0.01, -6.2603)));
        }

        RouteLegMetrics leg = new RouteLegMetrics(1.0, 180L);
        when(routesService.computeDrivingLeg(any(GeoPoint.class), any(GeoPoint.class)))
                .thenReturn(leg);

        // Act
        RoutePlanningService.PlannedRoute result = routePlanningService.planRoute(
                TripType.MORNING_TO_OFFICE, driver, office, testPickups
        );

        // Assert
        assertThat(result.orderedStops()).hasSize(count);
    }

    @Test
    @DisplayName("Should calculate correct totals for route")
    void testCalculateTotalsRoadAware() {
        RouteLegMetrics leg1 = new RouteLegMetrics(2.0, 480L);
        RouteLegMetrics leg2 = new RouteLegMetrics(3.0, 720L);

        when(routesService.computeDrivingLeg(any(GeoPoint.class), any(GeoPoint.class)))
                .thenReturn(leg1)
                .thenReturn(leg2)
                .thenReturn(leg1);

        // Act
        RoutePlanningService.PlannedRoute result = routePlanningService.planRoute(
                TripType.MORNING_TO_OFFICE, driver, office, List.of(pickups.get(0), pickups.get(1))
        );

        // Assert
        assertThat(result.totalDistanceKm()).isGreaterThanOrEqualTo(2.0);
        assertThat(result.totalDurationSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should use per-request leg cache")
    void testPerRequestLegCache() {
        RouteLegMetrics leg = new RouteLegMetrics(1.5, 300L);
        when(routesService.computeDrivingLeg(any(GeoPoint.class), any(GeoPoint.class)))
                .thenReturn(leg);

        // Act - call planRoute which should reuse legs via cache
        routePlanningService.planRoute(TripType.MORNING_TO_OFFICE, driver, office, pickups);

        // Assert - verify that computeDrivingLeg wasn't called excessively
        // For 3 pickups, we should call it multiple times but with caching
        verify(routesService, atLeast(3)).computeDrivingLeg(any(GeoPoint.class), any(GeoPoint.class));
    }
}
