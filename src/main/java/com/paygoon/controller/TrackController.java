package com.paygoon.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.TrackGpxResponse;
import com.paygoon.dto.TrackResponse;
import com.paygoon.dto.TrackRouteSummaryResponse;
import com.paygoon.dto.TrackUploadRequest;
import com.paygoon.dto.TrackUploadResponse;
import com.paygoon.model.AppUser;
import com.paygoon.model.Track;
import com.paygoon.repository.UserRepository;
import com.paygoon.service.TrackService;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
@Validated
public class TrackController {

    private final TrackService trackService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Iterable<TrackResponse>> getAllTracks() {
        Iterable<TrackResponse> tracks = trackService.getAllTracks();
        return ResponseEntity.ok(tracks);
    }

    @GetMapping("/me")
    public ResponseEntity<List<TrackResponse>> getMyTracks(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser creator = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        List<TrackResponse> tracks = trackService.getTracksByCreator(creator.getId());
        return ResponseEntity.ok(tracks);
    }

    @GetMapping("/shared")
    public ResponseEntity<List<TrackResponse>> getSharedTracks(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser requester = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        List<TrackResponse> tracks = trackService.getSharedTracksExcludingUser(requester.getId());
        return ResponseEntity.ok(tracks);
    }

    @GetMapping("/route/{routeId}")
    public ResponseEntity<List<TrackRouteSummaryResponse>> getTracksByRoute(@PathVariable Long routeId) {
        List<TrackRouteSummaryResponse> tracks = trackService.getTracksByRoute(routeId);
        return ResponseEntity.ok(tracks);
    }

    @GetMapping("/{id}/gpx")
    public ResponseEntity<TrackGpxResponse> getTrackGpx(@PathVariable Long id) {
        try {
            TrackGpxResponse response = trackService.getTrackGpx(id);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping
    public ResponseEntity<TrackUploadResponse> uploadTrack(
            @Valid @RequestBody TrackUploadRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new TrackUploadResponse(null, "No autenticado", -1));
        }

        AppUser creator = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        try {
            Track savedTrack = trackService.createTrack(request, creator);
            TrackUploadResponse response = new TrackUploadResponse(
                    savedTrack.getId(),
                    "Track subido correctamente",
                    0
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new TrackUploadResponse(null, "Ruta no encontrada", -2));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new TrackUploadResponse(null, "No se pudo subir el track", -99));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrack(@PathVariable Long id) {
        try {
            trackService.deleteTrack(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
