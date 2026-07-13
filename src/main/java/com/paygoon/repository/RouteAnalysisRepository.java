package com.paygoon.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paygoon.model.RouteAnalysis;

@Repository
public interface RouteAnalysisRepository extends JpaRepository<RouteAnalysis, Long> {
    List<RouteAnalysis> findTop100ByOrderByCreatedAtDesc();
    List<RouteAnalysis> findAllByOrderByUpdatedAtDesc();
    Optional<RouteAnalysis> findFirstBySourceAndSourceTrackIdAndUserIdOrderByUpdatedAtDesc(String source, Long sourceTrackId, Long userId);

    Optional<RouteAnalysis> findFirstByGpxHashAndUserIdOrderByUpdatedAtDesc(String gpxHash, Long userId);

    Optional<RouteAnalysis> findFirstByGpxHashOrderByUpdatedAtDesc(String gpxHash);

    List<RouteAnalysis> findAllByGpxHashOrderByUpdatedAtDesc(String gpxHash);

    Optional<RouteAnalysis> findByIdAndUserId(Long id, Long userId);
}
