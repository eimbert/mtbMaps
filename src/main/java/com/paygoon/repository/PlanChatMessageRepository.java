package com.paygoon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paygoon.model.PlanChatMessage;

public interface PlanChatMessageRepository extends JpaRepository<PlanChatMessage, Long> {
    List<PlanChatMessage> findTop100ByFolderIdOrderByIdDesc(Long folderId);
    List<PlanChatMessage> findTop100ByFolderIdAndIdGreaterThanOrderByIdAsc(Long folderId, Long id);
    long countByFolderIdAndIdGreaterThanAndAuthorIdNot(Long folderId, Long id, Long authorId);
    long countByFolderIdAndIdGreaterThan(Long folderId, Long id);
}
