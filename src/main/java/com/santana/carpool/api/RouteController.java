package com.santana.carpool.api;

import com.santana.carpool.api.dto.RouteRequestDto;
import com.santana.carpool.api.dto.RouteResponseDto;
import com.santana.carpool.api.dto.WeeklyRouteRequestDto;
import com.santana.carpool.api.dto.WeeklyRouteResponseDto;
import com.santana.carpool.service.RouteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RouteController {
    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/route")
    public RouteResponseDto route(@RequestBody RouteRequestDto request) {
        return routeService.planSingleRoute(request);
    }

    @PostMapping("/weekly-route")
    public WeeklyRouteResponseDto weeklyRoute(@RequestBody WeeklyRouteRequestDto request) {
        return routeService.planWeeklyRoute(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> internal(IllegalStateException ex) {
        return Map.of("error", ex.getMessage());
    }
}
