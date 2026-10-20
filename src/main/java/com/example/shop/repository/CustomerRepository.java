package com.example.shop.repository;

import com.example.shop.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Find one customer, but only if they are not soft-deleted
    Optional<Customer> findByIdAndDeletedFalse(Long id);

    // List customers page by page, skipping soft-deleted ones
    Page<Customer> findAllByDeletedFalse(Pageable pageable);

    // Used to block sign-up with an email that is already taken (including by deleted customers)
    boolean existsByEmail(String email);
}
