package com.paygoon.service;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.paygoon.dto.TrackRouteSummaryResponse;
import com.paygoon.dto.TrackUploadRequest;
import com.paygoon.dto.TrackResponse;
import com.paygoon.controller.AuthenticationController;
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

    private static final short ASCENT_ALGORITHM_VERSION = 2;

//    private final AuthenticationController authenticationController;

    private final TrackRepository trackRepository;
    private final RouteRepository routeRepository;

//    TrackService(AuthenticationController authenticationController) {
//        this.authenticationController = authenticationController;
//		
//    }

    @Transactional
    public List<TrackResponse> getAllTracks() {
        return trackRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<TrackResponse> getTracksByCreator(Long creatorId) {
        return trackRepository.findByCreatedById(creatorId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<TrackRouteSummaryResponse> getTracksByRoute(Long routeId) {
        return trackRepository.findRouteSummariesByRouteId(routeId);
    }

    @Transactional
    public List<TrackResponse> getSharedTracksExcludingUser(Long userId) {
        return trackRepository.findBySharedTrueAndCreatedByIdNot(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public Track createTrack(TrackUploadRequest request, AppUser creator) {
//        Route route = routeRepository.findById(request.routeId())
//                .orElseThrow(() -> new EntityNotFoundException("Route not found"));

    	Track track = new Track();
    	
    	if (request.routeId() != null) {
            Route route = routeRepository.findById(request.routeId())
                    .orElseThrow(() -> new EntityNotFoundException("Route not found: " + request.routeId()));
            track.setRoute(route);
        } else {
            track.setRoute(null);
        }
    	        
        track.setNickname(resolveNickname(creator));
        track.setCategory(request.category());
        track.setBikeType(request.bikeType());
        track.setTimeSeconds(request.timeSeconds());
        track.setTiempoReal(request.tiempoReal());
        track.setDuracionRecorrido(request.duracionRecorrido());
        track.setDistanceKm(request.distanceKm());
        Long conservativeAscent = calculateAscent(request.routeXml());
        track.setDesnivel(conservativeAscent != null ? conservativeAscent : request.ascent());
        track.setDesnivelVersion(ASCENT_ALGORITHM_VERSION);
        track.setDifficultyScore(request.difficultyScore());
        track.setDifficultyLevel(request.difficultyLevel());
        track.setStartLat(request.startLat());
        track.setStartLon(request.startLon());
        track.setRouteXml(request.routeXml());
        track.setFileName(request.fileName());
        track.setYear(request.year());
        track.setAutonomousCommunity(request.autonomousCommunity());
        track.setProvince(request.province());
        track.setComarca(request.comarca());
        track.setPopulation(request.population());
        track.setUploadedAt(request.uploadedAt() != null ? request.uploadedAt() : LocalDateTime.now());
        track.setCreatedBy(creator);
        track.setTitle(request.title());
        track.setShared(Boolean.TRUE.equals(request.shared()));


        return trackRepository.save(track);
    }

    public TrackGpxResponse getTrackGpx(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new EntityNotFoundException("Track not found"));

        return new TrackGpxResponse(track.getId(), track.getFileName(), track.getRouteXml());
    }

    @Transactional
    public void deleteTrack(Long trackId) {
        if (!trackRepository.existsById(trackId)) {
            throw new EntityNotFoundException("Track not found: " + trackId);
        }

        trackRepository.deleteById(trackId);
    }

    private TrackResponse mapToResponse(Track track) {
        ensureStoredAscent(track);
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
                track.getDesnivel(),
                track.getDifficultyScore(),
                track.getDifficultyLevel(),
                track.getFileName(),
                track.getYear(),
                track.getAutonomousCommunity(),
                track.getProvince(),
                track.getComarca(),
                track.getPopulation(),
                buildStartLocationUrl(track),
                track.getUploadedAt(),
                track.getCreatedBy() != null ? track.getCreatedBy().getId() : null,
                track.getTitle(),
                track.isShared(),
                track.getStartLat(),
                track.getStartLon()
        );
    }

    private void ensureStoredAscent(Track track) {
        if (Short.valueOf(ASCENT_ALGORITHM_VERSION).equals(track.getDesnivelVersion())
                || track.getRouteXml() == null || track.getRouteXml().isBlank()) {
            return;
        }
        Long calculated = calculateAscent(track.getRouteXml());
        if (calculated != null) {
            track.setDesnivel(calculated);
            track.setDesnivelVersion(ASCENT_ALGORITHM_VERSION);
        }
    }

    private Long calculateAscent(String routeXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            NodeList points = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(routeXml)))
                    .getElementsByTagNameNS("*", "trkpt");
            List<Double> elevations = new ArrayList<>();
            Double previousRaw = null;
            for (int index = 0; index < points.getLength(); index++) {
                Element point = (Element) points.item(index);
                NodeList elevationNodes = point.getElementsByTagNameNS("*", "ele");
                if (elevationNodes.getLength() == 0) continue;
                double current = Double.parseDouble(elevationNodes.item(0).getTextContent().trim());
                if (previousRaw == null || Math.abs(current - previousRaw) < 100) {
                    elevations.add(current);
                }
                previousRaw = current;
            }
            if (elevations.isEmpty()) return null;

            double[] smoothed = new double[elevations.size()];
            for (int index = 0; index < elevations.size(); index++) {
                int from = Math.max(0, index - 4);
                int to = Math.min(elevations.size() - 1, index + 4);
                double sum = 0;
                for (int sample = from; sample <= to; sample++) sum += elevations.get(sample);
                smoothed[index] = sum / (to - from + 1);
            }

            double gain = 0;
            double valley = smoothed[0];
            double peak = smoothed[0];
            boolean climbing = false;
            for (double elevation : smoothed) {
                if (!climbing && elevation < valley) {
                    valley = elevation;
                    peak = elevation;
                }
                if (elevation > peak) peak = elevation;
                if (peak - valley >= 5) climbing = true;
                if (climbing && peak - elevation >= 5) {
                    gain += peak - valley;
                    valley = elevation;
                    peak = elevation;
                    climbing = false;
                }
            }
            if (peak - valley >= 5) gain += peak - valley;
            return Math.round(gain);
        } catch (Exception ignored) {
            return null;
        }
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

    private String buildStartLocationUrl(Track track) {
        if (track.getStartLat() != null && track.getStartLon() != null) {
            return "https://www.google.com/maps/search/?api=1&query="
                    + track.getStartLat().toPlainString()
                    + ","
                    + track.getStartLon().toPlainString();
        }

        String population = track.getPopulation();
        if (population == null || population.isBlank()) {
            return null;
        }

        return "https://www.google.com/maps/search/?api=1&query="
                + URLEncoder.encode(population, StandardCharsets.UTF_8);
    }
}
