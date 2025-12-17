package com.paygoon.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paygoon.dto.TrackUploadRequest;
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

    public List<Track> getAllTracks() {
        return trackRepository.findAll();
    }

    @Transactional
    public Track createTrack(TrackUploadRequest request, AppUser creator) {
        Route route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new EntityNotFoundException("Route not found"));

        Track track = new Track();
        track.setRoute(route);
        track.setNickname(request.nickname());
        track.setCategory(request.category());
        track.setBikeType(request.bikeType());
        track.setTimeSeconds(request.timeSeconds());
        track.setDistanceKm(request.distanceKm());
        track.setAscentM(request.ascentM());
        track.setGpxAssetUrl(request.gpxAssetUrl());
        track.setRouteXml(request.routeXml());
        track.setFileName(request.fileName());
        track.setUploadedAt(request.uploadedAt() != null ? request.uploadedAt() : LocalDateTime.now());
        track.setCreatedBy(creator);

        return trackRepository.save(track);
    }
}
