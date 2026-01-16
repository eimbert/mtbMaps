package com.paygoon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paygoon.model.PlanFolder;
import com.paygoon.model.AppUser;

import java.util.List;

@Repository
public interface PlanFolderRepository extends JpaRepository<PlanFolder, Long> {
    List<PlanFolder> findByOwner(AppUser owner);
}
