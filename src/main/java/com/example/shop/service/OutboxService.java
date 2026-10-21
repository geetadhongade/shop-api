package com.example.shop.service;

import com.example.shop.entity.Order;
import com.example.shop.entity.OrderItem;
import com.example.shop.entity.OutboxEvent;
import com.example.shop.entity.OutboxEventType;
import com.example.shop.event.OrderPlacedEvent;
import com.example.shop.logging.RequestIdFilter;
import com.example.shop.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

// Saves events into the outbox_event table. A background publisher sends them to Kafka later.
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    // Version of the OrderPlacedEvent message layout
    private static final int ORDER_PLACED_SCHEMA_VERSION = 1;

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final String baseCurrency;

    public OutboxService(OutboxEventRepository outboxEventRepository,
                         JsonMapper jsonMapper,
                         @Value("${shop.currency.base}") String baseCurrency) {
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
        this.baseCurrency = baseCurrency;
    }

    // Saves an ORDER_PLACED event for a just-saved order.
    // MANDATORY = refuses to run outside a transaction, so the event is always saved
    // together with the order: both are saved, or neither is.
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveOrderPlacedEvent(Order order) {
        // Created ONCE here and reused on every re-send, so the consumer can skip duplicates
        UUID eventId = UUID.randomUUID();

        int itemCount = order.getItems().stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();

        OrderPlacedEvent message = new OrderPlacedEvent(
                eventId,
                OutboxEventType.ORDER_PLACED.name(),
                ORDER_PLACED_SCHEMA_VERSION,
                order.getCreatedAt().toInstant(),
                order.getId(),
                order.getCustomer().getId(),
                order.getCustomer().getFirstName(),
                order.getCustomer().getPhone(),
                order.getTotalAmount(),
                baseCurrency,
                itemCount
        );

        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setEventType(OutboxEventType.ORDER_PLACED);
        event.setOrderId(order.getId());
        event.setPayload(jsonMapper.writeValueAsString(message));
        // The current request's ID, so the SMS logs can be traced back to this API call
        event.setRequestId(MDC.get(RequestIdFilter.MDC_KEY));
        outboxEventRepository.save(event);

        log.info("Saved {} event {} for order {}", OutboxEventType.ORDER_PLACED, eventId, order.getId());
    }
}
