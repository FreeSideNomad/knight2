package com.knight.application.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka configuration for topic auto-creation.
 * Creates required topics on application startup if they don't exist.
 */
@Configuration
public class KafkaConfiguration {

    @Value("${kafka.platform-events-topic:platform-events}")
    private String platformEventsTopic;

    /**
     * Creates the platform-events topic for cross-application event messaging.
     * This topic is used for events like USER_PASSWORD_SET, USER_MFA_ENROLLED, USER_ONBOARDING_COMPLETE.
     *
     * @return NewTopic configuration that will be auto-created by Spring Kafka
     */
    @Bean
    public NewTopic platformEventsTopic() {
        return TopicBuilder.name(platformEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
