package com.example.shop.service;

import com.example.shop.dto.PaymentCreateRequest;
import com.example.shop.dto.PaymentResponse;
import com.example.shop.entity.Order;
import com.example.shop.entity.OrderStatus;
import com.example.shop.entity.Payment;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.InvalidRequestException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    // Saves the payment AND marks the order as PAID in one transaction:
    // if either step fails, nothing is changed.
    @Transactional(timeoutString = "${shop.timeout.write-seconds}")
    public PaymentResponse createPayment(PaymentCreateRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order " + request.orderId() + " not found"));

        // Rule: one payment per order
        if (paymentRepository.existsByOrderId(order.getId())) {
            throw new BusinessRuleException("Order " + order.getId() + " already has a payment");
        }

        // Rule: only PENDING orders can be paid (not CANCELLED, SHIPPED, ...)
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException("Order " + order.getId() + " is " + order.getStatus()
                    + "; only PENDING orders can be paid");
        }

        // Rule: the amount must exactly match the order total.
        // compareTo (not equals) so that e.g. 4067.9 and 4067.90 count as the same amount.
        if (request.amount().compareTo(order.getTotalAmount()) != 0) {
            throw new InvalidRequestException("Amount must be " + order.getTotalAmount()
                    + " (the order total), but was " + request.amount());
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(request.amount());
        payment.setMethod(request.method());
        payment = paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAID);
        // saveAndFlush writes the status change now. If someone changed (e.g. cancelled) this order
        // at the same moment, the version check fails here and the whole payment is rolled back (409).
        orderRepository.saveAndFlush(order);

        log.info("Payment {} created for order {} ({} {})", payment.getId(), order.getId(), payment.getAmount(), payment.getMethod());
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true, timeoutString = "${shop.timeout.read-seconds}")
    public PaymentResponse getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " not found"));
        return PaymentResponse.from(payment);
    }

    // The payment of one order; 404 if the order doesn't exist or isn't paid yet
    @Transactional(readOnly = true, timeoutString = "${shop.timeout.read-seconds}")
    public PaymentResponse getPaymentForOrder(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order " + orderId + " not found");
        }
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " has no payment yet"));
        return PaymentResponse.from(payment);
    }
}
