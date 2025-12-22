package com.knight.application.persistence.approvalworkflows.repository;

import com.knight.application.persistence.approvalworkflows.entity.ApprovalWorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApprovalWorkflowJpaRepository extends JpaRepository<ApprovalWorkflowEntity, UUID> {
}
