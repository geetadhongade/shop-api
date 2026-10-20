package com.example.shop.controller;

import com.example.shop.dto.CustomerCreateRequest;
import com.example.shop.dto.CustomerResponse;
import com.example.shop.dto.CustomerUpdateRequest;
import com.example.shop.service.CustomerService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Bulkhead "api" limits how many requests run at the same time (settings in application.properties)
@RestController
@RequestMapping("/api/customers")
@Bulkhead(name = "api")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // GET /api/customers?page=0&size=20 (soft-deleted customers are not listed)
    @GetMapping
    public PagedModel<CustomerResponse> listCustomers(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(customerService.listCustomers(pageable));
    }

    // GET /api/customers/5
    @GetMapping("/{id}")
    public CustomerResponse getCustomer(@PathVariable Long id) {
        return customerService.getCustomer(id);
    }

    // POST /api/customers  -> 201 Created (sign-up: customer details + username + password)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(@Valid @RequestBody CustomerCreateRequest request) {
        return customerService.createCustomer(request);
    }

    // PUT /api/customers/5  (username and password can't be changed here)
    @PutMapping("/{id}")
    public CustomerResponse updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerUpdateRequest request) {
        return customerService.updateCustomer(id, request);
    }

    // DELETE /api/customers/5  -> 204 No Content (soft delete + cancels PENDING orders)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
    }
}
