package com.paygoon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
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
import com.paygoon.dto.PlanFolderInvitationListItemResponse;
import com.paygoon.dto.PlanFolderListItemResponse;
import com.paygoon.dto.PlanFolderMemberCreateRequest;
import com.paygoon.dto.PlanFolderMemberCreateResponse;
import com.paygoon.dto.PlanFolderMemberDeleteRequest;
import com.paygoon.dto.PlanFolderMemberStatusUpdateRequest;
import com.paygoon.dto.PlanFolderMemberStatusUpdateResponse;
import com.paygoon.dto.PlanFolderUpdateRequest;
import com.paygoon.dto.PlanTrackImportRequest;
import com.paygoon.dto.PlanTrackImportResponse;
import com.paygoon.dto.PlanTrackListItemResponse;
import com.paygoon.dto.PlanTrackUpdateRequest;
import com.paygoon.dto.PlanTrackUpdateResponse;
import com.paygoon.dto.PlanTrackVoteCreateRequest;
import com.paygoon.dto.PlanTrackVoteCreateResponse;
import com.paygoon.dto.PlanTrackVoteDeleteRequest;
import com.paygoon.dto.PlanTrackVoteDeleteResponse;
import com.paygoon.dto.PlanTrackVoteListItemResponse;
import com.paygoon.dto.PlanTrackVoteSummaryResponse;
import com.paygoon.model.AppUser;
import com.paygoon.model.Mensaje;
import com.paygoon.model.PlanFolder;
import com.paygoon.model.PlanFolderMember;
import com.paygoon.model.PlanTrack;
import com.paygoon.model.PlanTrackVote;
import com.paygoon.repository.MensajeRepository;
import com.paygoon.repository.PlanFolderRepository;
import com.paygoon.repository.PlanFolderMemberRepository;
import com.paygoon.repository.PlanTrackRepository;
import com.paygoon.repository.PlanTrackVoteRepository;
import com.paygoon.repository.PlanInvitationRepository;
import com.paygoon.repository.UserRepository;
import com.paygoon.service.PlanFolderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/plan-folders")
@RequiredArgsConstructor
@Validated
public class PlanFolderController {

    private static final Logger log = LoggerFactory.getLogger(PlanFolderController.class);

    private final PlanFolderRepository planFolderRepository;
    private final PlanFolderMemberRepository planFolderMemberRepository;
    private final PlanTrackRepository planTrackRepository;
    private final PlanTrackVoteRepository planTrackVoteRepository;
    private final PlanInvitationRepository planInvitationRepository;
    private final MensajeRepository mensajeRepository;
    private final UserRepository userRepository;
    private final PlanFolderService planFolderService;

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

        List<PlanFolderMember> memberFolders = planFolderMemberRepository.findByUserAndStatus(
                user,
                PlanFolderMember.Status.accepted
        );
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

            Mensaje invitationMessage = new Mensaje();
            invitationMessage.setUser(memberUser);
            invitationMessage.setUserMsg(requester);
            invitationMessage.setMensaje("Te han invitado a la carpeta '" + folder.getName() + "'.");
            invitationMessage.setTipoMsg(1);
            invitationMessage.setEstado(null);
            invitationMessage.setIdInvitacion(savedMember.getId());
            mensajeRepository.save(invitationMessage);

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

    @PutMapping("/members")
    public ResponseEntity<PlanFolderMemberStatusUpdateResponse> updatePlanFolderMemberStatus(
            @Valid @RequestBody PlanFolderMemberStatusUpdateRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanFolderMemberStatusUpdateResponse(null, "No autenticado", -1));
        }

        PlanFolderMember member = planFolderMemberRepository.findById(request.id()).orElse(null);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanFolderMemberStatusUpdateResponse(null, "Miembro no encontrado", -2));
        }

        try {
            member.setStatus(request.status());
            PlanFolderMember savedMember = planFolderMemberRepository.save(member);

            return ResponseEntity.ok(new PlanFolderMemberStatusUpdateResponse(
                    savedMember.getId(),
                    "Estado actualizado correctamente",
                    0
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlanFolderMemberStatusUpdateResponse(
                            request.id(),
                            "No se pudo actualizar el estado",
                            -99
                    ));
        }
    }

    @DeleteMapping("/members")
    public ResponseEntity<Void> removePlanFolderMember(
            @Valid @RequestBody PlanFolderMemberDeleteRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PlanFolder folder = planFolderRepository.findById(request.folderId()).orElse(null);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        AppUser memberUser = userRepository.findById(request.userId()).orElse(null);
        if (memberUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        PlanFolderMember member = planFolderMemberRepository
                .findByFolderIdAndUserId(request.folderId(), request.userId())
                .orElse(null);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        planFolderMemberRepository.delete(member);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{folderId}/invitations")
    public ResponseEntity<List<PlanFolderInvitationListItemResponse>> getPlanFolderInvitations(
            @Valid @PathVariable Long folderId,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        PlanFolder folder = planFolderRepository.findById(folderId).orElse(null);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of());
        }

        List<PlanFolderMember> members = planFolderMemberRepository.findByFolderId(folderId);
        List<PlanFolderInvitationListItemResponse> response = new ArrayList<>();
        for (PlanFolderMember member : members) {
            AppUser memberUser = member.getUser();
            response.add(new PlanFolderInvitationListItemResponse(
                    member.getId(),
                    folderId,
                    memberUser != null ? memberUser.getId() : null,
                    memberUser != null ? memberUser.getName() : null,
                    memberUser != null ? memberUser.getEmail() : null,
                    member.getNickname(),
                    member.getRole(),
                    member.getStatus(),
                    member.getModifiedAt()
            ));
        }

        return ResponseEntity.ok(response);
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

    @DeleteMapping("/{folderId}")
    @Transactional
    public ResponseEntity<Void> deletePlanFolder(
            @Valid @PathVariable Long folderId,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser requester = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        PlanFolder folder = planFolderRepository.findById(folderId).orElse(null);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (folder.getOwner() != null && !folder.getOwner().getId().equals(requester.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        planFolderService.deletePlanFolder(folder);

        return ResponseEntity.noContent().build();
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
            planTrack.setDesnivel(request.desnivel());
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

    @PutMapping("/updateTrack")
    public ResponseEntity<PlanTrackUpdateResponse> updatePlanTrack(
            @Valid @RequestBody PlanTrackUpdateRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanTrackUpdateResponse(null, "No autenticado", -1));
        }

        PlanTrack track = planTrackRepository.findById(request.id()).orElse(null);
        if (track == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanTrackUpdateResponse(null, "Track no encontrado", -2));
        }

        try {
            track.setRouteXml(request.routeXml());
            PlanTrack savedTrack = planTrackRepository.save(track);

            return ResponseEntity.ok(new PlanTrackUpdateResponse(
                    savedTrack.getId(),
                    "Track actualizado correctamente",
                    0
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlanTrackUpdateResponse(track.getId(), "No se pudo actualizar el track", -99));
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
                    track.getDesnivel(),
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

    @GetMapping("/tracks/{trackId}/votes-summary")
    public ResponseEntity<PlanTrackVoteSummaryResponse> getPlanTrackVoteSummary(
            @Valid @PathVariable Long trackId,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        if (!planTrackRepository.existsById(trackId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        long totalVotes = planTrackVoteRepository.countByTrackId(trackId);
        boolean votedByUser = planTrackVoteRepository.existsByTrackIdAndUserId(trackId, user.getId());

        return ResponseEntity.ok(new PlanTrackVoteSummaryResponse(trackId, totalVotes, votedByUser));
    }

    @PostMapping("/votes")
    @Transactional
    public ResponseEntity<PlanTrackVoteCreateResponse> createPlanTrackVote(
            @Valid @RequestBody PlanTrackVoteCreateRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanTrackVoteCreateResponse(null, "No autenticado", -1));
        }

        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        if (user.getId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanTrackVoteCreateResponse(null, "Usuario no autenticado", -1));
        }

        if (!planFolderRepository.existsById(request.idFolder())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanTrackVoteCreateResponse(null, "Carpeta no encontrada", -2));
        }

        if (!planTrackRepository.existsByIdAndFolderId(request.idTrack(), request.idFolder())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanTrackVoteCreateResponse(null, "Track no encontrado", -3));
        }

        try {
            planTrackVoteRepository.deleteByFolderIdAndUserId(request.idFolder(), user.getId());

            PlanTrackVote vote = new PlanTrackVote();
            vote.setFolder(planFolderRepository.getReferenceById(request.idFolder()));
            vote.setUser(user);
            vote.setTrack(planTrackRepository.getReferenceById(request.idTrack()));

            log.info("[PLAN TRACK VOTE] Saving vote: folderId={}, trackId={}, userId={}",
                    request.idFolder(), request.idTrack(), user.getId());

            PlanTrackVote savedVote = planTrackVoteRepository.save(vote);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new PlanTrackVoteCreateResponse(
                            savedVote.getId(),
                            "Voto guardado correctamente",
                            0
                    ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlanTrackVoteCreateResponse(null, "No se pudo guardar el voto", -99));
        }
    }

    @DeleteMapping("/votes")
    public ResponseEntity<PlanTrackVoteDeleteResponse> deletePlanTrackVote(
            @Valid @RequestBody PlanTrackVoteDeleteRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanTrackVoteDeleteResponse(null, "No autenticado", -1));
        }

        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        if (user.getId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanTrackVoteDeleteResponse(null, "Usuario no autenticado", -1));
        }

        if (!planFolderRepository.existsById(request.idFolder())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanTrackVoteDeleteResponse(null, "Carpeta no encontrada", -2));
        }

        if (!planTrackRepository.existsByIdAndFolderId(request.idTrack(), request.idFolder())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanTrackVoteDeleteResponse(null, "Track no encontrado", -3));
        }

        PlanTrackVote vote = planTrackVoteRepository
                .findByFolderIdAndUserIdAndTrackId(request.idFolder(), user.getId(), request.idTrack())
                .orElse(null);
        if (vote == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PlanTrackVoteDeleteResponse(null, "Voto no encontrado", -4));
        }

        try {
            planTrackVoteRepository.delete(vote);

            return ResponseEntity.ok(new PlanTrackVoteDeleteResponse(
                    vote.getId(),
                    "Voto eliminado correctamente",
                    0
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlanTrackVoteDeleteResponse(null, "No se pudo eliminar el voto", -99));
        }
    }
}
