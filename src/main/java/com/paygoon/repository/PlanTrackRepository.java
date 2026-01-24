package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paygoon.model.PlanTrack;

import java.util.List;

@Repository
public interface PlanTrackRepository extends JpaRepository<PlanTrack, Long> {
    List<PlanTrack> findByFolderId(Long folderId);

    void deleteByFolderId(Long folderId);
}
