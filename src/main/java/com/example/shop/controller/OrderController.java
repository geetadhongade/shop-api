package com.example.shop.controller;

import com.example.shop.dto.OrderCreateRequest;
import com.example.shop.dto.OrderItemResponse;
import com.example.shop.dto.OrderResponse;
import com.example.shop.dto.OrderSummaryResponse;
import com.example.shop.dto.OrderTotalResponse;
import com.example.shop.service.OrderService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Bulkhead "api" limits how many requests run at the same time (settings in application.properties)
@RestController
@RequestMapping("/api/orders")
@Bulkhead(name = "api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // POST /api/orders  -> 201 Created
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return orderService.createOrder(request);
    }

    // GET /api/orders/5  (full order with items)
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    // GET /api/orders?customerId=6&page=0&size=20  (customerId is required; newest orders first)
    @GetMapping
    public PagedModel<OrderSummaryResponse> listOrdersForCustomer(
            @RequestParam Long customerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return new PagedModel<>(orderService.listOrdersForCustomer(customerId, pageable));
    }

    // GET /api/orders/5/products  (products in the order, with quantity and price paid)
    @GetMapping("/{id}/products")
    public List<OrderItemResponse> getOrderProducts(@PathVariable Long id) {
        return orderService.getOrderProducts(id);
    }

    // GET /api/orders/5/total?currency=EUR  (order total converted with today's rate from Frankfurter)
    @GetMapping("/{id}/total")
    public OrderTotalResponse getOrderTotal(@PathVariable Long id, @RequestParam String currency) {
        return orderService.getOrderTotal(id, currency);
    }

    // POST /api/orders/5/cancel  (only PENDING orders; otherwise 409)
    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }
}
