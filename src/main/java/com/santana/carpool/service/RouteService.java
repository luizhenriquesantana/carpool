package com.santana.carpool.service;

import com.santana.carpool.api.dto.ApiStopDto;
import com.santana.carpool.api.dto.ColleagueRequest;
import com.santana.carpool.api.dto.DailyRoutePlanDto;
import com.santana.carpool.api.dto.DayRequest;
import com.santana.carpool.api.dto.RouteRequestDto;
import com.santana.carpool.api.dto.RouteResponseDto;
import com.santana.carpool.api.dto.TripType;
import com.santana.carpool.api.dto.WeeklyRouteRequestDto;
import com.santana.carpool.api.dto.WeeklyRouteResponseDto;
import com.santana.carpool.auth.SavedPostalCodeService;
import com.santana.carpool.domain.Stop;
import com.santana.carpool.domain.StopType;
import com.santana.carpool.geocoding.GoogleGeocodingService;
import com.santana.carpool.routing.GoogleRoutesService;
import com.santana.carpool.routing.RoutePlanningService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RouteService {
    private final GoogleGeocodingService geocodingService;
    private final RoutePlanningService routePlanningService;
    private final GoogleRoutesService routesService;
    private final SavedPostalCodeService savedPostalCodeService;

    public RouteService(
            GoogleGeocodingService geocodingService,
            RoutePlanningService routePlanningService,
            GoogleRoutesService routesService,
            SavedPostalCodeService savedPostalCodeService
    ) {
        this.geocodingService = geocodingService;
        this.routePlanningService = routePlanningService;
        this.routesService = routesService;
        this.savedPostalCodeService = savedPostalCodeService;
    }

    public RouteResponseDto planSingleRoute(RouteRequestDto request, String userEmail) {
        SingleRouteContext context = validateAndResolveSingleRoute(request);
        touchPostalCodes(userEmail, List.of(request.driverPostalCode(), request.officePostalCode()),
                request.colleagues().stream().map(ColleagueRequest::postalCode).toList());
        return buildSingleRouteResponse(context);
    }

    public WeeklyRouteResponseDto planWeeklyRoute(WeeklyRouteRequestDto request, String userEmail) {
        WeeklyRouteContext context = validateAndResolveWeeklyRoute(request);
        touchPostalCodes(userEmail, List.of(request.officePostalCode()),
                request.members().stream().map(com.santana.carpool.api.dto.MemberRequest::postalCode).toList());
        List<DailyRoutePlanDto> plannedDays = buildWeeklyPlans(context);
        return new WeeklyRouteResponseDto(
                toApiStop(context.office()),
                plannedDays,
                context.driverAssignments(),
                cacheStats()
        );
    }

    private void touchPostalCodes(String userEmail, List<String> requiredCodes, List<String> optionalCodes) {
        if (userEmail == null || userEmail.isBlank()) return;
        for (String code : requiredCodes) {
            if (code != null && !code.isBlank()) {
                try {
                    savedPostalCodeService.touchLastUsedForUser(userEmail, code);
                } catch (Exception ignored) {
                }
            }
        }
        for (String code : optionalCodes) {
            if (code != null && !code.isBlank()) {
                try {
                    savedPostalCodeService.touchLastUsedForUser(userEmail, code);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private SingleRouteContext validateAndResolveSingleRoute(RouteRequestDto request) {
        requireNotBlank(request.country(), "country");
        requireNotBlank(request.driverName(), "driverName");
        requireNotBlank(request.driverPostalCode(), "driverPostalCode");
        requireNotBlank(request.officeName(), "officeName");
        requireNotBlank(request.officePostalCode(), "officePostalCode");

        if (request.colleagues() == null || request.colleagues().isEmpty()) {
            throw new IllegalArgumentException("At least one colleague is required.");
        }

        TripType tripType = parseTripType(request.tripType());

        Stop driver = geocodeStop("driver", request.driverName(), StopType.DRIVER_START, request.driverPostalCode(), request.driverStreet(), request.driverHouseNumber(), request.country());
        Stop office = geocodeStop("office", request.officeName(), StopType.OFFICE, request.officePostalCode(), request.officeStreet(), request.officeHouseNumber(), request.country());

        List<Stop> pickups = new ArrayList<>();
        int index = 1;
        for (ColleagueRequest colleague : request.colleagues()) {
            requireNotBlank(colleague.name(), "colleagues.name");
            requireNotBlank(colleague.postalCode(), "colleagues.postalCode");
            pickups.add(geocodeStop("p" + index, colleague.name(), StopType.PICKUP, colleague.postalCode(), colleague.street(), colleague.houseNumber(), request.country()));
            index++;
        }

        return new SingleRouteContext(tripType, driver, office, pickups);
    }

    private RouteResponseDto buildSingleRouteResponse(SingleRouteContext context) {
        RoutePlanningService.PlannedRoute plan = routePlanningService.planRoute(
                context.tripType(),
                context.driver(),
                context.office(),
                context.pickups()
        );

        return new RouteResponseDto(
                context.tripType().name(),
                toApiStop(context.driver()),
                toApiStop(context.office()),
                context.tripType() == TripType.MORNING_TO_OFFICE
                        ? plan.orderedStops().stream().map(this::toApiStop).toList()
                        : List.of(),
                context.tripType() == TripType.EVENING_TO_HOME
                        ? plan.orderedStops().stream().map(this::toApiStop).toList()
                        : List.of(),
                plan.totalDistanceKm(),
                toMinutes(plan.totalDurationSeconds()),
                cacheStats()
        );
    }

    private WeeklyRouteContext validateAndResolveWeeklyRoute(WeeklyRouteRequestDto request) {
        requireNotBlank(request.country(), "country");
        requireNotBlank(request.officeName(), "officeName");
        requireNotBlank(request.officePostalCode(), "officePostalCode");

        if (request.members() == null || request.members().isEmpty()) {
            throw new IllegalArgumentException("At least one member is required.");
        }

        Stop office = geocodeStop("office", request.officeName(), StopType.OFFICE, request.officePostalCode(), request.officeStreet(), request.officeHouseNumber(), request.country());

        List<MemberInfo> members = request.members().stream().map(m -> {
            requireNotBlank(m.name(), "members.name");
            requireNotBlank(m.postalCode(), "members.postalCode");
            boolean canDrive = m.canDrive() == null || m.canDrive();
            Stop stop = geocodeStop("m-" + sanitizeIdPart(m.name()), m.name(), StopType.PICKUP, m.postalCode(), m.street(), m.houseNumber(), request.country());
            return new MemberInfo(m.name(), canDrive, stop);
        }).toList();

        List<MemberInfo> drivers = members.stream().filter(MemberInfo::canDrive).toList();
        if (drivers.isEmpty()) {
            throw new IllegalArgumentException("No eligible drivers found. Set canDrive=true for at least one member.");
        }

        List<DayRequest> days = (request.days() == null || request.days().isEmpty())
                ? defaultWeekdays()
                : request.days();

        Map<String, Integer> driverAssignments = new LinkedHashMap<>();
        for (MemberInfo d : drivers) {
            driverAssignments.put(d.name(), 0);
        }

        return new WeeklyRouteContext(office, members, drivers, days, driverAssignments);
    }

    private List<DailyRoutePlanDto> buildWeeklyPlans(WeeklyRouteContext context) {
        List<DailyRoutePlanDto> plannedDays = new ArrayList<>();

        for (DayRequest day : context.days()) {
            requireNotBlank(day.day(), "days.day");
            TripType tripType = parseTripType(day.tripType());

            MemberInfo driver = selectDriver(day.fixedDriverName(), context.drivers(), context.driverAssignments());
            context.driverAssignments().put(driver.name(), context.driverAssignments().get(driver.name()) + 1);

            List<Stop> pickups = context.members().stream()
                    .filter(m -> !m.name().equalsIgnoreCase(driver.name()))
                    .map(MemberInfo::stop)
                    .toList();

            RoutePlanningService.PlannedRoute plan = routePlanningService.planRoute(
                    tripType,
                    driver.stop(),
                    context.office(),
                    pickups
            );

            plannedDays.add(new DailyRoutePlanDto(
                    day.day(),
                    tripType.name(),
                    toApiStop(driver.stop()),
                    tripType == TripType.MORNING_TO_OFFICE
                            ? plan.orderedStops().stream().map(this::toApiStop).toList()
                            : List.of(),
                    tripType == TripType.EVENING_TO_HOME
                            ? plan.orderedStops().stream().map(this::toApiStop).toList()
                            : List.of(),
                    plan.totalDistanceKm(),
                    toMinutes(plan.totalDurationSeconds())
            ));
        }

        return plannedDays;
    }

    private List<DayRequest> defaultWeekdays() {
        return List.of(
                new DayRequest("Monday", null, null),
                new DayRequest("Tuesday", null, null),
                new DayRequest("Wednesday", null, null),
                new DayRequest("Thursday", null, null),
                new DayRequest("Friday", null, null)
        );
    }

    MemberInfo selectDriver(String fixedDriverName, List<MemberInfo> drivers, Map<String, Integer> assignments) {
        if (fixedDriverName != null && !fixedDriverName.isBlank()) {
            return drivers.stream()
                    .filter(d -> d.name().equalsIgnoreCase(fixedDriverName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Fixed driver not found or not eligible: " + fixedDriverName));
        }

        return drivers.stream()
                .sorted(Comparator.comparingInt((MemberInfo d) -> assignments.getOrDefault(d.name(), 0))
                        .thenComparing(MemberInfo::name, String.CASE_INSENSITIVE_ORDER))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No eligible driver available."));
    }

    private Stop geocodeStop(String id, String label, StopType type, String postalCode, String countryCode) {
        return new Stop(id, label, type, postalCode, geocodingService.geocodePostalCode(postalCode, countryCode));
    }

    private Stop geocodeStop(String id, String label, StopType type, String postalCode, String street, String houseNumber, String countryCode) {
        return new Stop(id, label, type, postalCode, geocodingService.geocodePostalCode(postalCode, countryCode, street, houseNumber));
    }

    private long toMinutes(long seconds) {
        return Math.max(1L, (seconds + 59L) / 60L);
    }

    private ApiStopDto toApiStop(Stop stop) {
        return new ApiStopDto(
                stop.id(),
                stop.label(),
                stop.addressOrPostalCode(),
                stop.coordinates().latitude(),
                stop.coordinates().longitude()
        );
    }

    private void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
    }

    private String sanitizeIdPart(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private TripType parseTripType(String rawTripType) {
        if (rawTripType == null || rawTripType.isBlank()) {
            return TripType.MORNING_TO_OFFICE;
        }

        try {
            return TripType.valueOf(rawTripType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid tripType. Supported values: MORNING_TO_OFFICE, EVENING_TO_HOME"
            );
        }
    }

    Map<String, Long> cacheStats() {
        Map<String, Long> geocodeStats = geocodingService.cacheStats();
        Map<String, Long> routesStats = routesService.cacheStats();

        return Map.of(
                "geocodeHits", geocodeStats.getOrDefault("hits", 0L),
                "geocodeMisses", geocodeStats.getOrDefault("misses", 0L),
                "geocodeSize", geocodeStats.getOrDefault("size", 0L),
                "routesHits", routesStats.getOrDefault("hits", 0L),
                "routesMisses", routesStats.getOrDefault("misses", 0L),
                "routesSize", routesStats.getOrDefault("size", 0L)
        );
    }

    record MemberInfo(String name, boolean canDrive, Stop stop) {
    }

    record SingleRouteContext(TripType tripType, Stop driver, Stop office, List<Stop> pickups) {
    }

    record WeeklyRouteContext(
            Stop office,
            List<MemberInfo> members,
            List<MemberInfo> drivers,
            List<DayRequest> days,
            Map<String, Integer> driverAssignments
    ) {
    }
}
