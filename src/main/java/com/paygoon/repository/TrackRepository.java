package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paygoon.model.Track;

public interface TrackRepository extends JpaRepository<Track, Long> {
}
