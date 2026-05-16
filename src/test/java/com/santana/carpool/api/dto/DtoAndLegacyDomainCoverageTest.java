package com.santana.carpool.api.dto;

import com.santana.carpool.domain.GeoPoint;
import com.santana.carpool.domain.LocationInput;
import com.santana.carpool.domain.RouteLeg;
import com.santana.carpool.domain.RouteOptimizer;
import com.santana.carpool.domain.RoutePlan;
import com.santana.carpool.domain.RouteRequest;
import com.santana.carpool.domain.Stop;
import com.santana.carpool.domain.StopType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DTO and Legacy Domain Coverage Tests")
class DtoAndLegacyDomainCoverageTest {

    @Test
    @DisplayName("Should create and read all API DTO records")
    void testAllApiDtos() {
        ApiStopDto stopDto = new ApiStopDto("p1", "Dunnes Stores Athlone", "N37 A1B2", 53.3025, -8.9849);
        ColleagueRequest colleague = new ColleagueRequest("SuperValu Golden Island", "N37 C3D4");
        DayRequest day = new DayRequest("Monday", "Dunnes Stores Athlone", "MORNING_TO_OFFICE");
        MemberRequest member = new MemberRequest("B&Q Athlone", "N37 E5F6", true);
        RouteRequestDto routeRequest = new RouteRequestDto(
                "IE", "Dunnes Stores Athlone", "N37 A1B2", "Office", "N37 XR90", "MORNING_TO_OFFICE", List.of(colleague)
        );
        RouteResponseDto routeResponse = new RouteResponseDto(
                "MORNING_TO_OFFICE", stopDto, stopDto, List.of(stopDto), List.of(), 12.0, 25L, Map.of("hits", 1L)
        );
        DailyRoutePlanDto dailyPlan = new DailyRoutePlanDto(
                "Monday", "MORNING_TO_OFFICE", stopDto, List.of(stopDto), List.of(), 8.0, 15L
        );
        WeeklyRouteRequestDto weeklyRequest = new WeeklyRouteRequestDto(
                "IE", "Office", "N37 XR90", List.of(member), List.of(day)
        );
        WeeklyRouteResponseDto weeklyResponse = new WeeklyRouteResponseDto(
                stopDto, List.of(dailyPlan), Map.of("Dunnes Stores Athlone", 1), Map.of("hits", 2L)
        );

        assertThat(stopDto.id()).isEqualTo("p1");
        assertThat(colleague.name()).isEqualTo("SuperValu Golden Island");
        assertThat(day.day()).isEqualTo("Monday");
        assertThat(member.canDrive()).isTrue();
        assertThat(routeRequest.colleagues()).hasSize(1);
        assertThat(routeResponse.tripType()).isEqualTo("MORNING_TO_OFFICE");
        assertThat(dailyPlan.tripType()).isEqualTo("MORNING_TO_OFFICE");
        assertThat(weeklyRequest.members()).hasSize(1);
        assertThat(weeklyResponse.days()).hasSize(1);
        assertThat(TripType.valueOf("EVENING_TO_HOME")).isEqualTo(TripType.EVENING_TO_HOME);
    }

    @Test
    @DisplayName("Should validate LocationInput")
    void testLocationInputValidation() {
        LocationInput valid = new LocationInput("Home", "N37 A1B2");
        assertThat(valid.label()).isEqualTo("Home");

        assertThatThrownBy(() -> new LocationInput("", "N37 A1B2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Label cannot be blank");

        assertThatThrownBy(() -> new LocationInput("Home", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Address or Eircode cannot be blank");
    }

    @Test
    @DisplayName("Should validate RouteLeg")
    void testRouteLegValidation() {
        Stop a = new Stop("a", "A", StopType.PICKUP, "N37 C3D4", new GeoPoint(53.30, -8.98));
        Stop b = new Stop("b", "B", StopType.PICKUP, "N37 E5F6", new GeoPoint(53.31, -8.97));

        RouteLeg valid = new RouteLeg(a, b, 1.2, 5);
        assertThat(valid.distanceKm()).isEqualTo(1.2);

        assertThatThrownBy(() -> new RouteLeg(null, b, 1.0, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoints are required");

        assertThatThrownBy(() -> new RouteLeg(a, b, -0.1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Distance cannot be negative");

        assertThatThrownBy(() -> new RouteLeg(a, b, 0.1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duration cannot be negative");
    }

    @Test
    @DisplayName("Should validate RouteRequest and RoutePlan")
    void testRouteRequestAndPlanValidation() {
        Stop driver = new Stop("d", "Driver", StopType.DRIVER_START, "N37 D1D1", new GeoPoint(53.29, -8.99));
        Stop pickup = new Stop("p", "Pickup", StopType.PICKUP, "N37 P1P1", new GeoPoint(53.30, -8.98));
        Stop office = new Stop("o", "Office", StopType.OFFICE, "N37 O1O1", new GeoPoint(53.31, -8.97));

        RouteRequest request = new RouteRequest(driver, List.of(pickup), office);
        assertThat(request.pickups()).hasSize(1);

        RouteLeg leg = new RouteLeg(driver, office, 3.5, 12);
        RoutePlan plan = new RoutePlan(List.of(pickup), List.of(leg), 3.5, 12);
        assertThat(plan.totalDurationMinutes()).isEqualTo(12);

        List<Stop> emptyStops = List.of();
        List<Stop> pickupList = List.of(pickup);
        List<RouteLeg> legList = List.of(leg);

        assertThatThrownBy(() -> new RouteRequest(null, pickupList, office))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Driver start is required");

        assertThatThrownBy(() -> new RouteRequest(driver, null, office))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pickup list is required");

        assertThatThrownBy(() -> new RouteRequest(driver, pickupList, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Office stop is required");

        assertThatThrownBy(() -> new RoutePlan(emptyStops, legList, 1.0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ordered stops are required");

        assertThatThrownBy(() -> new RoutePlan(pickupList, null, 1.0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Route legs list is required");

        assertThatThrownBy(() -> new RoutePlan(pickupList, legList, -1.0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Total distance cannot be negative");

        assertThatThrownBy(() -> new RoutePlan(pickupList, legList, 1.0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Total duration cannot be negative");
    }

    @Test
    @DisplayName("Should allow RouteOptimizer implementation")
    void testRouteOptimizerInterfaceUsage() {
        Stop driver = new Stop("d", "Driver", StopType.DRIVER_START, "N37 D1D1", new GeoPoint(53.29, -8.99));
        Stop office = new Stop("o", "Office", StopType.OFFICE, "N37 O1O1", new GeoPoint(53.31, -8.97));
        Stop pickup = new Stop("p", "Pickup", StopType.PICKUP, "N37 P1P1", new GeoPoint(53.30, -8.98));

        RouteOptimizer optimizer = req -> new RoutePlan(
                req.pickups(),
                List.of(new RouteLeg(req.driverStart(), req.office(), 3.0, 10)),
                3.0,
                10
        );

        RoutePlan optimized = optimizer.optimize(new RouteRequest(driver, List.of(pickup), office));
        assertThat(optimized.totalDistanceKm()).isEqualTo(3.0);
    }
}
