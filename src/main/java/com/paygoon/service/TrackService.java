package com.paygoon.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paygoon.dto.TrackRouteSummaryResponse;
import com.paygoon.dto.TrackUploadRequest;
import com.paygoon.dto.TrackResponse;
import com.paygoon.dto.TrackGpxResponse;
import com.paygoon.model.AppUser;
import com.paygoon.model.Route;
import com.paygoon.model.Track;
import com.paygoon.repository.RouteRepository;
import com.paygoon.repository.TrackRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrackService {

    private final TrackRepository trackRepository;
    private final RouteRepository routeRepository;

    public List<TrackResponse> getAllTracks() {
        return trackRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<TrackRouteSummaryResponse> getTracksByRoute(Long routeId) {
        return trackRepository.findRouteSummariesByRouteId(routeId);
    }

    @Transactional
    public Track createTrack(TrackUploadRequest request, AppUser creator) {
        Route route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new EntityNotFoundException("Route not found"));

        Track track = new Track();
        track.setRoute(route);
        track.setNickname(resolveNickname(creator));
        track.setCategory(request.category());
        track.setBikeType(request.bikeType());
        track.setTimeSeconds(request.timeSeconds());
        track.setTiempoReal(request.tiempoReal());
        track.setDuracionRecorrido(request.duracionRecorrido());
        track.setDistanceKm(request.distanceKm());
        track.setRouteXml(request.routeXml());
        track.setFileName(request.fileName());
        track.setUploadedAt(request.uploadedAt() != null ? request.uploadedAt() : LocalDateTime.now());
        track.setCreatedBy(creator);

        return trackRepository.save(track);
    }

    public TrackGpxResponse getTrackGpx(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new EntityNotFoundException("Track not found"));

        return new TrackGpxResponse(track.getId(), track.getFileName(), track.getRouteXml());
    }

    private TrackResponse mapToResponse(Track track) {
        return new TrackResponse(
                track.getId(),
                track.getRoute() != null ? track.getRoute().getId() : null,
                track.getNickname(),
                track.getCategory(),
                track.getBikeType(),
                track.getTimeSeconds(),
                track.getTiempoReal(),
                track.getDuracionRecorrido(),
                track.getDistanceKm(),
                track.getFileName(),
                track.getUploadedAt(),
                track.getCreatedBy() != null ? track.getCreatedBy().getId() : null
        );
    }

    private String resolveNickname(AppUser creator) {
        if (creator.getNickname() != null && !creator.getNickname().isBlank()) {
            return creator.getNickname();
        }

        if (creator.getName() != null && !creator.getName().isBlank()) {
            return creator.getName();
        }

        return creator.getEmail();
    }
}
