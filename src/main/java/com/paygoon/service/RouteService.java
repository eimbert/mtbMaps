package com.paygoon.service;

import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paygoon.dto.RouteCreateRequest;
import com.paygoon.model.AppUser;
import com.paygoon.model.Route;
import com.paygoon.repository.RouteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;

    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    @Transactional
    public Route createRoute(RouteCreateRequest request, AppUser creator) {
        Route route = new Route();
        route.setName(request.name());
        route.setPopulation(request.population());
        route.setAutonomousCommunity(request.autonomousCommunity());
        route.setYear(request.year());
        route.setLogoMime(request.logoMime());
        route.setCreatedBy(creator);

        if (request.logoBase64() != null && !request.logoBase64().isBlank()) {
            byte[] decodedLogo = Base64.getDecoder().decode(request.logoBase64());
            route.setLogoBlob(decodedLogo);
        }

        return routeRepository.save(route);
    }
}
