package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import com.paygoon.model.AppUser;
import com.paygoon.model.PlanFolderMember;

@Repository
public interface PlanFolderMemberRepository extends JpaRepository<PlanFolderMember, Long> {
    List<PlanFolderMember> findByUser(AppUser user);

    List<PlanFolderMember> findByUserAndStatus(AppUser user, PlanFolderMember.Status status);

    List<PlanFolderMember> findByNickname(String nickname);

    List<PlanFolderMember> findByFolderId(Long folderId);

    boolean existsByFolderIdAndUserId(Long folderId, Long userId);

    Optional<PlanFolderMember> findByFolderIdAndUserId(Long folderId, Long userId);

    @Modifying
    @Transactional
    @Query("delete from PlanFolderMember member where member.folder.id = :folderId")
    void deleteByFolderId(@Param("folderId") Long folderId);
}
