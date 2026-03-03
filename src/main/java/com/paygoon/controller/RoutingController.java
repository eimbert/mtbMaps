package com.paygoon.controller;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.ProximityMatrixRequest;
import com.paygoon.dto.ProximityMatrixResponse;
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

    @PostMapping("/matrix-rank")
    public ProximityMatrixResponse matrixRank(
            @Valid @RequestBody ProximityMatrixRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest) {
        return routingMatrixService.rankByRoad(request, authentication, httpServletRequest);
    }
}
