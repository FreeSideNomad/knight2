package com.knight.application.persistence.policies.repository;

import com.knight.application.persistence.policies.entity.PolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PolicyJpaRepository extends JpaRepository<PolicyEntity, UUID> {
}
