package com.example.shop.controller;

import com.example.shop.dto.CustomerCreateRequest;
import com.example.shop.dto.CustomerResponse;
import com.example.shop.dto.CustomerUpdateRequest;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web test: real HTTP requests into CustomerController (and GlobalExceptionHandler),
// but CustomerService is a fake (mock), so no database is used
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    private static final String VALID_SIGN_UP_JSON = """
            { "firstName": "Anna", "lastName": "Smith", "email": "anna@example.com", "phone": "+1 555-0100",
              "street": "1 Main St", "city": "Austin", "state": "TX", "zipCode": "73301", "country": "USA",
              "username": "anna.smith", "password": "s3cretPass" }
            """;

    // ---------- happy paths ----------

    @Test
    void listCustomers_returnsPagedJson() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(customerService.listCustomers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildResponse(1L, "Anna")), pageable, 1));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("Anna"))
                .andExpect(jsonPath("$.content[0].username").value("anna.smith"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void getCustomer_returnsCustomerWithoutPassword() throws Exception {
        when(customerService.getCustomer(1L)).thenReturn(buildResponse(1L, "Anna"));

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.username").value("anna.smith"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void createCustomer_returns201() throws Exception {
        when(customerService.createCustomer(any(CustomerCreateRequest.class))).thenReturn(buildResponse(1L, "Anna"));

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_SIGN_UP_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void updateCustomer_withOnlyOneField_returns200() throws Exception {
        CustomerUpdateRequest onlyCity = new CustomerUpdateRequest(
                null, null, null, null, null, "Dallas", null, null, null);
        when(customerService.updateCustomer(1L, onlyCity)).thenReturn(buildResponse(1L, "Anna"));

        mockMvc.perform(put("/api/customers/1").contentType(MediaType.APPLICATION_JSON).content("{ \"city\": \"Dallas\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(customerService).updateCustomer(1L, onlyCity);
    }

    @Test
    void deleteCustomer_returns204() throws Exception {
        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isNoContent());

        verify(customerService).deleteCustomer(1L);
    }

    // ---------- 400 Bad Request ----------

    @Test
    void createCustomer_withInvalidFields_returns400WithFieldErrors() throws Exception {
        String invalidJson = """
                { "firstName": "", "lastName": "Smith", "email": "not-an-email", "phone": "abc",
                  "username": "a b", "password": "short" }
                """;

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").value("firstName is required"))
                .andExpect(jsonPath("$.fieldErrors.email").value("email must be a valid email address"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("phone must be 5-20 characters: digits, spaces, + - ( )"))
                .andExpect(jsonPath("$.fieldErrors.username").value("username must be 3-50 characters: letters, digits, . _ -"))
                .andExpect(jsonPath("$.fieldErrors.password").value("password must be 8-72 characters"));

        verifyNoInteractions(customerService);
    }

    @Test
    void updateCustomer_withBlankValues_returns400() throws Exception {
        String invalidJson = """
                { "firstName": "   ", "email": "" }
                """;

        mockMvc.perform(put("/api/customers/1").contentType(MediaType.APPLICATION_JSON).content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").value("firstName must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.email").value("email must not be blank"));

        verifyNoInteractions(customerService);
    }

    // ---------- 404 / 409 ----------

    @Test
    void getCustomer_whenMissing_returns404() throws Exception {
        when(customerService.getCustomer(99L)).thenThrow(new ResourceNotFoundException("Customer 99 not found"));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer 99 not found"));
    }

    @Test
    void createCustomer_whenEmailTaken_returns409() throws Exception {
        when(customerService.createCustomer(any(CustomerCreateRequest.class)))
                .thenThrow(new BusinessRuleException("Email anna@example.com is already taken"));

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_SIGN_UP_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email anna@example.com is already taken"));
    }

    // Two sign-ups at the same moment: the database UNIQUE rule rejects the second one
    @Test
    void createCustomer_whenDatabaseRejectsDuplicateEmail_returns409() throws Exception {
        when(customerService.createCustomer(any(CustomerCreateRequest.class)))
                .thenThrow(databaseError("duplicate key value violates unique constraint \"customer_email_key\""));

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_SIGN_UP_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already taken"));
    }

    @Test
    void createCustomer_whenDatabaseRejectsDuplicateUsername_returns409() throws Exception {
        when(customerService.createCustomer(any(CustomerCreateRequest.class)))
                .thenThrow(databaseError("duplicate key value violates unique constraint \"customer_credentials_username_key\""));

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_SIGN_UP_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    void deleteCustomer_whenDatabaseRejectsForOtherReason_returns409() throws Exception {
        doThrow(databaseError("violates foreign key constraint \"orders_customer_id_fkey\""))
                .when(customerService).deleteCustomer(1L);

        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Request conflicts with existing data"));
    }

    // ---------- helpers to build fake data ----------

    private CustomerResponse buildResponse(Long id, String firstName) {
        OffsetDateTime fixedTime = OffsetDateTime.parse("2026-10-18T10:00:00Z");
        return new CustomerResponse(id, firstName, "Smith", "anna@example.com", "+1 555-0100",
                "1 Main St", "Austin", "TX", "73301", "USA", "anna.smith", fixedTime, fixedTime);
    }

    // What Spring throws when the database rejects a change; the real database message is the "cause"
    private DataIntegrityViolationException databaseError(String databaseMessage) {
        return new DataIntegrityViolationException("could not execute statement", new RuntimeException(databaseMessage));
    }
}
