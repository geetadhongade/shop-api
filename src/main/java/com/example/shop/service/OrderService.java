package com.example.shop.service;

import com.example.shop.client.CurrencyClient;
import com.example.shop.client.ExchangeRate;
import com.example.shop.dto.OrderCreateRequest;
import com.example.shop.dto.OrderItemRequest;
import com.example.shop.dto.OrderItemResponse;
import com.example.shop.dto.OrderResponse;
import com.example.shop.dto.OrderSummaryResponse;
import com.example.shop.dto.OrderTotalResponse;
import com.example.shop.entity.Customer;
import com.example.shop.entity.Order;
import com.example.shop.entity.OrderItem;
import com.example.shop.entity.OrderStatus;
import com.example.shop.entity.Product;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.InvalidRequestException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.repository.CustomerRepository;
import com.example.shop.repository.OrderItemRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CurrencyClient currencyClient;
    private final OutboxService outboxService;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        CustomerRepository customerRepository,
                        ProductRepository productRepository,
                        CurrencyClient currencyClient,
                        OutboxService outboxService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.currencyClient = currencyClient;
        this.outboxService = outboxService;
    }

    // Places a new PENDING order. Prices are copied from the products right now,
    // so later price changes don't affect this order.
    @Transactional(timeoutString = "${shop.timeout.write-seconds}")
    public OrderResponse createOrder(OrderCreateRequest request) {
        Customer customer = findActiveCustomerOrThrow(request.customerId());

        Order order = new Order();
        order.setCustomer(customer);

        BigDecimal totalAmount = BigDecimal.ZERO;
        Set<Long> productIdsSeen = new HashSet<>();

        for (OrderItemRequest itemRequest : request.items()) {
            // Rule: the same product can't be listed twice (send a bigger quantity instead)
            if (!productIdsSeen.add(itemRequest.productId())) {
                throw new InvalidRequestException("Product " + itemRequest.productId()
                        + " is listed more than once; use a bigger quantity instead");
            }

            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product " + itemRequest.productId() + " not found"));

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(product.getPrice());
            order.addItem(item);

            // total = sum of (price x quantity) of every item
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            totalAmount = totalAmount.add(lineTotal);
        }

        order.setTotalAmount(totalAmount);
        // Saving the order also saves its items (cascade = ALL in Order.items)
        Order savedOrder = orderRepository.save(order);

        // Same transaction: if saving the event fails, the order is rolled back too (and the other way round)
        outboxService.saveOrderPlacedEvent(savedOrder);

        return OrderResponse.from(savedOrder);
    }

    @Transactional(readOnly = true, timeoutString = "${shop.timeout.read-seconds}")
    public OrderResponse getOrder(Long orderId) {
        return OrderResponse.from(findOrderOrThrow(orderId));
    }

    // One customer's orders, page by page (short version, without items)
    @Transactional(readOnly = true, timeoutString = "${shop.timeout.read-seconds}")
    public Page<OrderSummaryResponse> listOrdersForCustomer(Long customerId, Pageable pageable) {
        findActiveCustomerOrThrow(customerId);
        return orderRepository.findAllByCustomerId(customerId, pageable).map(OrderSummaryResponse::from);
    }

    // The products in one order, with quantity and the price paid
    @Transactional(readOnly = true, timeoutString = "${shop.timeout.read-seconds}")
    public List<OrderItemResponse> getOrderProducts(Long orderId) {
        findOrderOrThrow(orderId);
        return orderItemRepository.findAllByOrderId(orderId).stream()
                .map(OrderItemResponse::from)
                .toList();
    }

    // The order total converted to another currency, using today's rate from Frankfurter.
    // Deliberately NOT @Transactional: the order is read in its own short transaction (by the repository),
    // and the slow HTTP call happens afterwards, so no database connection is held while we wait for it.
    public OrderTotalResponse getOrderTotal(Long orderId, String currency) {
        // Currency codes are 3 letters, e.g. "EUR" (checked here so we never send junk to Frankfurter)
        if (currency == null || !currency.matches("^[A-Za-z]{3}$")) {
            throw new InvalidRequestException("currency must be a 3-letter code, e.g. EUR");
        }
        String targetCurrency = currency.toUpperCase();

        Order order = findOrderOrThrow(orderId);
        ExchangeRate exchangeRate = currencyClient.getRateFromBase(targetCurrency);

        // Money is shown with 2 decimals, rounded half up (e.g. 3624.055 -> 3624.06)
        BigDecimal convertedTotal = order.getTotalAmount()
                .multiply(exchangeRate.rate())
                .setScale(2, RoundingMode.HALF_UP);

        return new OrderTotalResponse(
                order.getId(),
                order.getTotalAmount(),
                exchangeRate.fromCurrency(),
                exchangeRate.toCurrency(),
                exchangeRate.rate(),
                convertedTotal,
                exchangeRate.date()
        );
    }

    // Rule: only PENDING orders can be cancelled (PAID, SHIPPED and CANCELLED can't)
    @Transactional(timeoutString = "${shop.timeout.write-seconds}")
    public OrderResponse cancelOrder(Long orderId) {
        Order order = findOrderOrThrow(orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException("Order " + orderId + " is " + order.getStatus()
                    + "; only PENDING orders can be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        // saveAndFlush writes the change now, so updatedAt in the response is the new time
        return OrderResponse.from(orderRepository.saveAndFlush(order));
    }

    private Order findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
    }

    // Deleted customers count as "not found", the same as in CustomerService
    private Customer findActiveCustomerOrThrow(Long customerId) {
        return customerRepository.findByIdAndDeletedFalse(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + customerId + " not found"));
    }
}
