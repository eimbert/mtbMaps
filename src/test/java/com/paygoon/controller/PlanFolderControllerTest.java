package com.paygoon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.paygoon.dto.PlanFolderMemberCreateRequest;
import com.paygoon.dto.PlanFolderMemberCreateResponse;
import com.paygoon.model.AppUser;
import com.paygoon.model.Mensaje;
import com.paygoon.model.PlanFolder;
import com.paygoon.model.PlanFolderMember;
import com.paygoon.repository.MensajeRepository;
import com.paygoon.repository.PlanFolderMemberRepository;
import com.paygoon.repository.PlanFolderRepository;
import com.paygoon.repository.PlanInvitationRepository;
import com.paygoon.repository.PlanTrackRepository;
import com.paygoon.repository.PlanTrackVoteRepository;
import com.paygoon.repository.UserRepository;
import com.paygoon.service.NotificationService;
import com.paygoon.service.PlanFolderService;

@ExtendWith(MockitoExtension.class)
class PlanFolderControllerTest {

    @Mock
    private PlanFolderRepository planFolderRepository;

    @Mock
    private PlanFolderMemberRepository planFolderMemberRepository;

    @Mock
    private PlanTrackRepository planTrackRepository;

    @Mock
    private PlanTrackVoteRepository planTrackVoteRepository;

    @Mock
    private PlanInvitationRepository planInvitationRepository;

    @Mock
    private MensajeRepository mensajeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlanFolderService planFolderService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private PlanFolderController controller;

    @Test
    void deletePlanFolderRemovesTracksAndInvitations() {
        AppUser requester = new AppUser();
        requester.setId(25L);
        requester.setEmail("owner@example.com");

        PlanFolder folder = new PlanFolder();
        folder.setId(42L);
        folder.setOwner(requester);

        when(authentication.getName()).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(requester));
        when(planFolderRepository.findById(42L)).thenReturn(Optional.of(folder));

        ResponseEntity<Void> response = controller.deletePlanFolder(42L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(planTrackVoteRepository).deleteByFolderId(42L);
        verify(planTrackRepository).deleteByFolderId(42L);
        verify(planFolderMemberRepository).deleteByFolderId(42L);
        verify(planInvitationRepository).deleteByFolderId(42L);
        verify(planFolderRepository).delete(folder);
    }

    @Test
    void addPlanFolderMemberCreatesInvitationMessage() {
        AppUser requester = new AppUser();
        requester.setId(7L);
        requester.setEmail("owner@example.com");

        AppUser invitedUser = new AppUser();
        invitedUser.setId(17L);
        invitedUser.setEmail("tramites1024@gmail.com");
        invitedUser.setNickname("prueba");

        PlanFolder folder = new PlanFolder();
        folder.setId(13L);
        folder.setOwner(requester);

        PlanFolderMember savedMember = new PlanFolderMember();
        savedMember.setId(99L);

        when(authentication.getName()).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(requester));
        when(planFolderRepository.findById(13L)).thenReturn(Optional.of(folder));
        when(userRepository.findById(17L)).thenReturn(Optional.of(invitedUser));
        when(planFolderMemberRepository.existsByFolderIdAndUserId(13L, 17L)).thenReturn(false);
        when(planFolderMemberRepository.save(any(PlanFolderMember.class))).thenReturn(savedMember);
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanFolderMemberCreateRequest request = new PlanFolderMemberCreateRequest(
                13L,
                17L,
                "tramites1024@gmail.com",
                "prueba"
        );

        ResponseEntity<PlanFolderMemberCreateResponse> response = controller.addPlanFolderMember(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(0);
        assertThat(response.getBody().id()).isEqualTo(99L);
        verify(mensajeRepository).save(any(Mensaje.class));
        verify(notificationService).sendPlanFolderInvitationEmail(invitedUser, requester, folder);
    }
}
