package com.example.shop.outbox;

import com.example.shop.entity.OutboxEvent;
import com.example.shop.entity.OutboxEventStatus;
import com.example.shop.entity.OutboxEventType;
import com.example.shop.repository.OutboxEventRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit test: Kafka, the database and the transaction manager are all fakes (mocks)
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final int BATCH_SIZE = 50;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private KafkaAdmin kafkaAdmin;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final NewTopic orderEventsTopic = new NewTopic("order-events", 3, (short) 1);

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate, kafkaAdmin,
                orderEventsTopic, transactionManager, BATCH_SIZE, 10);
    }

    @Test
    void publish_sendsToTopicWithOrderIdKeyAndHeaders() {
        OutboxEvent event = buildEvent(1L, 23L, "step5-check");
        when(outboxEventRepository.findNextToPublish(BATCH_SIZE)).thenReturn(List.of(event));
        kafkaAccepts();

        publisher.publishPendingEvents();

        ProducerRecord<String, String> sent = captureSent().get(0);
        assertThat(sent.topic()).isEqualTo("order-events");
        assertThat(sent.key()).isEqualTo("23");
        assertThat(sent.value()).isEqualTo(event.getPayload());
        assertThat(header(sent, "eventId")).isEqualTo(event.getEventId().toString());
        assertThat(header(sent, "eventType")).isEqualTo("ORDER_PLACED");
        assertThat(header(sent, "reqId")).isEqualTo("step5-check");
    }

    @Test
    void publish_withoutRequestId_sendsNoReqIdHeader() {
        when(outboxEventRepository.findNextToPublish(BATCH_SIZE)).thenReturn(List.of(buildEvent(1L, 23L, null)));
        kafkaAccepts();

        publisher.publishPendingEvents();

        assertThat(captureSent().get(0).headers().lastHeader("reqId")).isNull();
    }

    @Test
    void publish_whenKafkaConfirms_marksEventPublished() {
        OutboxEvent event = buildEvent(1L, 23L, "step5-check");
        event.setLastError("old error from an earlier attempt");
        when(outboxEventRepository.findNextToPublish(BATCH_SIZE)).thenReturn(List.of(event));
        kafkaAccepts();

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void publish_whenKafkaFails_keepsEventNewAndCountsAttempt() {
        OutboxEvent event = buildEvent(1L, 23L, "step5-check");
        when(outboxEventRepository.findNextToPublish(BATCH_SIZE)).thenReturn(List.of(event));
        kafkaFails("Kafka down");

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("Kafka down");
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void publish_afterAFailure_laterEventsAreNotSentThisRound() {
        OutboxEvent first = buildEvent(1L, 23L, null);
        OutboxEvent second = buildEvent(2L, 24L, null);
        when(outboxEventRepository.findNextToPublish(BATCH_SIZE)).thenReturn(List.of(first, second));
        kafkaFails("Kafka down");

        publisher.publishPendingEvents();

        // Only the first one was tried; the second must not overtake it
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(second.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(second.getAttempts()).isZero();
    }

    @Test
    void publish_sendsSeveralEventsOldestFirst() {
        OutboxEvent first = buildEvent(1L, 23L, null);
        OutboxEvent second = buildEvent(2L, 24L, null);
        when(outboxEventRepository.findNextToPublish(BATCH_SIZE)).thenReturn(List.of(first, second));
        kafkaAccepts();

        publisher.publishPendingEvents();

        assertThat(captureSent()).extracting(ProducerRecord::key).containsExactly("23", "24");
        assertThat(first.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(second.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void publish_whenNothingIsWaiting_sendsNothing() {
        when(outboxEventRepository.findNextToPublish(BATCH_SIZE)).thenReturn(List.of());

        publisher.publishPendingEvents();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(kafkaAdmin, never()).createOrModifyTopics(any(NewTopic[].class));
    }

    @Test
    void publish_createsTopicOnlyOnce() {
        when(outboxEventRepository.findNextToPublish(BATCH_SIZE))
                .thenReturn(List.of(buildEvent(1L, 23L, null)))
                .thenReturn(List.of(buildEvent(2L, 24L, null)));
        kafkaAccepts();

        publisher.publishPendingEvents();
        publisher.publishPendingEvents();

        verify(kafkaAdmin, times(1)).createOrModifyTopics(orderEventsTopic);
    }

    @Test
    void publish_whenTopicCannotBeCreated_retriesTopicNextRound() {
        OutboxEvent event = buildEvent(1L, 23L, null);
        when(outboxEventRepository.findNextToPublish(BATCH_SIZE)).thenReturn(List.of(event));
        doThrow(new KafkaException("Failed to create topics"))
                .doNothing()
                .when(kafkaAdmin).createOrModifyTopics(orderEventsTopic);
        kafkaAccepts();

        // Round 1: Kafka down, topic not created, nothing sent
        publisher.publishPendingEvents();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(event.getLastError()).contains("Failed to create topics");
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));

        // Round 2: Kafka is back, topic created, event sent
        publisher.publishPendingEvents();
        verify(kafkaAdmin, times(2)).createOrModifyTopics(orderEventsTopic);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void publish_cutsVeryLongErrorsTo500Characters() {
        OutboxEvent event = buildEvent(1L, 23L, null);
        when(outboxEventRepository.findNextToPublish(BATCH_SIZE)).thenReturn(List.of(event));
        kafkaFails("x".repeat(2000));

        publisher.publishPendingEvents();

        assertThat(event.getLastError()).hasSize(500);
    }

    // ---------- helpers ----------

    private void kafkaAccepts() {
        // Pretend Kafka stored the message in partition 1 at offset 7
        RecordMetadata stored = new RecordMetadata(new TopicPartition("order-events", 1), 7, 0, 0, 0, 0);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<String, String>(null, stored)));
    }

    private void kafkaFails(String message) {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException(message)));
    }

    @SuppressWarnings("unchecked")
    private List<ProducerRecord<String, String>> captureSent() {
        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
        return sent.getAllValues();
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private OutboxEvent buildEvent(Long id, Long orderId, String requestId) {
        OutboxEvent event = new OutboxEvent();
        ReflectionTestUtils.setField(event, "id", id);
        event.setEventId(UUID.randomUUID());
        event.setEventType(OutboxEventType.ORDER_PLACED);
        event.setOrderId(orderId);
        event.setPayload("{\"orderId\": " + orderId + "}");
        event.setRequestId(requestId);
        return event;
    }
}
