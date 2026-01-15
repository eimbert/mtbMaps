package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paygoon.model.PlanFolderMember;

@Repository
public interface PlanFolderMemberRepository extends JpaRepository<PlanFolderMember, Long> {
}
