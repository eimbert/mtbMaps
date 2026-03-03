package com.paygoon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
        when(directionsClient.fetchRoundTrip(anyString(), anyList(), anyDouble(), anyInt(), anyInt()))
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
                new RoundTripRouteRequest.Start(41.637, 2.361)));

        assertEquals(2, response.geometry().coordinates().size());
        assertEquals(34780.2, response.distanceMeters());
        assertEquals(6910.4, response.durationSeconds());
        assertEquals(812.7, response.ascentMeters());
    }

    @Test
    void shouldRejectInvalidProfile() {
        RoundTripRoutingException ex = assertThrows(RoundTripRoutingException.class,
                () -> service.generateRoundTrip(new RoundTripRouteRequest(
                        "driving-car",
                        "medium",
                        35.0,
                        new RoundTripRouteRequest.Start(41.637, 2.361))));

        assertEquals("INVALID_PROFILE", ex.getErrorCode());
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void shouldRejectOrsResponseWithTooFewCoordinates() {
        when(directionsClient.fetchRoundTrip(anyString(), anyList(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new OpenRouteServiceDirectionsClient.DirectionsResult(
                        List.of(List.of(2.361000, 41.637000)),
                        1000.0,
                        100.0,
                        10.0));

        RoundTripRoutingException ex = assertThrows(RoundTripRoutingException.class,
                () -> service.generateRoundTrip(new RoundTripRouteRequest(
                        "cycling-mountain",
                        "technical",
                        35.0,
                        new RoundTripRouteRequest.Start(41.637, 2.361))));

        assertEquals("ORS_INVALID_RESPONSE", ex.getErrorCode());
        assertEquals(502, ex.getStatus().value());
    }
}
