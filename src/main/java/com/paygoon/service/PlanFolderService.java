package com.paygoon.service;

import com.paygoon.model.PlanFolder;
import com.paygoon.repository.PlanFolderMemberRepository;
import com.paygoon.repository.PlanFolderRepository;
import com.paygoon.repository.PlanInvitationRepository;
import com.paygoon.repository.PlanTrackRepository;
import com.paygoon.repository.PlanTrackVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanFolderService {

    private final PlanFolderRepository planFolderRepository;
    private final PlanFolderMemberRepository planFolderMemberRepository;
    private final PlanTrackRepository planTrackRepository;
    private final PlanTrackVoteRepository planTrackVoteRepository;
    private final PlanInvitationRepository planInvitationRepository;

    @Transactional
    public void deletePlanFolder(PlanFolder folder) {
        Long folderId = folder.getId();
        if (folderId == null) {
            return;
        }

        planTrackVoteRepository.deleteByFolderId(folderId);
        planTrackRepository.deleteByFolderId(folderId);
        planFolderMemberRepository.deleteByFolderId(folderId);
        planInvitationRepository.deleteByFolderId(folderId);
        planFolderRepository.delete(folder);
    }
}
