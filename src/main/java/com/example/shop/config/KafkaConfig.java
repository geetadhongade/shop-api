package com.example.shop.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

// Kafka topic definition + turns on @Scheduled jobs (used by the outbox publisher)
@Configuration
@EnableScheduling
public class KafkaConfig {

    // The topic for all order events. Created by OutboxPublisher before its first send.
    @Bean
    public NewTopic orderEventsTopic(@Value("${shop.kafka.topic.order-events}") String topicName,
                                     @Value("${shop.kafka.topic.order-events-partitions}") int partitions) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                // Only one Kafka server, so only one copy of each message
                .replicas(1)
                .build();
    }
}
