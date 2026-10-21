package com.example.shop.service;

import com.example.shop.entity.Customer;
import com.example.shop.entity.Order;
import com.example.shop.entity.OrderItem;
import com.example.shop.entity.OutboxEvent;
import com.example.shop.entity.OutboxEventStatus;
import com.example.shop.entity.OutboxEventType;
import com.example.shop.logging.RequestIdFilter;
import com.example.shop.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

// Unit test: the repository is a fake (mock); a real JsonMapper builds the payload so we can check the JSON
@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(outboxEventRepository, jsonMapper, "USD");
    }

    @AfterEach
    void clearRequestId() {
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    @Test
    void saveOrderPlacedEvent_savesNewOrderPlacedRow() {
        outboxService.saveOrderPlacedEvent(buildOrder());

        OutboxEvent saved = captureSavedEvent();
        assertThat(saved.getEventType()).isEqualTo(OutboxEventType.ORDER_PLACED);
        assertThat(saved.getOrderId()).isEqualTo(23L);
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getEventId()).isNotNull();
        assertThat(saved.getPublishedAt()).isNull();
    }

    @Test
    void saveOrderPlacedEvent_payloadHasTheAgreedFields() {
        outboxService.saveOrderPlacedEvent(buildOrder());

        OutboxEvent saved = captureSavedEvent();
        JsonNode payload = jsonMapper.readTree(saved.getPayload());
        assertThat(payload.get("eventType").asString()).isEqualTo("ORDER_PLACED");
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(payload.get("occurredAt").asString()).isEqualTo("2026-10-21T14:46:55Z");
        assertThat(payload.get("orderId").asLong()).isEqualTo(23L);
        assertThat(payload.get("customerId").asLong()).isEqualTo(6L);
        assertThat(payload.get("customerFirstName").asString()).isEqualTo("Bob");
        assertThat(payload.get("customerPhone").asString()).isEqualTo("+1 555 0100");
        assertThat(payload.get("totalAmount").decimalValue()).isEqualByComparingTo("4188.00");
        assertThat(payload.get("currency").asString()).isEqualTo("USD");
    }

    @Test
    void saveOrderPlacedEvent_itemCountIsTotalQuantity() {
        // 2 laptops + 3 lipsticks = 5 (not 2 different products)
        outboxService.saveOrderPlacedEvent(buildOrder());

        JsonNode payload = jsonMapper.readTree(captureSavedEvent().getPayload());
        assertThat(payload.get("itemCount").asInt()).isEqualTo(5);
    }

    @Test
    void saveOrderPlacedEvent_eventIdInRowMatchesEventIdInPayload() {
        outboxService.saveOrderPlacedEvent(buildOrder());

        OutboxEvent saved = captureSavedEvent();
        JsonNode payload = jsonMapper.readTree(saved.getPayload());
        assertThat(payload.get("eventId").asString()).isEqualTo(saved.getEventId().toString());
    }

    @Test
    void saveOrderPlacedEvent_eachEventGetsItsOwnEventId() {
        outboxService.saveOrderPlacedEvent(buildOrder());
        outboxService.saveOrderPlacedEvent(buildOrder());

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getEventId())
                .isNotEqualTo(saved.getAllValues().get(1).getEventId());
    }

    @Test
    void saveOrderPlacedEvent_storesCurrentRequestId() {
        MDC.put(RequestIdFilter.MDC_KEY, "step5-check");

        outboxService.saveOrderPlacedEvent(buildOrder());

        assertThat(captureSavedEvent().getRequestId()).isEqualTo("step5-check");
    }

    @Test
    void saveOrderPlacedEvent_withoutRequest_storesNoRequestId() {
        outboxService.saveOrderPlacedEvent(buildOrder());

        assertThat(captureSavedEvent().getRequestId()).isNull();
    }

    @Test
    void saveOrderPlacedEvent_payloadLeavesOutPersonalDataAndItems() {
        outboxService.saveOrderPlacedEvent(buildOrder());

        JsonNode payload = jsonMapper.readTree(captureSavedEvent().getPayload());
        assertThat(payload.has("email")).isFalse();
        assertThat(payload.has("customerEmail")).isFalse();
        assertThat(payload.has("lastName")).isFalse();
        assertThat(payload.has("street")).isFalse();
        assertThat(payload.has("city")).isFalse();
        assertThat(payload.has("items")).isFalse();
        // Exactly the 11 agreed fields
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder(
                "eventId", "eventType", "schemaVersion", "occurredAt", "orderId", "customerId",
                "customerFirstName", "customerPhone", "totalAmount", "currency", "itemCount");
    }

    // ---------- helpers ----------

    private OutboxEvent captureSavedEvent() {
        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(saved.capture());
        return saved.getValue();
    }

    // Order 23 for Bob: 2 x Laptop (2019.00) + 3 x Lipstick (50.00) = 4188.00, 5 items in total
    private Order buildOrder() {
        Customer bob = new Customer();
        ReflectionTestUtils.setField(bob, "id", 6L);
        bob.setFirstName("Bob");
        bob.setLastName("Builder");
        bob.setEmail("bob@example.com");
        bob.setPhone("+1 555 0100");
        bob.setCity("Austin");

        Order order = new Order();
        order.setCustomer(bob);
        order.setTotalAmount(new BigDecimal("4188.00"));
        order.addItem(buildItem(2, "2019.00"));
        order.addItem(buildItem(3, "50.00"));
        ReflectionTestUtils.setField(order, "id", 23L);
        ReflectionTestUtils.setField(order, "createdAt", OffsetDateTime.parse("2026-10-21T09:46:55-05:00"));
        return order;
    }

    private OrderItem buildItem(int quantity, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }
}
