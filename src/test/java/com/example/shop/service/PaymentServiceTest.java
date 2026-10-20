package com.example.shop.service;

import com.example.shop.dto.PaymentCreateRequest;
import com.example.shop.dto.PaymentResponse;
import com.example.shop.entity.Order;
import com.example.shop.entity.OrderStatus;
import com.example.shop.entity.Payment;
import com.example.shop.entity.PaymentMethod;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.InvalidRequestException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit test: the repositories are fakes (mocks), so no database is used
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentService paymentService;

    // ---------- create ----------

    @Test
    void createPayment_savesPaymentAndMarksOrderPaid() {
        Order order = buildOrder(7L, OrderStatus.PENDING, "100.00");
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(7L)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> {
            Payment saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        // 100.0 and 100.00 are the same amount
        PaymentCreateRequest request = new PaymentCreateRequest(7L, new BigDecimal("100.0"), PaymentMethod.UPI);

        PaymentResponse result = paymentService.createPayment(request);

        ArgumentCaptor<Payment> savedPayment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(savedPayment.capture());
        assertThat(savedPayment.getValue().getOrder()).isSameAs(order);
        assertThat(savedPayment.getValue().getMethod()).isEqualTo(PaymentMethod.UPI);

        // The order is now PAID, and written right away so the version check runs
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).saveAndFlush(order);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.orderId()).isEqualTo(7L);
        assertThat(result.amount()).isEqualByComparingTo("100.00");
        assertThat(result.method()).isEqualTo(PaymentMethod.UPI);
    }

    @Test
    void createPayment_whenOrderMissing_throwsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        PaymentCreateRequest request = new PaymentCreateRequest(99L, new BigDecimal("1.00"), PaymentMethod.CASH);

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order 99 not found");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_whenOrderAlreadyHasPayment_throwsAndSavesNothing() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(buildOrder(7L, OrderStatus.PAID, "100.00")));
        when(paymentRepository.existsByOrderId(7L)).thenReturn(true);
        PaymentCreateRequest request = new PaymentCreateRequest(7L, new BigDecimal("100.00"), PaymentMethod.CARD);

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Order 7 already has a payment");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_whenOrderNotPending_throwsAndSavesNothing() {
        when(orderRepository.findById(8L)).thenReturn(Optional.of(buildOrder(8L, OrderStatus.CANCELLED, "50.00")));
        when(paymentRepository.existsByOrderId(8L)).thenReturn(false);
        PaymentCreateRequest request = new PaymentCreateRequest(8L, new BigDecimal("50.00"), PaymentMethod.UPI);

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Order 8 is CANCELLED; only PENDING orders can be paid");
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPayment_whenAmountWrong_throwsAndSavesNothing() {
        Order order = buildOrder(7L, OrderStatus.PENDING, "4067.97");
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(7L)).thenReturn(false);
        PaymentCreateRequest request = new PaymentCreateRequest(7L, new BigDecimal("100.00"), PaymentMethod.CARD);

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Amount must be 4067.97 (the order total), but was 100.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(paymentRepository, never()).save(any());
    }

    // ---------- get ----------

    @Test
    void getPayment_whenFound_returnsPayment() {
        Payment payment = buildPayment(1L, buildOrder(7L, OrderStatus.PAID, "100.00"));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentResponse result = paymentService.getPayment(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.orderId()).isEqualTo(7L);
        assertThat(result.createdAt()).isEqualTo(payment.getCreatedAt());
        assertThat(result.updatedAt()).isEqualTo(payment.getUpdatedAt());
    }

    @Test
    void getPayment_whenMissing_throwsNotFound() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Payment 99 not found");
    }

    @Test
    void getPaymentForOrder_whenPaid_returnsPayment() {
        when(orderRepository.existsById(7L)).thenReturn(true);
        when(paymentRepository.findByOrderId(7L))
                .thenReturn(Optional.of(buildPayment(1L, buildOrder(7L, OrderStatus.PAID, "100.00"))));

        PaymentResponse result = paymentService.getPaymentForOrder(7L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.orderId()).isEqualTo(7L);
    }

    @Test
    void getPaymentForOrder_whenNotPaidYet_throwsNotFound() {
        when(orderRepository.existsById(7L)).thenReturn(true);
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentForOrder(7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order 7 has no payment yet");
    }

    @Test
    void getPaymentForOrder_whenOrderMissing_throwsNotFound() {
        when(orderRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> paymentService.getPaymentForOrder(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order 99 not found");
        verify(paymentRepository, never()).findByOrderId(any());
    }

    // ---------- helpers to build fake data ----------
    // The database normally sets ids and dates, and the entities have no setters for them,
    // so the tests set them directly

    private Order buildOrder(Long id, OrderStatus status, String totalAmount) {
        Order order = new Order();
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal(totalAmount));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private Payment buildPayment(Long id, Order order) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "createdAt", OffsetDateTime.parse("2026-10-18T10:00:00Z"));
        ReflectionTestUtils.setField(payment, "updatedAt", OffsetDateTime.parse("2026-10-18T10:00:00Z"));
        return payment;
    }
}
