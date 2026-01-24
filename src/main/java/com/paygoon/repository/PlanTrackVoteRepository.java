package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paygoon.model.PlanTrackVote;

import java.util.List;

@Repository
public interface PlanTrackVoteRepository extends JpaRepository<PlanTrackVote, Long> {
    List<PlanTrackVote> findByFolderId(Long folderId);

    long countByTrackId(Long trackId);

    boolean existsByTrackIdAndUserId(Long trackId, Long userId);

    void deleteByTrackId(Long trackId);

    void deleteByFolderId(Long folderId);

    void deleteByFolderIdAndUserId(Long folderId, Long userId);
}
