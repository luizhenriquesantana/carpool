package com.santana.carpool.api;

import com.santana.carpool.api.dto.ColleagueRequest;
import com.santana.carpool.api.dto.DayRequest;
import com.santana.carpool.api.dto.MemberRequest;
import com.santana.carpool.api.dto.RouteRequestDto;
import com.santana.carpool.api.dto.RouteResponseDto;
import com.santana.carpool.api.dto.TripType;
import com.santana.carpool.api.dto.WeeklyRouteRequestDto;
import com.santana.carpool.api.dto.WeeklyRouteResponseDto;
import com.santana.carpool.domain.GeoPoint;
import com.santana.carpool.domain.Stop;
import com.santana.carpool.domain.StopType;
import com.santana.carpool.geocoding.GoogleGeocodingService;
import com.santana.carpool.routing.GoogleRoutesService;
import com.santana.carpool.routing.RoutePlanningService;
import com.santana.carpool.service.RouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RouteController Unit Tests")
class RouteControllerUnitTest {

    @Mock
    private GoogleGeocodingService geocodingService;

    @Mock
    private RoutePlanningService routePlanningService;

    @Mock
    private GoogleRoutesService routesService;

    private RouteService routeService;
    private RouteController controller;

    @BeforeEach
    void setUp() {
                routeService = new RouteService(geocodingService, routePlanningService, routesService);
                controller = new RouteController(routeService);
    }

    @Test
    @DisplayName("Should build MORNING route response with pickup order")
    void testRouteMorningSuccess() {
                when(geocodingService.cacheStats()).thenReturn(Map.of("hits", 3L, "misses", 1L, "size", 2L));
                when(routesService.cacheStats()).thenReturn(Map.of("hits", 5L, "misses", 2L, "size", 4L));
        when(geocodingService.geocodePostalCode(anyString(), anyString())).thenReturn(new GeoPoint(53.3025, -8.9849));

        Stop p1 = new Stop("p1", "SuperValu Golden Island", StopType.PICKUP, "N37 C3D4", new GeoPoint(53.31, -8.98));
        Stop p2 = new Stop("p2", "B&Q Athlone", StopType.PICKUP, "N37 E5F6", new GeoPoint(53.30, -8.97));
        RoutePlanningService.PlannedRoute plan = new RoutePlanningService.PlannedRoute(List.of(p1, p2), 12.4, 1800L);
        when(routePlanningService.planRoute(eq(TripType.MORNING_TO_OFFICE), any(Stop.class), any(Stop.class), anyList()))
                .thenReturn(plan);

        RouteRequestDto request = new RouteRequestDto(
                "IE",
                "Dunnes Stores Athlone",
                "N37 A1B2",
                "Athlone Business Campus",
                "N37 XR90",
                "MORNING_TO_OFFICE",
                List.of(new ColleagueRequest("SuperValu Golden Island", "N37 C3D4"), new ColleagueRequest("B&Q Athlone", "N37 E5F6"))
        );

        RouteResponseDto response = controller.route(request);

        assertThat(response.tripType()).isEqualTo("MORNING_TO_OFFICE");
        assertThat(response.pickupOrder()).hasSize(2);
        assertThat(response.dropoffOrder()).isEmpty();
        assertThat(response.totalEstimatedKm()).isEqualTo(12.4);
        assertThat(response.totalEstimatedDurationMinutes()).isEqualTo(30L);
        assertThat(response.cacheStats()).containsEntry("routesHits", 5L);
    }

    @Test
    @DisplayName("Should build EVENING route response with dropoff order")
    void testRouteEveningSuccess() {
                when(geocodingService.cacheStats()).thenReturn(Map.of("hits", 3L, "misses", 1L, "size", 2L));
                when(routesService.cacheStats()).thenReturn(Map.of("hits", 5L, "misses", 2L, "size", 4L));
        when(geocodingService.geocodePostalCode(anyString(), anyString())).thenReturn(new GeoPoint(53.3025, -8.9849));

        Stop d1 = new Stop("p1", "SuperValu Golden Island", StopType.PICKUP, "N37 C3D4", new GeoPoint(53.31, -8.98));
        RoutePlanningService.PlannedRoute plan = new RoutePlanningService.PlannedRoute(List.of(d1), 7.0, 1200L);
        when(routePlanningService.planRoute(eq(TripType.EVENING_TO_HOME), any(Stop.class), any(Stop.class), anyList()))
                .thenReturn(plan);

        RouteRequestDto request = new RouteRequestDto(
                "IE",
                "Dunnes Stores Athlone",
                "N37 A1B2",
                "Athlone Business Campus",
                "N37 XR90",
                "EVENING_TO_HOME",
                List.of(new ColleagueRequest("SuperValu Golden Island", "N37 C3D4"))
        );

        RouteResponseDto response = controller.route(request);

        assertThat(response.tripType()).isEqualTo("EVENING_TO_HOME");
        assertThat(response.pickupOrder()).isEmpty();
        assertThat(response.dropoffOrder()).hasSize(1);
        assertThat(response.totalEstimatedDurationMinutes()).isEqualTo(20L);
    }

    @Test
    @DisplayName("Should reject invalid trip type")
    void testRouteInvalidTripType() {
        RouteRequestDto request = new RouteRequestDto(
                "IE",
                "Dunnes Stores Athlone",
                "N37 A1B2",
                "Athlone Business Campus",
                "N37 XR90",
                "INVALID",
                List.of(new ColleagueRequest("SuperValu Golden Island", "N37 C3D4"))
        );

        assertThatThrownBy(() -> controller.route(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tripType");
    }

    @Test
    @DisplayName("Should plan default five weekdays for weekly route")
    void testWeeklyRouteDefaultDays() {
                when(geocodingService.cacheStats()).thenReturn(Map.of("hits", 3L, "misses", 1L, "size", 2L));
                when(routesService.cacheStats()).thenReturn(Map.of("hits", 5L, "misses", 2L, "size", 4L));
        when(geocodingService.geocodePostalCode(anyString(), anyString())).thenReturn(new GeoPoint(53.3025, -8.9849));

        Stop onlyPickup = new Stop("m-bob", "SuperValu Golden Island", StopType.PICKUP, "N37 C3D4", new GeoPoint(53.31, -8.98));
        RoutePlanningService.PlannedRoute plan = new RoutePlanningService.PlannedRoute(List.of(onlyPickup), 8.5, 900L);
        when(routePlanningService.planRoute(any(TripType.class), any(Stop.class), any(Stop.class), anyList()))
                .thenReturn(plan);

        WeeklyRouteRequestDto request = new WeeklyRouteRequestDto(
                "IE",
                "Athlone Business Campus",
                "N37 XR90",
                List.of(
                        new MemberRequest("Dunnes Stores Athlone", "N37 G7H8", true),
                        new MemberRequest("SuperValu Golden Island", "N37 C3D4", false)
                ),
                null
        );

        WeeklyRouteResponseDto response = controller.weeklyRoute(request);

        assertThat(response.days()).hasSize(5);
        assertThat(response.driverAssignments()).containsEntry("Dunnes Stores Athlone", 5);
                verify(routePlanningService, times(5)).planRoute(eq(TripType.MORNING_TO_OFFICE), any(Stop.class), any(Stop.class), anyList());
    }

    @Test
    @DisplayName("Should plan explicit weekly days and fixed driver")
    void testWeeklyRouteExplicitDaysAndFixedDriver() {
                when(geocodingService.cacheStats()).thenReturn(Map.of("hits", 3L, "misses", 1L, "size", 2L));
                when(routesService.cacheStats()).thenReturn(Map.of("hits", 5L, "misses", 2L, "size", 4L));
        when(geocodingService.geocodePostalCode(anyString(), anyString())).thenReturn(new GeoPoint(53.3025, -8.9849));

        RoutePlanningService.PlannedRoute plan = new RoutePlanningService.PlannedRoute(List.of(), 2.0, 300L);
        when(routePlanningService.planRoute(any(TripType.class), any(Stop.class), any(Stop.class), anyList()))
                .thenReturn(plan);

        WeeklyRouteRequestDto request = new WeeklyRouteRequestDto(
                "IE",
                "Office",
                "N37 XR90",
                List.of(
                        new MemberRequest("Dunnes Stores Athlone", "N37 G7H8", true),
                        new MemberRequest("SuperValu Golden Island", "N37 C3D4", true)
                ),
                List.of(
                        new DayRequest("Monday", "SuperValu Golden Island", "MORNING_TO_OFFICE"),
                        new DayRequest("Tuesday", "SuperValu Golden Island", "EVENING_TO_HOME")
                )
        );

        WeeklyRouteResponseDto response = controller.weeklyRoute(request);

        assertThat(response.days()).hasSize(2);
        assertThat(response.driverAssignments()).containsEntry("SuperValu Golden Island", 2);
    }

    @Test
    @DisplayName("Should reject weekly request with no members")
    void testWeeklyRouteNoMembers() {
        WeeklyRouteRequestDto request = new WeeklyRouteRequestDto("IE", "Office", "N37 XR90", List.of(), List.of());

        assertThatThrownBy(() -> controller.weeklyRoute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one member is required");
    }

    @Test
    @DisplayName("Should reject missing driverName")
    void testRouteMissingDriverName() {
        RouteRequestDto request = new RouteRequestDto(
                "IE",
                " ",
                "N37 XR90",
                "Office",
                "N37 XR90",
                "MORNING_TO_OFFICE",
                List.of(new ColleagueRequest("SuperValu Golden Island", "N37 C3D4"))
        );

        assertThatThrownBy(() -> controller.route(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required field: driverName");
    }

    @Test
    @DisplayName("Should reject request with empty colleagues")
    void testRouteEmptyColleagues() {
        RouteRequestDto request = new RouteRequestDto(
                "IE",
                "Dunnes Stores Athlone",
                "N37 XR90",
                "Office",
                "N37 XR90",
                "MORNING_TO_OFFICE",
                List.of()
        );

        assertThatThrownBy(() -> controller.route(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one colleague is required");
    }

    @Test
    @DisplayName("Should default tripType to MORNING when missing")
    void testRouteDefaultsTripType() {
        when(geocodingService.cacheStats()).thenReturn(Map.of("hits", 0L, "misses", 0L, "size", 0L));
        when(routesService.cacheStats()).thenReturn(Map.of("hits", 0L, "misses", 0L, "size", 0L));
        when(geocodingService.geocodePostalCode(anyString(), anyString())).thenReturn(new GeoPoint(53.3025, -8.9849));

        RoutePlanningService.PlannedRoute plan = new RoutePlanningService.PlannedRoute(List.of(), 3.0, 600L);
        when(routePlanningService.planRoute(eq(TripType.MORNING_TO_OFFICE), any(Stop.class), any(Stop.class), anyList()))
                .thenReturn(plan);

        RouteRequestDto request = new RouteRequestDto(
                "IE",
                "Dunnes Stores Athlone",
                "N37 XR90",
                "Office",
                "N37 XR90",
                null,
                List.of(new ColleagueRequest("SuperValu Golden Island", "N37 C3D4"))
        );

        RouteResponseDto response = controller.route(request);
        assertThat(response.tripType()).isEqualTo("MORNING_TO_OFFICE");
    }

    @Test
    @DisplayName("Should reject weekly request when no eligible drivers")
    void testWeeklyRouteNoEligibleDrivers() {
        when(geocodingService.geocodePostalCode(anyString(), anyString())).thenReturn(new GeoPoint(53.3025, -8.9849));

        WeeklyRouteRequestDto request = new WeeklyRouteRequestDto(
                "IE",
                "Office",
                "N37 XR90",
                List.of(
                        new MemberRequest("Dunnes Stores Athlone", "N37 G7H8", false),
                        new MemberRequest("SuperValu Golden Island", "N37 C3D4", false)
                ),
                List.of(new DayRequest("Monday", null, "MORNING_TO_OFFICE"))
        );

        assertThatThrownBy(() -> controller.weeklyRoute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No eligible drivers found");
    }

    @Test
    @DisplayName("Should reject unknown fixed weekly driver")
    void testWeeklyRouteUnknownFixedDriver() {
        when(geocodingService.geocodePostalCode(anyString(), anyString())).thenReturn(new GeoPoint(53.3025, -8.9849));

        WeeklyRouteRequestDto request = new WeeklyRouteRequestDto(
                "IE",
                "Office",
                "N37 XR90",
                List.of(
                        new MemberRequest("Dunnes Stores Athlone", "N37 G7H8", true),
                        new MemberRequest("SuperValu Golden Island", "N37 C3D4", true)
                ),
                List.of(new DayRequest("Monday", "NonExisting", "MORNING_TO_OFFICE"))
        );

        assertThatThrownBy(() -> controller.weeklyRoute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fixed driver not found or not eligible");
    }

    @Test
    @DisplayName("Should map bad request handler response")
    void testBadRequestHandler() {
        Map<String, String> error = controller.badRequest(new IllegalArgumentException("bad input"));
        assertThat(error).containsEntry("error", "bad input");
    }

    @Test
    @DisplayName("Should map internal error handler response")
    void testInternalHandler() {
        Map<String, String> error = controller.internal(new IllegalStateException("boom"));
        assertThat(error).containsEntry("error", "boom");
    }
}
