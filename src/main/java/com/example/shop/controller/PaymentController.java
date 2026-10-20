package com.example.shop.controller;

import com.example.shop.dto.PaymentCreateRequest;
import com.example.shop.dto.PaymentResponse;
import com.example.shop.service.PaymentService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Bulkhead "api" limits how many requests run at the same time (settings in application.properties).
// Mapped at /api (not /api/payments) because it also serves /api/orders/{orderId}/payment.
@RestController
@RequestMapping("/api")
@Bulkhead(name = "api")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // POST /api/payments  -> 201 Created, and the order becomes PAID
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody PaymentCreateRequest request) {
        return paymentService.createPayment(request);
    }

    // GET /api/payments/3
    @GetMapping("/payments/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return paymentService.getPayment(id);
    }

    // GET /api/orders/7/payment  (404 if the order isn't paid yet)
    @GetMapping("/orders/{orderId}/payment")
    public PaymentResponse getPaymentForOrder(@PathVariable Long orderId) {
        return paymentService.getPaymentForOrder(orderId);
    }
}
