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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.PlanFolderCreateRequest;
import com.paygoon.dto.PlanFolderCreateResponse;
import com.paygoon.dto.PlanFolderListItemResponse;
import com.paygoon.dto.PlanFolderMemberCreateRequest;
import com.paygoon.dto.PlanFolderMemberCreateResponse;
import com.paygoon.dto.PlanFolderUpdateRequest;
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

    @PostMapping("/members")
    public ResponseEntity<PlanFolderMemberCreateResponse> addPlanFolderMember(
            @Valid @RequestBody PlanFolderMemberCreateRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanFolderMemberCreateResponse(null, "No autenticado", -1));
        }

        AppUser requester = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        PlanFolder folder = planFolderRepository.findById(request.folderId()).orElse(null);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanFolderMemberCreateResponse(null, "Carpeta no encontrada", -2));
        }

        AppUser memberUser = null;
        if (request.userId() != null) {
            memberUser = userRepository.findById(request.userId()).orElse(null);
        } else if (request.email() != null && !request.email().isBlank()) {
            memberUser = userRepository.findByEmail(request.email()).orElse(null);
        } else if (request.nickname() != null && !request.nickname().isBlank()) {
            memberUser = userRepository.findByNickname(request.nickname()).orElse(null);
        }

        if (memberUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanFolderMemberCreateResponse(null, "Usuario no encontrado", -3));
        }

        if (planFolderMemberRepository.existsByFolderIdAndUserId(folder.getId(), memberUser.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new PlanFolderMemberCreateResponse(null, "El usuario ya pertenece a la carpeta", -4));
        }

        try {
            PlanFolderMember member = new PlanFolderMember();
            member.setFolder(folder);
            member.setUser(memberUser);
            member.setNickname(memberUser.getNickname());
            member.setInvitedEmail(request.email());
            member.setInvitedBy(requester);
            member.setStatus(PlanFolderMember.Status.pending);

            PlanFolderMember savedMember = planFolderMemberRepository.save(member);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new PlanFolderMemberCreateResponse(
                            savedMember.getId(),
                            "Miembro agregado correctamente",
                            0
                    ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlanFolderMemberCreateResponse(null, "No se pudo agregar el miembro", -99));
        }
    }

    @PutMapping("/{folderId}")
    public ResponseEntity<PlanFolderCreateResponse> updatePlanFolder(
            @Valid @RequestBody PlanFolderUpdateRequest request,
            @Valid @PathVariable Long folderId,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanFolderCreateResponse(null, "No autenticado", -1));
        }

        AppUser requester = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        PlanFolder folder = planFolderRepository.findById(folderId).orElse(null);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanFolderCreateResponse(null, "Carpeta no encontrada", -2));
        }

        if (folder.getOwner() != null && !folder.getOwner().getId().equals(requester.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new PlanFolderCreateResponse(folderId, "No autorizado", -3));
        }

        try {
            folder.setName(request.name());
            folder.setPlannedDate(request.plannedDate());
            folder.setObservations(request.observations());

            PlanFolder savedPlanFolder = planFolderRepository.save(folder);

            return ResponseEntity.ok(new PlanFolderCreateResponse(
                    savedPlanFolder.getId(),
                    "Carpeta de plan actualizada correctamente",
                    0
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlanFolderCreateResponse(folderId, "No se pudo actualizar la carpeta del plan", -99));
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
