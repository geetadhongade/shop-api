package com.example.shop.controller;

import com.example.shop.dto.PaymentCreateRequest;
import com.example.shop.dto.PaymentResponse;
import com.example.shop.entity.PaymentMethod;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.InvalidRequestException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web test: real HTTP requests into PaymentController (and GlobalExceptionHandler),
// but PaymentService is a fake (mock), so no database is used
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private static final String VALID_PAYMENT_JSON = """
            { "orderId": 7, "amount": 100.00, "method": "UPI" }
            """;

    // ---------- happy paths ----------

    @Test
    void createPayment_returns201() throws Exception {
        PaymentCreateRequest expectedRequest = new PaymentCreateRequest(7L, new BigDecimal("100.00"), PaymentMethod.UPI);
        when(paymentService.createPayment(expectedRequest)).thenReturn(buildResponse());

        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_PAYMENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderId").value(7))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.method").value("UPI"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-18T10:00:00Z"));
    }

    @Test
    void getPayment_returnsPayment() throws Exception {
        when(paymentService.getPayment(1L)).thenReturn(buildResponse());

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getPaymentForOrder_returnsPayment() throws Exception {
        when(paymentService.getPaymentForOrder(7L)).thenReturn(buildResponse());

        mockMvc.perform(get("/api/orders/7/payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(7));
    }

    // ---------- 400 Bad Request ----------

    @Test
    void createPayment_withInvalidFields_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("{ \"amount\": -1 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.orderId").value("orderId is required"))
                .andExpect(jsonPath("$.fieldErrors.amount").value("amount must not be negative"))
                .andExpect(jsonPath("$.fieldErrors.method").value("method is required (CARD, CASH or UPI)"));

        verifyNoInteractions(paymentService);
    }

    @Test
    void createPayment_withTooManyDecimals_returns400() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"orderId\": 7, \"amount\": 100.001, \"method\": \"UPI\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount")
                        .value("amount must have at most 10 digits before the dot and 2 after"));
    }

    @Test
    void createPayment_withUnknownMethod_returns400ListingAllowedValues() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"orderId\": 7, \"amount\": 100.00, \"method\": \"PAYPAL\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'method'. Allowed: CARD, CASH, UPI"));
    }

    @Test
    void createPayment_withWrongAmount_returns400() throws Exception {
        when(paymentService.createPayment(any(PaymentCreateRequest.class)))
                .thenThrow(new InvalidRequestException("Amount must be 4067.97 (the order total), but was 100.00"));

        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_PAYMENT_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount must be 4067.97 (the order total), but was 100.00"));
    }

    // ---------- 404 / 409 ----------

    @Test
    void getPaymentForOrder_whenNotPaidYet_returns404() throws Exception {
        when(paymentService.getPaymentForOrder(7L))
                .thenThrow(new ResourceNotFoundException("Order 7 has no payment yet"));

        mockMvc.perform(get("/api/orders/7/payment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order 7 has no payment yet"));
    }

    @Test
    void createPayment_whenOrderCancelled_returns409() throws Exception {
        when(paymentService.createPayment(any(PaymentCreateRequest.class)))
                .thenThrow(new BusinessRuleException("Order 8 is CANCELLED; only PENDING orders can be paid"));

        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_PAYMENT_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order 8 is CANCELLED; only PENDING orders can be paid"));
    }

    // Two payments at the same moment: the database UNIQUE rule on payment.order_id rejects the second
    @Test
    void createPayment_whenDatabaseRejectsSecondPayment_returns409() throws Exception {
        when(paymentService.createPayment(any(PaymentCreateRequest.class)))
                .thenThrow(new DataIntegrityViolationException("could not execute statement",
                        new RuntimeException("duplicate key value violates unique constraint \"payment_order_id_key\"")));

        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_PAYMENT_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order already has a payment"));
    }

    // ---------- helpers to build fake data ----------

    private PaymentResponse buildResponse() {
        OffsetDateTime fixedTime = OffsetDateTime.parse("2026-10-18T10:00:00Z");
        return new PaymentResponse(1L, 7L, new BigDecimal("100.00"), PaymentMethod.UPI, fixedTime, fixedTime);
    }
}
