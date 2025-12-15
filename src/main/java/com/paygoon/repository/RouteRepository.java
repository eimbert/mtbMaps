package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paygoon.model.Route;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {
}
