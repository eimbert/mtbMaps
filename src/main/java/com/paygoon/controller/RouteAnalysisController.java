package com.paygoon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.RouteAnalysisRequest;
import com.paygoon.dto.RouteAnalysisResponse;
import com.paygoon.dto.PublicRouteAnalysisResponse;
import java.util.List;
import com.paygoon.model.AppUser;
import com.paygoon.repository.UserRepository;
import com.paygoon.service.RouteAnalysisService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/route-analysis")
@RequiredArgsConstructor
@Validated
public class RouteAnalysisController {

    private final RouteAnalysisService routeAnalysisService;
    private final UserRepository userRepository;

    @GetMapping("/public")
    public ResponseEntity<List<PublicRouteAnalysisResponse>> getPublicAnalyses() {
        return ResponseEntity.ok(routeAnalysisService.findPublicAnalyses());
    }

    @GetMapping("/public/{analysisId}")
    public ResponseEntity<PublicRouteAnalysisResponse> getPublicAnalysis(@PathVariable Long analysisId) {
        return routeAnalysisService.findPublicAnalysis(analysisId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/elevation")
    public ResponseEntity<RouteAnalysisResponse.RouteAnalysisStats> calculateElevation(
            @Valid @RequestBody RouteAnalysisRequest request,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(routeAnalysisService.calculateElevation(request.routeXml()));
    }

    @PostMapping
    public ResponseEntity<RouteAnalysisResponse> analyzeRoute(
            @Valid @RequestBody RouteAnalysisRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        return ResponseEntity.ok(routeAnalysisService.analyze(request, user));
    }

    @PostMapping("/lookup")
    public ResponseEntity<RouteAnalysisResponse> lookupExisting(
            @Valid @RequestBody RouteAnalysisRequest request,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return routeAnalysisService.findExisting(request.routeXml())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/track/{trackId}")
    public ResponseEntity<RouteAnalysisResponse> getTrackAnalysis(
            @PathVariable Long trackId,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        return routeAnalysisService.findByTrack(trackId, user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{analysisId}/refresh")
    public ResponseEntity<RouteAnalysisResponse> refreshAnalysis(
            @PathVariable Long analysisId,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        return routeAnalysisService.refresh(analysisId, user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
