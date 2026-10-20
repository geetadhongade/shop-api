package com.example.shop.service;

import com.example.shop.dto.ProductRequest;
import com.example.shop.dto.ProductResponse;
import com.example.shop.entity.Product;
import com.example.shop.entity.ProductType;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.repository.OrderItemRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit test: the repositories are fakes (mocks), so no database is used
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    // The real ProductService, with the two fake repositories passed in
    @InjectMocks
    private ProductService productService;

    // ---------- list ----------

    @Test
    void listProducts_returnsPageOfProducts() {
        Pageable pageable = PageRequest.of(0, 20);
        Product laptop = buildProduct(1L, "Laptop", ProductType.ELECTRONICS, "899.99");
        Product shirt = buildProduct(2L, "T-shirt", ProductType.CLOTHING, "15.50");
        when(productRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(laptop, shirt), pageable, 2));

        Page<ProductResponse> result = productService.listProducts(pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(ProductResponse::name).containsExactly("Laptop", "T-shirt");
    }

    // ---------- get ----------

    @Test
    void getProduct_whenFound_returnsProduct() {
        Product laptop = buildProduct(1L, "Laptop", ProductType.ELECTRONICS, "899.99");
        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));

        ProductResponse result = productService.getProduct(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Laptop");
        assertThat(result.type()).isEqualTo(ProductType.ELECTRONICS);
        assertThat(result.price()).isEqualByComparingTo("899.99");
        assertThat(result.createdAt()).isEqualTo(laptop.getCreatedAt());
        assertThat(result.updatedAt()).isEqualTo(laptop.getUpdatedAt());
    }

    @Test
    void getProduct_whenMissing_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product 99 not found");
    }

    // ---------- create ----------

    @Test
    void createProduct_copiesRequestAndSaves() {
        ProductRequest request = new ProductRequest("Lipstick", ProductType.COSMETICS, new BigDecimal("9.99"));
        // The fake repository returns what the database would: the same product, now with an ID
        when(productRepository.save(any(Product.class))).thenAnswer(call -> {
            Product saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        ProductResponse result = productService.createProduct(request);

        // Check what was actually passed to save()
        ArgumentCaptor<Product> savedProduct = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(savedProduct.capture());
        assertThat(savedProduct.getValue().getName()).isEqualTo("Lipstick");
        assertThat(savedProduct.getValue().getType()).isEqualTo(ProductType.COSMETICS);
        assertThat(savedProduct.getValue().getPrice()).isEqualByComparingTo("9.99");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Lipstick");
    }

    // ---------- update ----------

    @Test
    void updateProduct_whenFound_changesFieldsAndSaves() {
        Product laptop = buildProduct(1L, "Laptop", ProductType.ELECTRONICS, "899.99");
        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));
        when(productRepository.saveAndFlush(laptop)).thenReturn(laptop);
        ProductRequest request = new ProductRequest("Laptop Pro", ProductType.ELECTRONICS, new BigDecimal("1199.00"));

        ProductResponse result = productService.updateProduct(1L, request);

        verify(productRepository).saveAndFlush(laptop);
        assertThat(result.name()).isEqualTo("Laptop Pro");
        assertThat(result.price()).isEqualByComparingTo("1199.00");
    }

    @Test
    void updateProduct_whenMissing_throwsNotFoundAndSavesNothing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        ProductRequest request = new ProductRequest("Laptop Pro", ProductType.ELECTRONICS, new BigDecimal("1199.00"));

        assertThatThrownBy(() -> productService.updateProduct(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product 99 not found");
        verify(productRepository, never()).saveAndFlush(any());
    }

    // ---------- delete ----------

    @Test
    void deleteProduct_whenNotInAnyOrder_deletes() {
        Product laptop = buildProduct(1L, "Laptop", ProductType.ELECTRONICS, "899.99");
        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));
        when(orderItemRepository.existsByProductId(1L)).thenReturn(false);

        productService.deleteProduct(1L);

        verify(productRepository).delete(laptop);
    }

    @Test
    void deleteProduct_whenInAnOrder_throwsAndDoesNotDelete() {
        Product laptop = buildProduct(1L, "Laptop", ProductType.ELECTRONICS, "899.99");
        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));
        when(orderItemRepository.existsByProductId(1L)).thenReturn(true);

        assertThatThrownBy(() -> productService.deleteProduct(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Product 1 is in an order and can't be deleted");
        verify(productRepository, never()).delete(any());
    }

    @Test
    void deleteProduct_whenMissing_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product 99 not found");
        verify(productRepository, never()).delete(any());
    }

    // Builds a fake product. The database normally sets id and dates, and the entity has no setters
    // for them, so the test sets them directly.
    private Product buildProduct(Long id, String name, ProductType type, String price) {
        Product product = new Product();
        product.setName(name);
        product.setType(type);
        product.setPrice(new BigDecimal(price));
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "createdAt", OffsetDateTime.parse("2026-10-18T10:00:00Z"));
        ReflectionTestUtils.setField(product, "updatedAt", OffsetDateTime.parse("2026-10-18T10:00:00Z"));
        return product;
    }
}
