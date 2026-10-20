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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit test: repositories and the password encoder are fakes (mocks), so no database is used
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerCredentialsRepository credentialsRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomerService customerService;

    // ---------- list ----------

    @Test
    void listCustomers_returnsActiveCustomersWithUsernames() {
        Pageable pageable = PageRequest.of(0, 20);
        Customer anna = buildCustomer(1L, "Anna", "anna@example.com");
        Customer bob = buildCustomer(2L, "Bob", "bob@example.com");
        when(customerRepository.findAllByDeletedFalse(pageable)).thenReturn(new PageImpl<>(List.of(anna, bob), pageable, 2));
        when(credentialsRepository.findByCustomerId(1L)).thenReturn(Optional.of(buildCredentials(anna, "anna.smith")));
        // Bob has no login row (shouldn't happen, but the response must still work)
        when(credentialsRepository.findByCustomerId(2L)).thenReturn(Optional.empty());

        Page<CustomerResponse> result = customerService.listCustomers(pageable);

        assertThat(result.getContent()).extracting(CustomerResponse::firstName).containsExactly("Anna", "Bob");
        assertThat(result.getContent()).extracting(CustomerResponse::username).containsExactly("anna.smith", null);
    }

    // ---------- get ----------

    @Test
    void getCustomer_whenFound_returnsAllFields() {
        Customer anna = buildCustomer(1L, "Anna", "anna@example.com");
        when(customerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(anna));
        when(credentialsRepository.findByCustomerId(1L)).thenReturn(Optional.of(buildCredentials(anna, "anna.smith")));

        CustomerResponse result = customerService.getCustomer(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.firstName()).isEqualTo("Anna");
        assertThat(result.lastName()).isEqualTo("Smith");
        assertThat(result.email()).isEqualTo("anna@example.com");
        assertThat(result.phone()).isEqualTo("5550100");
        assertThat(result.street()).isEqualTo("1 Main St");
        assertThat(result.city()).isEqualTo("Austin");
        assertThat(result.state()).isEqualTo("TX");
        assertThat(result.zipCode()).isEqualTo("73301");
        assertThat(result.country()).isEqualTo("USA");
        assertThat(result.username()).isEqualTo("anna.smith");
        assertThat(result.createdAt()).isEqualTo(anna.getCreatedAt());
        assertThat(result.updatedAt()).isEqualTo(anna.getUpdatedAt());
    }

    @Test
    void getCustomer_whenMissingOrDeleted_throwsNotFound() {
        when(customerRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer 99 not found");
    }

    // ---------- create ----------

    @Test
    void createCustomer_savesCustomerAndScrambledPassword() {
        CustomerCreateRequest request = buildCreateRequest("  Anna.Smith@Example.COM ", "anna.smith");
        when(customerRepository.existsByEmail("anna.smith@example.com")).thenReturn(false);
        when(credentialsRepository.existsByUsername("anna.smith")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(call -> {
            Customer saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        when(passwordEncoder.encode("s3cretPass")).thenReturn("$2a$10$scrambled");

        CustomerResponse result = customerService.createCustomer(request);

        // Customer saved with a trimmed, lowercase email and all the details
        ArgumentCaptor<Customer> savedCustomer = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(savedCustomer.capture());
        assertThat(savedCustomer.getValue().getEmail()).isEqualTo("anna.smith@example.com");
        assertThat(savedCustomer.getValue().getFirstName()).isEqualTo("Anna");
        assertThat(savedCustomer.getValue().getCity()).isEqualTo("Austin");

        // Login saved with the SCRAMBLED password, linked to the customer
        ArgumentCaptor<CustomerCredentials> savedCredentials = ArgumentCaptor.forClass(CustomerCredentials.class);
        verify(credentialsRepository).save(savedCredentials.capture());
        assertThat(savedCredentials.getValue().getUsername()).isEqualTo("anna.smith");
        assertThat(savedCredentials.getValue().getPasswordHash()).isEqualTo("$2a$10$scrambled");
        assertThat(savedCredentials.getValue().getCustomer()).isSameAs(savedCustomer.getValue());

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.username()).isEqualTo("anna.smith");
    }

    @Test
    void createCustomer_whenEmailTaken_throwsAndSavesNothing() {
        CustomerCreateRequest request = buildCreateRequest("anna@example.com", "anna.smith");
        when(customerRepository.existsByEmail("anna@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Email anna@example.com is already taken");
        verify(customerRepository, never()).save(any());
        verify(credentialsRepository, never()).save(any());
    }

    @Test
    void createCustomer_whenUsernameTaken_throwsAndSavesNothing() {
        CustomerCreateRequest request = buildCreateRequest("anna@example.com", "anna.smith");
        when(customerRepository.existsByEmail("anna@example.com")).thenReturn(false);
        when(credentialsRepository.existsByUsername("anna.smith")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Username anna.smith is already taken");
        verify(customerRepository, never()).save(any());
        verify(credentialsRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void updateCustomer_withAllFields_changesEverything() {
        Customer anna = buildCustomer(1L, "Anna", "anna@example.com");
        when(customerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(anna));
        when(customerRepository.existsByEmail("anna.new@example.com")).thenReturn(false);
        when(customerRepository.saveAndFlush(anna)).thenReturn(anna);
        when(credentialsRepository.findByCustomerId(1L)).thenReturn(Optional.of(buildCredentials(anna, "anna.smith")));
        CustomerUpdateRequest request = new CustomerUpdateRequest(
                "Annie", "Jones", "Anna.New@Example.com", "5550199",
                "2 Oak Ave", "Dallas", "Texas", "75001", "United States");

        CustomerResponse result = customerService.updateCustomer(1L, request);

        assertThat(result.firstName()).isEqualTo("Annie");
        assertThat(result.lastName()).isEqualTo("Jones");
        assertThat(result.email()).isEqualTo("anna.new@example.com");
        assertThat(result.phone()).isEqualTo("5550199");
        assertThat(result.street()).isEqualTo("2 Oak Ave");
        assertThat(result.city()).isEqualTo("Dallas");
        assertThat(result.state()).isEqualTo("Texas");
        assertThat(result.zipCode()).isEqualTo("75001");
        assertThat(result.country()).isEqualTo("United States");
        assertThat(result.username()).isEqualTo("anna.smith");
    }

    @Test
    void updateCustomer_withNoFields_keepsEverything() {
        Customer anna = buildCustomer(1L, "Anna", "anna@example.com");
        when(customerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(anna));
        when(customerRepository.saveAndFlush(anna)).thenReturn(anna);
        CustomerUpdateRequest emptyRequest = new CustomerUpdateRequest(
                null, null, null, null, null, null, null, null, null);

        CustomerResponse result = customerService.updateCustomer(1L, emptyRequest);

        assertThat(result.firstName()).isEqualTo("Anna");
        assertThat(result.lastName()).isEqualTo("Smith");
        assertThat(result.email()).isEqualTo("anna@example.com");
        assertThat(result.phone()).isEqualTo("5550100");
        assertThat(result.street()).isEqualTo("1 Main St");
        assertThat(result.city()).isEqualTo("Austin");
        assertThat(result.state()).isEqualTo("TX");
        assertThat(result.zipCode()).isEqualTo("73301");
        assertThat(result.country()).isEqualTo("USA");
        // No email sent -> no "is it taken?" check
        verify(customerRepository, never()).existsByEmail(anyString());
    }

    @Test
    void updateCustomer_withSameEmail_doesNotCheckIfTaken() {
        Customer anna = buildCustomer(1L, "Anna", "anna@example.com");
        when(customerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(anna));
        when(customerRepository.saveAndFlush(anna)).thenReturn(anna);
        CustomerUpdateRequest request = new CustomerUpdateRequest(
                null, null, "ANNA@example.com", null, null, null, null, null, null);

        customerService.updateCustomer(1L, request);

        // Her own email (in different case) is not "taken"
        verify(customerRepository, never()).existsByEmail(anyString());
        assertThat(anna.getEmail()).isEqualTo("anna@example.com");
    }

    @Test
    void updateCustomer_whenNewEmailTaken_throwsAndSavesNothing() {
        Customer anna = buildCustomer(1L, "Anna", "anna@example.com");
        when(customerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(anna));
        when(customerRepository.existsByEmail("bob@example.com")).thenReturn(true);
        CustomerUpdateRequest request = new CustomerUpdateRequest(
                null, null, "bob@example.com", null, null, null, null, null, null);

        assertThatThrownBy(() -> customerService.updateCustomer(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Email bob@example.com is already taken");
        verify(customerRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateCustomer_whenMissing_throwsNotFound() {
        when(customerRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());
        CustomerUpdateRequest request = new CustomerUpdateRequest(
                "Annie", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> customerService.updateCustomer(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer 99 not found");
        verify(customerRepository, never()).saveAndFlush(any());
    }

    // ---------- delete ----------

    @Test
    void deleteCustomer_softDeletesAndCancelsOnlyPendingOrders() {
        Customer anna = buildCustomer(1L, "Anna", "anna@example.com");
        Order pendingOrder = buildOrder(OrderStatus.PENDING);
        Order paidOrder = buildOrder(OrderStatus.PAID);
        Order shippedOrder = buildOrder(OrderStatus.SHIPPED);
        when(customerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(anna));
        when(orderRepository.findAllByCustomerId(1L)).thenReturn(List.of(pendingOrder, paidOrder, shippedOrder));

        customerService.deleteCustomer(1L);

        assertThat(anna.isDeleted()).isTrue();
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(shippedOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void deleteCustomer_whenMissing_throwsNotFound() {
        when(customerRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.deleteCustomer(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer 99 not found");
        verify(orderRepository, never()).findAllByCustomerId(any());
    }

    // ---------- helpers to build fake data ----------

    // The database normally sets id and dates, and the entity has no setters for them,
    // so the test sets them directly
    private Customer buildCustomer(Long id, String firstName, String email) {
        Customer customer = new Customer();
        customer.setFirstName(firstName);
        customer.setLastName("Smith");
        customer.setEmail(email);
        customer.setPhone("5550100");
        customer.setStreet("1 Main St");
        customer.setCity("Austin");
        customer.setState("TX");
        customer.setZipCode("73301");
        customer.setCountry("USA");
        ReflectionTestUtils.setField(customer, "id", id);
        ReflectionTestUtils.setField(customer, "createdAt", OffsetDateTime.parse("2026-10-18T10:00:00Z"));
        ReflectionTestUtils.setField(customer, "updatedAt", OffsetDateTime.parse("2026-10-18T10:00:00Z"));
        return customer;
    }

    private CustomerCredentials buildCredentials(Customer customer, String username) {
        CustomerCredentials credentials = new CustomerCredentials();
        credentials.setCustomer(customer);
        credentials.setUsername(username);
        credentials.setPasswordHash("$2a$10$scrambled");
        return credentials;
    }

    private CustomerCreateRequest buildCreateRequest(String email, String username) {
        return new CustomerCreateRequest("Anna", "Smith", email, "5550100",
                "1 Main St", "Austin", "TX", "73301", "USA", username, "s3cretPass");
    }

    private Order buildOrder(OrderStatus status) {
        Order order = new Order();
        order.setStatus(status);
        return order;
    }
}
