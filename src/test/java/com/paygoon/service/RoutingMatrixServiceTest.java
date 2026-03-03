package com.paygoon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import com.paygoon.components.OpenRouteServiceMatrixClient;
import com.paygoon.dto.ProximityMatrixRequest;
import com.paygoon.dto.ProximityMatrixResponse;

class RoutingMatrixServiceTest {

    @Mock
    private OpenRouteServiceMatrixClient orsClient;

    private RoutingMatrixService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RoutingMatrixService(orsClient, 10, 100);
    }

    @Test
    void shouldReturnMatrixOrderingByDurationAndTieByTrackId() {
        when(orsClient.fetchMatrix(anyString(), anyList(), anyList(), anyList(), anyList(), anyString()))
                .thenReturn(new OpenRouteServiceMatrixClient.MatrixResult(
                        List.of(List.of(400.0, 200.0, 200.0)),
                        List.of(List.of(1000.0, 2000.0, 3000.0))));

        ProximityMatrixRequest request = buildRequest(List.of(
                new ProximityMatrixRequest.Candidate(30L, 41.4, 2.2, 5.0),
                new ProximityMatrixRequest.Candidate(10L, 41.5, 2.3, 6.0),
                new ProximityMatrixRequest.Candidate(20L, 41.6, 2.4, 4.0)
        ));

        ProximityMatrixResponse response = service.rankByRoad(
                request,
                new UsernamePasswordAuthenticationToken("user@test.com", ""),
                new MockHttpServletRequest());

        assertEquals("matrix", response.strategyUsed());
        assertEquals(List.of(20L, 10L, 30L), response.ordered().stream().map(ProximityMatrixResponse.OrderedItem::trackId).toList());
        assertEquals(List.of(0, 1, 2), response.ordered().stream().map(ProximityMatrixResponse.OrderedItem::rank).toList());
    }

    @Test
    void shouldFallbackToHaversineWhenOrsFails() {
        when(orsClient.fetchMatrix(anyString(), anyList(), anyList(), anyList(), anyList(), anyString()))
                .thenThrow(new RuntimeException("quota exceeded"));

        ProximityMatrixRequest request = buildRequest(List.of(
                new ProximityMatrixRequest.Candidate(2L, 41.4, 2.2, 15.0),
                new ProximityMatrixRequest.Candidate(1L, 41.5, 2.3, 10.0)
        ));

        ProximityMatrixResponse response = service.rankByRoad(request, null, new MockHttpServletRequest());

        assertEquals("haversine", response.strategyUsed());
        assertEquals(List.of(1L, 2L), response.ordered().stream().map(ProximityMatrixResponse.OrderedItem::trackId).toList());
        assertEquals(List.of(null, null), response.ordered().stream().map(ProximityMatrixResponse.OrderedItem::roadDurationS).toList());
    }

    @Test
    void shouldRejectMoreThanTenCandidates() {
        List<ProximityMatrixRequest.Candidate> candidates = java.util.stream.LongStream.rangeClosed(1, 11)
                .mapToObj(i -> new ProximityMatrixRequest.Candidate(i, 41.0, 2.0, 1.0))
                .toList();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.rankByRoad(buildRequest(candidates), null, new MockHttpServletRequest()));

        assertEquals(400, ex.getStatusCode().value());
    }

    private ProximityMatrixRequest buildRequest(List<ProximityMatrixRequest.Candidate> candidates) {
        return new ProximityMatrixRequest(
                "driving-car",
                new ProximityMatrixRequest.Origin(41.38, 2.17),
                candidates,
                new ProximityMatrixRequest.Options(List.of("duration", "distance"), "m", "duration", "haversine")
        );
    }
}
