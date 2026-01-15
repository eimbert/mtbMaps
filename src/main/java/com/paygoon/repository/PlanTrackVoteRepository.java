package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paygoon.model.PlanTrackVote;

@Repository
public interface PlanTrackVoteRepository extends JpaRepository<PlanTrackVote, Long> {
}
