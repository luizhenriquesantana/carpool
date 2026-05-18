package com.santana.carpool.api;

import com.santana.carpool.api.dto.RouteRequestDto;
import com.santana.carpool.api.dto.RouteResponseDto;
import com.santana.carpool.api.dto.WeeklyRouteRequestDto;
import com.santana.carpool.api.dto.WeeklyRouteResponseDto;
import com.santana.carpool.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Tag(name = "Routes", description = "Carpool route optimization endpoints")
public class RouteController {
    private static final Logger log = LoggerFactory.getLogger(RouteController.class);
    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @Operation(
            summary = "Plan a single carpool route",
            description = "Geocodes all postal codes, optimizes pickup order using exhaustive search (<=8 pickups) or greedy fallback, and returns the optimal route with distance and duration.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Route planned successfully",
                            content = @Content(schema = @Schema(implementation = RouteResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request (missing fields, invalid trip type)"),
                    @ApiResponse(responseCode = "500", description = "Internal error (geocoding/routing API failure)")
            }
    )
    @PostMapping("/route")
    public RouteResponseDto route(@Valid @RequestBody RouteRequestDto request) {
        log.info("POST /api/route - driver={}, colleagues={}", request.driverName(), request.colleagues() != null ? request.colleagues().size() : 0);
        return routeService.planSingleRoute(request);
    }

    @Operation(
            summary = "Plan weekly carpool routes",
            description = "Plans routes for each day of the week with fair driver rotation. Defaults to Monday-Friday if no days are specified.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Weekly routes planned successfully",
                            content = @Content(schema = @Schema(implementation = WeeklyRouteResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request (missing fields, no eligible drivers)"),
                    @ApiResponse(responseCode = "500", description = "Internal error (geocoding/routing API failure)")
            }
    )
    @PostMapping("/weekly-route")
    public WeeklyRouteResponseDto weeklyRoute(@Valid @RequestBody WeeklyRouteRequestDto request) {
        log.info("POST /api/weekly-route - members={}, days={}", request.members() != null ? request.members().size() : 0, request.days() != null ? request.days().size() : "default");
        return routeService.planWeeklyRoute(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> internal(IllegalStateException ex) {
        log.error("Internal error: {}", ex.getMessage(), ex);
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> validationError(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        return Map.of("error", "Validation failed", "details", errors);
    }
}
