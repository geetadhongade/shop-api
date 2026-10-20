package com.example.shop.service;

import com.example.shop.dto.CustomerCreateRequest;
import com.example.shop.dto.CustomerResponse;
import com.example.shop.dto.CustomerUpdateRequest;
import com.example.shop.entity.Customer;
import com.example.shop.entity.CustomerCredentials;
import com.example.shop.entity.Order;
import com.example.shop.entity.OrderStatus;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.repository.CustomerCredentialsRepository;
import com.example.shop.repository.CustomerRepository;
import com.example.shop.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    // Orders in these statuses are cancelled when their customer is deleted.
    // PAID and SHIPPED orders are left alone (already paid for / already on their way).
    private static final List<OrderStatus> CANCELLABLE_STATUSES = List.of(OrderStatus.PENDING);

    private final CustomerRepository customerRepository;
    private final CustomerCredentialsRepository credentialsRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerCredentialsRepository credentialsRepository,
                           OrderRepository orderRepository,
                           PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.credentialsRepository = credentialsRepository;
        this.orderRepository = orderRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true, timeoutString = "${shop.timeout.read-seconds}")
    public Page<CustomerResponse> listCustomers(Pageable pageable) {
        return customerRepository.findAllByDeletedFalse(pageable)
                .map(customer -> CustomerResponse.from(customer, findUsername(customer.getId())));
    }

    @Transactional(readOnly = true, timeoutString = "${shop.timeout.read-seconds}")
    public CustomerResponse getCustomer(Long customerId) {
        Customer customer = findActiveCustomerOrThrow(customerId);
        return CustomerResponse.from(customer, findUsername(customerId));
    }

    // Saves the customer AND their login together. If anything fails, nothing is saved.
    @Transactional(timeoutString = "${shop.timeout.write-seconds}")
    public CustomerResponse createCustomer(CustomerCreateRequest request) {
        String email = normalizeEmail(request.email());

        if (customerRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email " + email + " is already taken");
        }
        if (credentialsRepository.existsByUsername(request.username())) {
            throw new BusinessRuleException("Username " + request.username() + " is already taken");
        }

        Customer customer = new Customer();
        copyDetailsToCustomer(customer, request.firstName(), request.lastName(), email, request.phone(),
                request.street(), request.city(), request.state(), request.zipCode(), request.country());
        customer = customerRepository.save(customer);

        CustomerCredentials credentials = new CustomerCredentials();
        credentials.setCustomer(customer);
        credentials.setUsername(request.username());
        // Store only the BCrypt-scrambled password, never the real one
        credentials.setPasswordHash(passwordEncoder.encode(request.password()));
        credentialsRepository.save(credentials);

        return CustomerResponse.from(customer, request.username());
    }

    // Only the fields that were sent (not null) are changed; missing fields keep their current value
    @Transactional(timeoutString = "${shop.timeout.write-seconds}")
    public CustomerResponse updateCustomer(Long customerId, CustomerUpdateRequest request) {
        Customer customer = findActiveCustomerOrThrow(customerId);

        if (request.email() != null) {
            String email = normalizeEmail(request.email());

            // Email may change, but only to one nobody else has
            boolean emailChanged = !email.equals(customer.getEmail());
            if (emailChanged && customerRepository.existsByEmail(email)) {
                throw new BusinessRuleException("Email " + email + " is already taken");
            }
            customer.setEmail(email);
        }

        if (request.firstName() != null) {
            customer.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            customer.setLastName(request.lastName());
        }
        if (request.phone() != null) {
            customer.setPhone(request.phone());
        }
        if (request.street() != null) {
            customer.setStreet(request.street());
        }
        if (request.city() != null) {
            customer.setCity(request.city());
        }
        if (request.state() != null) {
            customer.setState(request.state());
        }
        if (request.zipCode() != null) {
            customer.setZipCode(request.zipCode());
        }
        if (request.country() != null) {
            customer.setCountry(request.country());
        }

        // saveAndFlush writes the change now, so updatedAt in the response is the new time
        customer = customerRepository.saveAndFlush(customer);

        return CustomerResponse.from(customer, findUsername(customerId));
    }

    // Soft delete: the customer row is kept but hidden, and their PENDING orders are cancelled
    @Transactional(timeoutString = "${shop.timeout.write-seconds}")
    public void deleteCustomer(Long customerId) {
        Customer customer = findActiveCustomerOrThrow(customerId);
        customer.setDeleted(true);

        int cancelledOrderCount = 0;
        for (Order order : orderRepository.findAllByCustomerId(customerId)) {
            if (CANCELLABLE_STATUSES.contains(order.getStatus())) {
                order.setStatus(OrderStatus.CANCELLED);
                cancelledOrderCount++;
            }
        }
        // No save() needed: changes to loaded entities are written when the transaction ends

        log.info("Customer {} deleted, {} orders cancelled", customerId, cancelledOrderCount);
    }

    // Finds a customer that is not soft-deleted; deleted customers count as "not found"
    private Customer findActiveCustomerOrThrow(Long customerId) {
        return customerRepository.findByIdAndDeletedFalse(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + customerId + " not found"));
    }

    private String findUsername(Long customerId) {
        return credentialsRepository.findByCustomerId(customerId)
                .map(CustomerCredentials::getUsername)
                .orElse(null);
    }

    // "  Anna@Example.COM " -> "anna@example.com", so the same email can't be registered twice in different case
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private void copyDetailsToCustomer(Customer customer, String firstName, String lastName, String email, String phone,
                                       String street, String city, String state, String zipCode, String country) {
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setStreet(street);
        customer.setCity(city);
        customer.setState(state);
        customer.setZipCode(zipCode);
        customer.setCountry(country);
    }
}
