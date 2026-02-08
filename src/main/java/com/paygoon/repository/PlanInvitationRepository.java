package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.paygoon.model.PlanInvitation;

@Repository
public interface PlanInvitationRepository extends JpaRepository<PlanInvitation, Long> {
    @Modifying
    @Transactional
    @Query("delete from PlanInvitation invitation where invitation.folder.id = :folderId")
    void deleteByFolderId(@Param("folderId") Long folderId);
}
