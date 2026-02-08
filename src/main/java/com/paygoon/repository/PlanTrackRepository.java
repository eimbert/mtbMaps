package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.paygoon.model.PlanTrack;

import java.util.List;

@Repository
public interface PlanTrackRepository extends JpaRepository<PlanTrack, Long> {
    List<PlanTrack> findByFolderId(Long folderId);

    @Modifying
    @Transactional
    @Query("delete from PlanTrack track where track.folder.id = :folderId")
    void deleteByFolderId(@Param("folderId") Long folderId);

    boolean existsByIdAndFolderId(Long id, Long folderId);
}
