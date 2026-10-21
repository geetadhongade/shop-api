package com.example.shop.service;

import com.example.shop.dto.OrderCreateRequest;
import com.example.shop.dto.OrderItemRequest;
import com.example.shop.dto.OrderItemResponse;
import com.example.shop.dto.OrderResponse;
import com.example.shop.dto.OrderSummaryResponse;
import com.example.shop.entity.Customer;
import com.example.shop.entity.Order;
import com.example.shop.entity.OrderItem;
import com.example.shop.entity.OrderStatus;
import com.example.shop.entity.Product;
import com.example.shop.entity.ProductType;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.InvalidRequestException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.repository.CustomerRepository;
import com.example.shop.repository.OrderItemRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit test: all repositories are fakes (mocks), so no database is used
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private OrderService orderService;

    // ---------- create ----------

    @Test
    void createOrder_copiesPricesAndCalculatesTotal() {
        Customer bob = buildCustomer(6L);
        Product laptop = buildProduct(1L, "Laptop", ProductType.ELECTRONICS, "2019.00");
        Product lipstick = buildProduct(3L, "Lipstick", ProductType.COSMETICS, "9.99");
        when(customerRepository.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(bob));
        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));
        when(productRepository.findById(3L)).thenReturn(Optional.of(lipstick));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> {
            Order saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        OrderCreateRequest request = new OrderCreateRequest(6L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(3L, 3)));

        OrderResponse result = orderService.createOrder(request);

        // What was saved: the customer, PENDING status, both items with the product price copied in
        ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(savedOrder.capture());
        assertThat(savedOrder.getValue().getCustomer()).isSameAs(bob);
        assertThat(savedOrder.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getValue().getItems()).hasSize(2);
        assertThat(savedOrder.getValue().getItems().get(0).getOrder()).isSameAs(savedOrder.getValue());

        // 2 x 2019.00 + 3 x 9.99 = 4038.00 + 29.97 = 4067.97
        assertThat(result.totalAmount()).isEqualByComparingTo("4067.97");
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.customerId()).isEqualTo(6L);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.items()).extracting(OrderItemResponse::productName).containsExactly("Laptop", "Lipstick");
        assertThat(result.items()).extracting(OrderItemResponse::unitPrice)
                .containsExactly(new BigDecimal("2019.00"), new BigDecimal("9.99"));
        assertThat(result.items().get(0).lineTotal()).isEqualByComparingTo("4038.00");
        assertThat(result.items().get(1).lineTotal()).isEqualByComparingTo("29.97");
        assertThat(result.items().get(1).productType()).isEqualTo(ProductType.COSMETICS);
        assertThat(result.items().get(1).quantity()).isEqualTo(3);
    }

    @Test
    void createOrder_savesOrderPlacedEventForTheSavedOrder() {
        Customer bob = buildCustomer(6L);
        when(customerRepository.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(bob));
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(buildProduct(1L, "Laptop", ProductType.ELECTRONICS, "2019.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> {
            Order saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        orderService.createOrder(new OrderCreateRequest(6L, List.of(new OrderItemRequest(1L, 2))));

        // The outbox gets the order AFTER it was saved (so it already has its ID)
        ArgumentCaptor<Order> eventOrder = ArgumentCaptor.forClass(Order.class);
        verify(outboxService).saveOrderPlacedEvent(eventOrder.capture());
        assertThat(eventOrder.getValue().getId()).isEqualTo(7L);
        assertThat(eventOrder.getValue().getCustomer()).isSameAs(bob);
    }

    @Test
    void createOrder_whenSavingEventFails_failsTheWholeOrder() {
        when(customerRepository.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(buildCustomer(6L)));
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(buildProduct(1L, "Laptop", ProductType.ELECTRONICS, "2019.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        doThrow(new IllegalStateException("outbox insert failed"))
                .when(outboxService).saveOrderPlacedEvent(any(Order.class));

        // The error reaches createOrder, so Spring rolls back its transaction: no order without its event
        assertThatThrownBy(() -> orderService.createOrder(
                new OrderCreateRequest(6L, List.of(new OrderItemRequest(1L, 2)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox insert failed");
    }

    @Test
    void createOrder_whenCustomerMissingOrDeleted_throwsNotFound() {
        when(customerRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());
        OrderCreateRequest request = new OrderCreateRequest(99L, List.of(new OrderItemRequest(1L, 1)));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer 99 not found");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_whenSameProductTwice_throwsInvalidRequest() {
        when(customerRepository.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(buildCustomer(6L)));
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(buildProduct(1L, "Laptop", ProductType.ELECTRONICS, "2019.00")));
        OrderCreateRequest request = new OrderCreateRequest(6L, List.of(
                new OrderItemRequest(1L, 1),
                new OrderItemRequest(1L, 2)));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Product 1 is listed more than once; use a bigger quantity instead");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_whenProductMissing_throwsNotFound() {
        when(customerRepository.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(buildCustomer(6L)));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        OrderCreateRequest request = new OrderCreateRequest(6L, List.of(new OrderItemRequest(999L, 1)));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product 999 not found");
        verify(orderRepository, never()).save(any());
    }

    // ---------- get / list / products ----------

    @Test
    void getOrder_whenFound_returnsOrderWithItems() {
        Order order = buildOrder(7L, OrderStatus.PENDING);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getOrder(7L);

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.createdAt()).isEqualTo(order.getCreatedAt());
        assertThat(result.updatedAt()).isEqualTo(order.getUpdatedAt());
    }

    @Test
    void getOrder_whenMissing_throwsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order 99 not found");
    }

    @Test
    void listOrdersForCustomer_returnsSummaries() {
        Pageable pageable = PageRequest.of(0, 20);
        when(customerRepository.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(buildCustomer(6L)));
        when(orderRepository.findAllByCustomerId(6L, pageable)).thenReturn(new PageImpl<>(
                List.of(buildOrder(8L, OrderStatus.CANCELLED), buildOrder(7L, OrderStatus.PAID)), pageable, 2));

        Page<OrderSummaryResponse> result = orderService.listOrdersForCustomer(6L, pageable);

        assertThat(result.getContent()).extracting(OrderSummaryResponse::id).containsExactly(8L, 7L);
        assertThat(result.getContent()).extracting(OrderSummaryResponse::status)
                .containsExactly(OrderStatus.CANCELLED, OrderStatus.PAID);
        OrderSummaryResponse first = result.getContent().get(0);
        assertThat(first.customerId()).isEqualTo(6L);
        assertThat(first.totalAmount()).isEqualByComparingTo("4038.00");
        assertThat(first.createdAt()).isNotNull();
        assertThat(first.updatedAt()).isNotNull();
    }

    @Test
    void listOrdersForCustomer_whenCustomerMissing_throwsNotFound() {
        Pageable pageable = PageRequest.of(0, 20);
        when(customerRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.listOrdersForCustomer(99L, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer 99 not found");
        verify(orderRepository, never()).findAllByCustomerId(any(), any(Pageable.class));
    }

    @Test
    void getOrderProducts_returnsItems() {
        Order order = buildOrder(7L, OrderStatus.PENDING);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(7L)).thenReturn(order.getItems());

        List<OrderItemResponse> result = orderService.getOrderProducts(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).quantity()).isEqualTo(2);
    }

    @Test
    void getOrderProducts_whenOrderMissing_throwsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderProducts(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order 99 not found");
        verify(orderItemRepository, never()).findAllByOrderId(any());
    }

    // ---------- cancel ----------

    @Test
    void cancelOrder_whenPending_setsCancelled() {
        Order order = buildOrder(7L, OrderStatus.PENDING);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        OrderResponse result = orderService.cancelOrder(7L);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void cancelOrder_whenPaid_throwsAndDoesNotSave() {
        Order order = buildOrder(7L, OrderStatus.PAID);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(7L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Order 7 is PAID; only PENDING orders can be cancelled");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelOrder_whenMissing_throwsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order 99 not found");
    }

    // ---------- helpers to build fake data ----------
    // The database normally sets ids and dates, and the entities have no setters for them,
    // so the tests set them directly

    private Customer buildCustomer(Long id) {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    private Product buildProduct(Long id, String name, ProductType type, String price) {
        Product product = new Product();
        product.setName(name);
        product.setType(type);
        product.setPrice(new BigDecimal(price));
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    // An order for customer 6 with one item: 2 x Laptop at 2019.00
    private Order buildOrder(Long id, OrderStatus status) {
        Order order = new Order();
        order.setCustomer(buildCustomer(6L));
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("4038.00"));

        OrderItem item = new OrderItem();
        item.setProduct(buildProduct(1L, "Laptop", ProductType.ELECTRONICS, "2019.00"));
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("2019.00"));
        order.addItem(item);

        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "createdAt", OffsetDateTime.parse("2026-10-18T10:00:00Z"));
        ReflectionTestUtils.setField(order, "updatedAt", OffsetDateTime.parse("2026-10-18T10:00:00Z"));
        return order;
    }
}
