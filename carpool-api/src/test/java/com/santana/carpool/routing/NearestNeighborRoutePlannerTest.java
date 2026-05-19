package com.santana.carpool.routing;

import com.santana.carpool.domain.GeoPoint;
import com.santana.carpool.domain.Stop;
import com.santana.carpool.domain.StopType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NearestNeighborRoutePlanner Tests")
class NearestNeighborRoutePlannerTest {

    private NearestNeighborRoutePlanner routePlanner;

    private Stop driverStart;
    private List<Stop> pickups;

    @BeforeEach
    void setUp() {
        routePlanner = new NearestNeighborRoutePlanner();
        
        driverStart = new Stop("driver", "Driver", StopType.DRIVER_START, "N37 A1B2", 
                new GeoPoint(53.3498, -6.2603));
        
        pickups = List.of(
                new Stop("p1", "SuperValu Golden Island", StopType.PICKUP, "N37 C3D4", new GeoPoint(53.3520, -6.2700)),
                new Stop("p2", "B&Q Athlone", StopType.PICKUP, "N37 E5F6", new GeoPoint(53.3450, -6.2500)),
                new Stop("p3", "Athlone Regional Sports Centre", StopType.PICKUP, "N37 G7H8", new GeoPoint(53.3480, -6.2650))
        );
    }

    @Test
    @DisplayName("Should plan pickup order using nearest neighbor heuristic")
    void testPlanPickupOrder() {
        // Act
        List<Stop> result = routePlanner.planPickupOrder(driverStart, pickups);

        // Assert
        assertThat(result)
            .hasSize(3)
            .containsExactlyInAnyOrder(pickups.toArray(new Stop[0]));
    }

    @Test
    @DisplayName("Should handle single pickup")
    void testPlanPickupOrderWithSinglePickup() {
        // Act
        List<Stop> result = routePlanner.planPickupOrder(driverStart, List.of(pickups.get(0)));

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(pickups.get(0));
    }

    @Test
    @DisplayName("Should handle empty pickups")
    void testPlanPickupOrderWithNoPickups() {
        // Act
        List<Stop> result = routePlanner.planPickupOrder(driverStart, List.of());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should throw exception when driver start is null")
    void testPlanPickupOrderWithNullDriver() {
        // Act & Assert
        assertThatThrownBy(() -> routePlanner.planPickupOrder(null, pickups))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("driverStart is required");
    }

    @Test
    @DisplayName("Should throw exception when pickups is null")
    void testPlanPickupOrderWithNullPickups() {
        // Act & Assert
        assertThatThrownBy(() -> routePlanner.planPickupOrder(driverStart, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pickups list is required");
    }

    @Test
    @DisplayName("Should calculate Haversine distance correctly")
    void testHaversineDistance() {
        GeoPoint dublin = new GeoPoint(53.3498, -6.2603);
        GeoPoint galway = new GeoPoint(53.2707, -9.0568);

        // Act
        double distance = routePlanner.haversineKm(dublin, galway);

        // Assert - Straight-line (Haversine) Dublin to Galway is ~186 km
        assertThat(distance).isGreaterThan(180).isLessThan(200);
    }

    @Test
    @DisplayName("Should calculate zero distance for same point")
    void testHaversineDistanceForSamePoint() {
        GeoPoint point = new GeoPoint(53.3498, -6.2603);

        // Act
        double distance = routePlanner.haversineKm(point, point);

        // Assert
        assertThat(distance).isCloseTo(0.0, within(0.01));
    }

    @Test
    @DisplayName("Should calculate correct distance for known coordinate pairs")
    void testHaversineDistanceKnownValues() {
        // Straight-line (Haversine) Dublin to Cork is ~220 km
        GeoPoint dublin = new GeoPoint(53.3498, -6.2603);
        GeoPoint cork = new GeoPoint(51.8985, -8.4761);

        // Act
        double distance = routePlanner.haversineKm(dublin, cork);

        // Assert
        assertThat(distance).isGreaterThan(210).isLessThan(240);
    }

    @Test
    @DisplayName("Should handle antipodal points")
    void testHaversineDistanceAntipodal() {
        GeoPoint north = new GeoPoint(90.0, 0.0);
        GeoPoint south = new GeoPoint(-90.0, 0.0);

        // Act
        double distance = routePlanner.haversineKm(north, south);

        // Assert - Half Earth's circumference = ~20,015 km
        assertThat(distance).isGreaterThan(19000).isLessThan(21000);
    }

    @Test
    @DisplayName("Should maintain order consistency with nearest neighbor")
    void testNearestNeighborConsistency() {
        // Create pickups in a line
        Stop p1 = new Stop("p1", "P1", StopType.PICKUP, "N37 001", new GeoPoint(53.3500, -6.2603));
        Stop p2 = new Stop("p2", "P2", StopType.PICKUP, "N37 002", new GeoPoint(53.3510, -6.2603));
        Stop p3 = new Stop("p3", "P3", StopType.PICKUP, "N37 003", new GeoPoint(53.3520, -6.2603));

        // Act
        List<Stop> result = routePlanner.planPickupOrder(driverStart, List.of(p3, p1, p2));

        // Assert - should start with p1 (closest to driver), then p2, then p3
        assertThat(result.get(0)).isEqualTo(p1);
        assertThat(result.get(1)).isEqualTo(p2);
        assertThat(result.get(2)).isEqualTo(p3);
    }
}
