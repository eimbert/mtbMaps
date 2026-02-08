package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.paygoon.model.PlanTrackVote;

import java.util.List;

@Repository
public interface PlanTrackVoteRepository extends JpaRepository<PlanTrackVote, Long> {
    List<PlanTrackVote> findByFolderId(Long folderId);

    long countByTrackId(Long trackId);

    boolean existsByTrackIdAndUserId(Long trackId, Long userId);

    java.util.Optional<PlanTrackVote> findByFolderIdAndUserIdAndTrackId(Long folderId, Long userId, Long trackId);

    void deleteByTrackId(Long trackId);

    @Modifying
    @Transactional
    @Query("delete from PlanTrackVote vote where vote.folder.id = :folderId")
    void deleteByFolderId(@Param("folderId") Long folderId);

    @Modifying
    @Transactional
    @Query("delete from PlanTrackVote vote where vote.folder.id = :folderId and vote.user.id = :userId")
    void deleteByFolderIdAndUserId(@Param("folderId") Long folderId, @Param("userId") Long userId);
}
