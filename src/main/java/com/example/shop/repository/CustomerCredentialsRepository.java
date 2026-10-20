package com.example.shop.repository;

import com.example.shop.entity.CustomerCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerCredentialsRepository extends JpaRepository<CustomerCredentials, Long> {

    // The login of one customer — used to show the username in responses
    Optional<CustomerCredentials> findByCustomerId(Long customerId);

    // Used to block sign-up with a username that is already taken
    boolean existsByUsername(String username);
}
