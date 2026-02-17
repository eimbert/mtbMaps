package com.paygoon.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.paygoon.model.AppUser;
import com.paygoon.model.PlanFolder;
import com.paygoon.repository.PlanFolderMemberRepository;
import com.paygoon.repository.PlanFolderRepository;
import com.paygoon.repository.PlanInvitationRepository;
import com.paygoon.repository.PlanTrackRepository;
import com.paygoon.repository.PlanTrackVoteRepository;
import com.paygoon.repository.UserRepository;
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
    private UserRepository userRepository;

    @Mock
    private PlanFolderService planFolderService;

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
}
