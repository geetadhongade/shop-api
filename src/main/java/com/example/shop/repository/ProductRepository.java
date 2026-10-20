package com.example.shop.repository;

import com.example.shop.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository already gives us save, findById, findAll (with paging) and delete
public interface ProductRepository extends JpaRepository<Product, Long> {
}
