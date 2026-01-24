package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paygoon.model.PlanTrackVote;

import java.util.List;

@Repository
public interface PlanTrackVoteRepository extends JpaRepository<PlanTrackVote, Long> {
    List<PlanTrackVote> findByFolderId(Long folderId);

    void deleteByTrackId(Long trackId);

    void deleteByFolderId(Long folderId);
}
