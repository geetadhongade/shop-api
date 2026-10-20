package com.example.shop.controller;

import com.example.shop.dto.OrderCreateRequest;
import com.example.shop.dto.OrderItemRequest;
import com.example.shop.dto.OrderItemResponse;
import com.example.shop.dto.OrderResponse;
import com.example.shop.dto.OrderSummaryResponse;
import com.example.shop.entity.OrderStatus;
import com.example.shop.entity.ProductType;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.InvalidRequestException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web test: real HTTP requests into OrderController (and GlobalExceptionHandler),
// but OrderService is a fake (mock), so no database is used
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-10-18T10:00:00Z");

    private static final String VALID_ORDER_JSON = """
            { "customerId": 6, "items": [ { "productId": 1, "quantity": 2 } ] }
            """;

    // ---------- happy paths ----------

    @Test
    void createOrder_returns201() throws Exception {
        OrderCreateRequest expectedRequest = new OrderCreateRequest(6L, List.of(new OrderItemRequest(1L, 2)));
        when(orderService.createOrder(expectedRequest)).thenReturn(buildOrderResponse(OrderStatus.PENDING));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(4038.00))
                .andExpect(jsonPath("$.items[0].productName").value("Laptop"))
                .andExpect(jsonPath("$.items[0].lineTotal").value(4038.00));
    }

    @Test
    void getOrder_returnsOrder() throws Exception {
        when(orderService.getOrder(7L)).thenReturn(buildOrderResponse(OrderStatus.PENDING));

        mockMvc.perform(get("/api/orders/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(6))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void listOrders_forCustomer_returnsNewestFirst() throws Exception {
        OrderSummaryResponse summary = new OrderSummaryResponse(7L, 6L, OrderStatus.PENDING,
                new BigDecimal("4038.00"), FIXED_TIME, FIXED_TIME);
        // Default paging: 20 per page, newest orders first
        Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(orderService.listOrdersForCustomer(6L, expectedPageable))
                .thenReturn(new PageImpl<>(List.of(summary), expectedPageable, 1));

        mockMvc.perform(get("/api/orders").param("customerId", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].items").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        verify(orderService).listOrdersForCustomer(eq(6L), eq(expectedPageable));
    }

    @Test
    void getOrderProducts_returnsList() throws Exception {
        when(orderService.getOrderProducts(7L)).thenReturn(buildOrderResponse(OrderStatus.PENDING).items());

        mockMvc.perform(get("/api/orders/7/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].unitPrice").value(2019.00));
    }

    @Test
    void cancelOrder_returns200() throws Exception {
        when(orderService.cancelOrder(7L)).thenReturn(buildOrderResponse(OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/orders/7/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ---------- 400 Bad Request ----------

    @Test
    void listOrders_withoutCustomerId_returns400() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'customerId' is required"));

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_withInvalidItems_returns400WithFieldErrors() throws Exception {
        String invalidJson = """
                { "items": [ { "productId": 1, "quantity": 0 }, null, { "quantity": 1 } ] }
                """;

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerId").value("customerId is required"))
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").value("quantity must be at least 1"))
                .andExpect(jsonPath("$.fieldErrors['items[1]']").value("items must not contain empty entries"))
                .andExpect(jsonPath("$.fieldErrors['items[2].productId']").value("productId is required"));

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_withNoItems_returns400() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"customerId\": 6, \"items\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").value("items must contain at least one product"));
    }

    @Test
    void createOrder_withSameProductTwice_returns400() throws Exception {
        when(orderService.createOrder(any(OrderCreateRequest.class)))
                .thenThrow(new InvalidRequestException("Product 1 is listed more than once; use a bigger quantity instead"));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Product 1 is listed more than once; use a bigger quantity instead"));
    }

    // ---------- 404 / 409 ----------

    @Test
    void getOrder_whenMissing_returns404() throws Exception {
        when(orderService.getOrder(99L)).thenThrow(new ResourceNotFoundException("Order 99 not found"));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order 99 not found"));
    }

    @Test
    void cancelOrder_whenPaid_returns409() throws Exception {
        when(orderService.cancelOrder(7L))
                .thenThrow(new BusinessRuleException("Order 7 is PAID; only PENDING orders can be cancelled"));

        mockMvc.perform(post("/api/orders/7/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order 7 is PAID; only PENDING orders can be cancelled"));
    }

    // ---------- helpers to build fake data ----------

    // Order 7 for customer 6 with one item: 2 x Laptop at 2019.00
    private OrderResponse buildOrderResponse(OrderStatus status) {
        OrderItemResponse item = new OrderItemResponse(1L, "Laptop", ProductType.ELECTRONICS, 2,
                new BigDecimal("2019.00"), new BigDecimal("4038.00"));
        return new OrderResponse(7L, 6L, status, new BigDecimal("4038.00"), List.of(item), FIXED_TIME, FIXED_TIME);
    }
}
