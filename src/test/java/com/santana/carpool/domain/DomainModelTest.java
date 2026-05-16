package com.santana.carpool.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Domain Model Tests")
class DomainModelTest {

    @Test
    @DisplayName("Should create valid GeoPoint with valid coordinates")
    void testGeoPointValidCoordinates() {
        // Act
        GeoPoint point = new GeoPoint(53.3498, -6.2603);

        // Assert
        assertThat(point.latitude()).isEqualTo(53.3498);
        assertThat(point.longitude()).isEqualTo(-6.2603);
    }

    @Test
    @DisplayName("Should reject latitude > 90")
    void testGeoPointLatitudeTooHigh() {
        // Act & Assert
        assertThatThrownBy(() -> new GeoPoint(91.0, -6.2603))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude must be between -90 and 90");
    }

    @Test
    @DisplayName("Should reject latitude < -90")
    void testGeoPointLatitudeTooLow() {
        // Act & Assert
        assertThatThrownBy(() -> new GeoPoint(-91.0, -6.2603))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude must be between -90 and 90");
    }

    @Test
    @DisplayName("Should reject longitude > 180")
    void testGeoPointLongitudeTooHigh() {
        // Act & Assert
        assertThatThrownBy(() -> new GeoPoint(53.3498, 181.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude must be between -180 and 180");
    }

    @Test
    @DisplayName("Should reject longitude < -180")
    void testGeoPointLongitudeTooLow() {
        // Act & Assert
        assertThatThrownBy(() -> new GeoPoint(53.3498, -181.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude must be between -180 and 180");
    }

    @Test
    @DisplayName("Should accept boundary latitude values")
    void testGeoPointBoundaryLatitudes() {
        // North Pole
        GeoPoint north = new GeoPoint(90.0, 0.0);
        assertThat(north.latitude()).isEqualTo(90.0);

        // South Pole
        GeoPoint south = new GeoPoint(-90.0, 0.0);
        assertThat(south.latitude()).isEqualTo(-90.0);
    }

    @Test
    @DisplayName("Should accept boundary longitude values")
    void testGeoPointBoundaryLongitudes() {
        // Prime Meridian East
        GeoPoint east = new GeoPoint(0.0, 180.0);
        assertThat(east.longitude()).isEqualTo(180.0);

        // Prime Meridian West
        GeoPoint west = new GeoPoint(0.0, -180.0);
        assertThat(west.longitude()).isEqualTo(-180.0);
    }

    @Test
    @DisplayName("Should create valid Stop with all required fields")
    void testStopValidCreation() {
        // Act
        Stop stop = new Stop("p1", "Dunnes Stores Athlone", StopType.PICKUP, "N37 A1B2", 
                new GeoPoint(53.3498, -6.2603));

        // Assert
        assertThat(stop.id()).isEqualTo("p1");
        assertThat(stop.label()).isEqualTo("Dunnes Stores Athlone");
        assertThat(stop.type()).isEqualTo(StopType.PICKUP);
        assertThat(stop.addressOrEircode()).isEqualTo("N37 A1B2");
        assertThat(stop.coordinates()).isNotNull();
    }

    @Test
    @DisplayName("Should reject Stop with null id")
    void testStopNullId() {
        GeoPoint coordinates = new GeoPoint(53.3498, -6.2603);
        // Act & Assert
        assertThatThrownBy(() -> new Stop(null, "Dunnes Stores Athlone", StopType.PICKUP, "N37 A1B2", coordinates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stop id cannot be blank");
    }

    @Test
    @DisplayName("Should reject Stop with blank id")
    void testStopBlankId() {
        GeoPoint coordinates = new GeoPoint(53.3498, -6.2603);
        // Act & Assert
        assertThatThrownBy(() -> new Stop("  ", "Dunnes Stores Athlone", StopType.PICKUP, "N37 A1B2", coordinates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stop id cannot be blank");
    }

    @Test
    @DisplayName("Should reject Stop with null label")
    void testStopNullLabel() {
        GeoPoint coordinates = new GeoPoint(53.3498, -6.2603);
        // Act & Assert
        assertThatThrownBy(() -> new Stop("p1", null, StopType.PICKUP, "N37 A1B2", coordinates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stop label cannot be blank");
    }

    @Test
    @DisplayName("Should reject Stop with blank label")
    void testStopBlankLabel() {
        GeoPoint coordinates = new GeoPoint(53.3498, -6.2603);
        // Act & Assert
        assertThatThrownBy(() -> new Stop("p1", "   ", StopType.PICKUP, "N37 A1B2", coordinates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stop label cannot be blank");
    }

    @Test
    @DisplayName("Should reject Stop with null type")
    void testStopNullType() {
        GeoPoint coordinates = new GeoPoint(53.3498, -6.2603);
        // Act & Assert
        assertThatThrownBy(() -> new Stop("p1", "Dunnes Stores Athlone", null, "N37 A1B2", coordinates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stop type is required");
    }

    @Test
    @DisplayName("Should reject Stop with null address/eircode")
    void testStopNullAddress() {
        GeoPoint coordinates = new GeoPoint(53.3498, -6.2603);
        // Act & Assert
        assertThatThrownBy(() -> new Stop("p1", "Dunnes Stores Athlone", StopType.PICKUP, null, coordinates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Address or Eircode is required");
    }

    @Test
    @DisplayName("Should reject Stop with blank address/eircode")
    void testStopBlankAddress() {
        GeoPoint coordinates = new GeoPoint(53.3498, -6.2603);
        // Act & Assert
        assertThatThrownBy(() -> new Stop("p1", "Dunnes Stores Athlone", StopType.PICKUP, "  ", coordinates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Address or Eircode is required");
    }

    @Test
    @DisplayName("Should reject Stop with null coordinates")
    void testStopNullCoordinates() {
        // Act & Assert
        assertThatThrownBy(() -> new Stop("p1", "Dunnes Stores Athlone", StopType.PICKUP, "N37 A1B2", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Coordinates are required");
    }

    @Test
    @DisplayName("Should create valid Stop with different StopTypes")
    void testStopWithVariousTypes() {
        GeoPoint coord = new GeoPoint(53.3498, -6.2603);

        Stop driverStart = new Stop("driver", "Driver", StopType.DRIVER_START, "N37 A1B2", coord);
        assertThat(driverStart.type()).isEqualTo(StopType.DRIVER_START);

        Stop office = new Stop("office", "Office", StopType.OFFICE, "N37 XR90", coord);
        assertThat(office.type()).isEqualTo(StopType.OFFICE);

        Stop pickup = new Stop("p1", "Passenger", StopType.PICKUP, "N37 G7H8", coord);
        assertThat(pickup.type()).isEqualTo(StopType.PICKUP);
    }

    @Test
    @DisplayName("Should enforce StopType enum")
    void testStopTypeEnum() {
        // Assert that StopType enum has expected values
        assertThat(StopType.values())
                .contains(StopType.DRIVER_START, StopType.OFFICE, StopType.PICKUP);
    }

    @Test
    @DisplayName("Should handle equivalent GeoPoint values")
    void testGeoPointEquivalence() {
        GeoPoint point1 = new GeoPoint(53.3498, -6.2603);
        GeoPoint point2 = new GeoPoint(53.3498, -6.2603);

        // Records use value-based equality
        assertThat(point1).isEqualTo(point2);
    }

    @Test
    @DisplayName("Should create Stop record with immutable coordinates")
    void testStopCoordinatesImmutability() {
        GeoPoint coord = new GeoPoint(53.3498, -6.2603);
        Stop stop = new Stop("p1", "Dunnes Stores Athlone", StopType.PICKUP, "N37 A1B2", coord);

        // Records are immutable, accessing coordinates should return same value
        assertThat(stop.coordinates()).isEqualTo(coord);
    }
}
