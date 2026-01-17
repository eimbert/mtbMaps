package com.paygoon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.annotation.Validated;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.PlanFolderCreateRequest;
import com.paygoon.dto.PlanFolderCreateResponse;
import com.paygoon.dto.PlanFolderListItemResponse;
import com.paygoon.dto.PlanTrackImportRequest;
import com.paygoon.dto.PlanTrackImportResponse;
import com.paygoon.dto.PlanTrackListItemResponse;
import com.paygoon.dto.PlanTrackVoteListItemResponse;
import com.paygoon.model.AppUser;
import com.paygoon.model.PlanFolder;
import com.paygoon.model.PlanFolderMember;
import com.paygoon.model.PlanTrack;
import com.paygoon.model.PlanTrackVote;
import com.paygoon.repository.PlanFolderRepository;
import com.paygoon.repository.PlanFolderMemberRepository;
import com.paygoon.repository.PlanTrackRepository;
import com.paygoon.repository.PlanTrackVoteRepository;
import com.paygoon.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/plan-folders")
@RequiredArgsConstructor
@Validated
public class PlanFolderController {

    private final PlanFolderRepository planFolderRepository;
    private final PlanFolderMemberRepository planFolderMemberRepository;
    private final PlanTrackRepository planTrackRepository;
    private final PlanTrackVoteRepository planTrackVoteRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<PlanFolderListItemResponse>> getPlanFolders(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        List<PlanFolderListItemResponse> response = new ArrayList<>();

        List<PlanFolder> ownedFolders = planFolderRepository.findByOwner(user);
        for (PlanFolder folder : ownedFolders) {
            response.add(new PlanFolderListItemResponse(
                    folder.getId(),
                    folder.getOwner().getId(),
                    folder.getName(),
                    folder.getPlannedDate(),
                    folder.getObservations(),
                    folder.getCreatedAt(),
                    folder.getUpdatedAt(),
                    null
            ));
        }

        List<PlanFolderMember> memberFolders = planFolderMemberRepository.findByUser(user);
        for (PlanFolderMember member : memberFolders) {
            PlanFolder folder = member.getFolder();
            if (folder.getOwner() != null && folder.getOwner().getId().equals(user.getId())) {
                continue;
            }
            response.add(new PlanFolderListItemResponse(
                    folder.getId(),
                    folder.getOwner() != null ? folder.getOwner().getId() : null,
                    folder.getName(),
                    folder.getPlannedDate(),
                    folder.getObservations(),
                    folder.getCreatedAt(),
                    folder.getUpdatedAt(),
                    member.getRole()
            ));
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<PlanFolderCreateResponse> createPlanFolder(
            @Valid @RequestBody PlanFolderCreateRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanFolderCreateResponse(null, "No autenticado", -1));
        }

        AppUser owner = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        try {
            PlanFolder planFolder = new PlanFolder();
            planFolder.setOwner(owner);
            planFolder.setName(request.name());
            planFolder.setPlannedDate(request.plannedDate());
            planFolder.setObservations(request.observations());

            PlanFolder savedPlanFolder = planFolderRepository.save(planFolder);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new PlanFolderCreateResponse(
                            savedPlanFolder.getId(),
                            "Carpeta de plan creada correctamente",
                            0
                    ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlanFolderCreateResponse(null, "No se pudo crear la carpeta del plan", -99));
        }
    }

    @PostMapping("/tracks/import")
    public ResponseEntity<PlanTrackImportResponse> importPlanTrack(
            @Valid @RequestBody PlanTrackImportRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanTrackImportResponse(null, "No autenticado", -1));
        }

        AppUser requester = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        PlanFolder folder = planFolderRepository.findById(request.folderId())
                .orElse(null);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanTrackImportResponse(null, "Carpeta no encontrada", -2));
        }

        try {
            PlanTrack planTrack = new PlanTrack();
            planTrack.setFolder(folder);
            planTrack.setCreatedBy(requester);
            planTrack.setName(request.name());
            planTrack.setStartLat(request.startLat());
            planTrack.setStartLon(request.startLon());
            planTrack.setStartPopulation(request.startPopulation());
            planTrack.setDistanceKm(request.distanceKm());
            planTrack.setMovingTimeSec(request.movingTimeSec());
            planTrack.setTotalTimeSec(request.totalTimeSec());
            planTrack.setRouteXml(request.routeXml());
            planTrack.setSourceType(PlanTrack.SourceType.import_gpx);

            PlanTrack savedPlanTrack = planTrackRepository.save(planTrack);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new PlanTrackImportResponse(
                            savedPlanTrack.getId(),
                            "Track importado correctamente",
                            0
                    ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlanTrackImportResponse(null, "No se pudo importar el track", -99));
        }
    }

    @GetMapping("/{folderId}/tracks")
    public ResponseEntity<List<PlanTrackListItemResponse>> getPlanTracks(
            @Valid @PathVariable Long folderId,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        PlanFolder folder = planFolderRepository.findById(folderId).orElse(null);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of());
        }

        List<PlanTrack> tracks = planTrackRepository.findByFolderId(folderId);
        List<PlanTrackListItemResponse> response = new ArrayList<>();
        for (PlanTrack track : tracks) {
            response.add(new PlanTrackListItemResponse(
                    track.getId(),
                    folderId,
                    track.getCreatedBy() != null ? track.getCreatedBy().getId() : null,
                    track.getName(),
                    track.getStartLat(),
                    track.getStartLon(),
                    track.getStartPopulation(),
                    track.getDistanceKm(),
                    track.getMovingTimeSec(),
                    track.getTotalTimeSec(),
                    track.getHowToGetUrl(),
                    track.getSourceType(),
                    track.getGpxStoragePath(),
                    track.getRouteXml(),
                    track.getSortOrder(),
                    track.getCreatedAt()
            ));
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/tracks/{trackId}")
    public ResponseEntity<Void> deletePlanTrack(
            @Valid @PathVariable Long trackId,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PlanTrack track = planTrackRepository.findById(trackId).orElse(null);
        if (track == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        planTrackVoteRepository.deleteByTrackId(trackId);
        planTrackRepository.delete(track);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{folderId}/votes")
    public ResponseEntity<List<PlanTrackVoteListItemResponse>> getPlanTrackVotes(
            @Valid @PathVariable Long folderId,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        PlanFolder folder = planFolderRepository.findById(folderId).orElse(null);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of());
        }

        List<PlanTrackVote> votes = planTrackVoteRepository.findByFolderId(folderId);
        List<PlanTrackVoteListItemResponse> response = new ArrayList<>();
        for (PlanTrackVote vote : votes) {
            response.add(new PlanTrackVoteListItemResponse(
                    vote.getId(),
                    folderId,
                    vote.getUser() != null ? vote.getUser().getId() : null,
                    vote.getTrack() != null ? vote.getTrack().getId() : null,
                    vote.getCreatedAt(),
                    vote.getUpdatedAt()
            ));
        }

        return ResponseEntity.ok(response);
    }
}
