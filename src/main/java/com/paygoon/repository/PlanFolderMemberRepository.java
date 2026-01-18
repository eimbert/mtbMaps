package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import com.paygoon.model.AppUser;
import com.paygoon.model.PlanFolderMember;

@Repository
public interface PlanFolderMemberRepository extends JpaRepository<PlanFolderMember, Long> {
    List<PlanFolderMember> findByUser(AppUser user);

    List<PlanFolderMember> findByNickname(String nickname);

    List<PlanFolderMember> findByFolderId(Long folderId);

    boolean existsByFolderIdAndUserId(Long folderId, Long userId);
}
