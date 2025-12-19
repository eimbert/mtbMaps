package com.paygoon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.paygoon.dto.TrackRouteSummaryResponse;
import com.paygoon.model.Track;

public interface TrackRepository extends JpaRepository<Track, Long> {

    @Query("select new com.paygoon.dto.TrackRouteSummaryResponse("
            + "t.id, t.nickname, t.category, t.bikeType, t.distanceKm, t.tiempoReal) "
            + "from Track t where t.route.id = :routeId")
    List<TrackRouteSummaryResponse> findRouteSummariesByRouteId(@Param("routeId") Long routeId);
}
