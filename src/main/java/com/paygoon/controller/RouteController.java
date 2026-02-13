package com.paygoon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.RouteCreateRequest;
import com.paygoon.dto.RouteCreateResponse;
import com.paygoon.model.AppUser;
import com.paygoon.model.Route;
import com.paygoon.repository.UserRepository;
import com.paygoon.service.RouteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
@Validated
public class RouteController {

    private final RouteService routeService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Iterable<Route>> getAllRoutes() {
        Iterable<Route> routes = routeService.getAllRoutes();
        return ResponseEntity.ok(routes);
    }

    @PostMapping
    public ResponseEntity<RouteCreateResponse> createRoute(
            @Valid @RequestBody RouteCreateRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new RouteCreateResponse(null, "No autenticado", -1));
        }

        AppUser creator = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        try {
            Route savedRoute = routeService.createRoute(request, creator);
            RouteCreateResponse response = new RouteCreateResponse(
                    savedRoute.getId(),
                    "Ruta creada correctamente",
                    0
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new RouteCreateResponse(null, "Logo en base64 inválido", -2));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RouteCreateResponse(null, "No se pudo crear la ruta", -99));
        }
    }
}
