package com.paygoon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import com.paygoon.components.OpenRouteServiceDirectionsClient;
import com.paygoon.dto.RoundTripRouteRequest;
import com.paygoon.dto.RoundTripRouteResponse;

class RoundTripRoutingServiceTest {

    @Mock
    private OpenRouteServiceDirectionsClient directionsClient;

    private RoundTripRoutingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RoundTripRoutingService(directionsClient);
    }

    @Test
    void shouldReturnNormalizedRoundTripResponse() {
        when(directionsClient.fetchRoundTrip(anyString(), anyList(), anyMap()))
                .thenReturn(new OpenRouteServiceDirectionsClient.DirectionsResult(
                        List.of(
                                List.of(2.361000, 41.637000, 123.4),
                                List.of(2.362100, 41.638200, 125.0)),
                        34780.2,
                        6910.4,
                        812.7));

        RoundTripRouteResponse response = service.generateRoundTrip(new RoundTripRouteRequest(
                "cycling-mountain",
                "medium",
                35.0,
                new RoundTripRouteRequest.Start(41.637, 2.361),
                new RoundTripRouteRequest.Preferences(
                        List.of("highways"),
                        new RoundTripRouteRequest.Weightings(0.8, 0.7),
                        "trails",
                        1.0)));

        assertEquals(2, response.geometry().coordinates().size());
        assertEquals(34780.2, response.distanceMeters());
        assertEquals(6910.4, response.durationSeconds());
        assertEquals(812.7, response.ascentMeters());
        assertEquals(0, response.fallbackLevel());
        assertNotNull(response.appliedOptions());
    }

    @Test
    void shouldRejectInvalidProfile() {
        RoundTripRoutingException ex = assertThrows(RoundTripRoutingException.class,
                () -> service.generateRoundTrip(new RoundTripRouteRequest(
                        "driving-car",
                        "medium",
                        35.0,
                        new RoundTripRouteRequest.Start(41.637, 2.361),
                        null)));

        assertEquals("INVALID_PROFILE", ex.getErrorCode());
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void shouldFallbackAfterUpstreamError() {
        when(directionsClient.fetchRoundTrip(anyString(), anyList(), anyMap()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "boom"))
                .thenReturn(new OpenRouteServiceDirectionsClient.DirectionsResult(
                        List.of(
                                List.of(2.361000, 41.637000),
                                List.of(2.362100, 41.638200)),
                        1000.0,
                        100.0,
                        10.0));

        RoundTripRouteResponse response = service.generateRoundTrip(new RoundTripRouteRequest(
                "cycling-mountain",
                "technical",
                35.0,
                new RoundTripRouteRequest.Start(41.637, 2.361),
                null));

        assertEquals(1, response.fallbackLevel());
        assertNotNull(response.warnings());
    }


    @Test
    void shouldSupportAvoidAsphaltModeAlias() {
        when(directionsClient.fetchRoundTrip(anyString(), anyList(), anyMap()))
                .thenReturn(new OpenRouteServiceDirectionsClient.DirectionsResult(
                        List.of(
                                List.of(2.361000, 41.637000),
                                List.of(2.362100, 41.638200)),
                        18000.0,
                        3600.0,
                        250.0));

        RoundTripRouteResponse response = service.generateRoundTrip(new RoundTripRouteRequest(
                "cycling-mountain",
                "medium",
                15.0,
                new RoundTripRouteRequest.Start(41.637, 2.361),
                new RoundTripRouteRequest.Preferences(
                        List.of("highways"),
                        null,
                        "avoid-asphalt",
                        1.0)));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> profileParams = (java.util.Map<String, Object>) response.appliedOptions()
                .get("profile_params");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> weightings = (java.util.Map<String, Object>) profileParams.get("weightings");

        assertEquals(0.9, ((Number) weightings.get("green")).doubleValue());
        assertEquals(0.7, ((Number) weightings.get("quiet")).doubleValue());
    }

    @Test
    void shouldRejectUnsupportedAvoidFeature() {
        RoundTripRoutingException ex = assertThrows(RoundTripRoutingException.class,
                () -> service.generateRoundTrip(new RoundTripRouteRequest(
                        "cycling-mountain",
                        "technical",
                        35.0,
                        new RoundTripRouteRequest.Start(41.637, 2.361),
                        new RoundTripRouteRequest.Preferences(
                                List.of("unpaved"),
                                null,
                                "balanced",
                                1.0))));

        assertEquals("INVALID_AVOID_FEATURE", ex.getErrorCode());
        assertEquals(400, ex.getStatus().value());
    }
}
