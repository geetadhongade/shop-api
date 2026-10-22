package com.example.shop.outbox;

import com.example.shop.entity.OutboxEvent;
import com.example.shop.entity.OutboxEventStatus;
import com.example.shop.logging.RequestIdFilter;
import com.example.shop.repository.OutboxEventRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Background job: sends NEW outbox events to Kafka and marks them PUBLISHED.
// If Kafka is down, events stay NEW and are tried again on the next round - nothing is lost.
// Kept outside the service package so the timing aspect doesn't log every (once a second) round.
@Component
@ConditionalOnProperty(name = "shop.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    // last_error column is VARCHAR(500)
    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final NewTopic orderEventsTopic;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final long sendTimeoutSeconds;

    // Set once the topic exists, so it is only created once
    private volatile boolean topicReady = false;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           KafkaAdmin kafkaAdmin,
                           NewTopic orderEventsTopic,
                           PlatformTransactionManager transactionManager,
                           @Value("${shop.outbox.batch-size}") int batchSize,
                           @Value("${shop.outbox.send-timeout-seconds}") long sendTimeoutSeconds) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaAdmin = kafkaAdmin;
        this.orderEventsTopic = orderEventsTopic;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    // fixedDelay: the next round starts this long AFTER the previous one finished, so rounds never overlap
    @Scheduled(fixedDelayString = "${shop.outbox.poll-interval-ms}")
    public void publishPendingEvents() {
        // One transaction per round: the rows stay locked while we send them,
        // and their new status is saved when the round ends
        transactionTemplate.executeWithoutResult(status -> publishBatch());
    }

    private void publishBatch() {
        // These rows are "managed" by Hibernate: changes made to them below (status, attempts, ...)
        // are saved automatically with an UPDATE when the transaction commits - no save() call needed
        List<OutboxEvent> events = outboxEventRepository.findNextToPublish(batchSize);
        if (events.isEmpty()) {
            return;
        }

        for (OutboxEvent event : events) {
            // Log lines for this event carry the request ID of the API call that created it
            if (event.getRequestId() != null) {
                MDC.put(RequestIdFilter.MDC_KEY, event.getRequestId());
            }
            try {
                ensureTopicExists();
                RecordMetadata stored = send(event);
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setPublishedAt(OffsetDateTime.now());
                event.setLastError(null);
                log.info("Published {} event {} for order {} (partition {}, offset {})",
                        event.getEventType(), event.getEventId(), event.getOrderId(), stored.partition(), stored.offset());
            } catch (Exception ex) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(shorten(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                log.warn("Could not publish event {} for order {} (attempt {}), will retry: {}",
                        event.getEventId(), event.getOrderId(), event.getAttempts(), event.getLastError());
                // Stop this round: later events must not overtake this one. It is retried next round.
                return;
            } finally {
                MDC.remove(RequestIdFilter.MDC_KEY);
            }
        }
    }

    // Sends one event, waits until Kafka confirms it is stored, and returns where it was stored (partition + offset)
    private RecordMetadata send(OutboxEvent event) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                orderEventsTopic.name(),
                // Key = order ID: all events of one order go to the same partition, so they stay in order
                String.valueOf(event.getOrderId()),
                event.getPayload());
        // Headers let the consumer check for duplicates / trace the request without reading the body
        record.headers().add("eventId", event.getEventId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", event.getEventType().name().getBytes(StandardCharsets.UTF_8));
        if (event.getRequestId() != null) {
            record.headers().add("reqId", event.getRequestId().getBytes(StandardCharsets.UTF_8));
        }

        SendResult<String, String> result = kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
        return result.getRecordMetadata();
    }

    // Creates the topic if it doesn't exist yet (does nothing if it does)
    private void ensureTopicExists() {
        if (!topicReady) {
            kafkaAdmin.createOrModifyTopics(orderEventsTopic);
            topicReady = true;
        }
    }

    private static String shorten(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
