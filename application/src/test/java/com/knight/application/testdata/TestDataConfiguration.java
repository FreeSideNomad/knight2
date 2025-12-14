package com.knight.application.testdata;

import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.domain.approvalworkflows.repository.ApprovalWorkflowRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

/**
 * Test configuration that provides mock beans for repositories
 * not needed by the test data generator.
 */
@TestConfiguration
public class TestDataConfiguration {

    @Bean
    public ServicingProfileRepository servicingProfileRepository() {
        return mock(ServicingProfileRepository.class);
    }

    @Bean
    public IndirectClientRepository indirectClientRepository() {
        return mock(IndirectClientRepository.class);
    }

    @Bean
    public ApprovalWorkflowRepository approvalWorkflowRepository() {
        return mock(ApprovalWorkflowRepository.class);
    }
}
