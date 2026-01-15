package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paygoon.model.PlanInvitation;

@Repository
public interface PlanInvitationRepository extends JpaRepository<PlanInvitation, Long> {
}
