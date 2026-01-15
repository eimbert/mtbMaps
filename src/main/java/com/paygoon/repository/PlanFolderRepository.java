package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paygoon.model.PlanFolder;

@Repository
public interface PlanFolderRepository extends JpaRepository<PlanFolder, Long> {
}
