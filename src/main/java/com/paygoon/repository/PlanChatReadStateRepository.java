package com.paygoon.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paygoon.model.PlanChatReadState;

public interface PlanChatReadStateRepository extends JpaRepository<PlanChatReadState, Long> {
    Optional<PlanChatReadState> findByFolderIdAndUserId(Long folderId, Long userId);
}
