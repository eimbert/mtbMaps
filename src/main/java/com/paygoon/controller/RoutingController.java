package com.paygoon.controller;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.ApiErrorResponse;
import com.paygoon.dto.ProximityMatrixRequest;
import com.paygoon.dto.ProximityMatrixResponse;
import com.paygoon.dto.RoundTripRouteRequest;
import com.paygoon.dto.RoundTripRouteResponse;
import com.paygoon.service.RoundTripRoutingException;
import com.paygoon.service.RoundTripRoutingService;
import com.paygoon.service.RoutingMatrixService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/routing")
@RequiredArgsConstructor
@Validated
public class RoutingController {

    private final RoutingMatrixService routingMatrixService;
    private final RoundTripRoutingService roundTripRoutingService;

    @PostMapping("/matrix-rank")
    public ProximityMatrixResponse matrixRank(
            @Valid @RequestBody ProximityMatrixRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest) {
        return routingMatrixService.rankByRoad(request, authentication, httpServletRequest);
    }

    @PostMapping("/round-trip")
    public RoundTripRouteResponse roundTrip(@Valid @RequestBody RoundTripRouteRequest request) {
        return roundTripRoutingService.generateRoundTrip(request);
    }

    @ExceptionHandler(RoundTripRoutingException.class)
    public ResponseEntity<ApiErrorResponse> handleRoundTripError(RoundTripRoutingException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }
}
