package com.example.shop.service;

import com.example.shop.dto.ProductRequest;
import com.example.shop.dto.ProductResponse;
import com.example.shop.entity.Product;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.repository.OrderItemRepository;
import com.example.shop.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public ProductService(ProductRepository productRepository, OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true, timeoutString = "${shop.timeout.read-seconds}")
    public Page<ProductResponse> listProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true, timeoutString = "${shop.timeout.read-seconds}")
    public ProductResponse getProduct(Long productId) {
        return ProductResponse.from(findProductOrThrow(productId));
    }

    @Transactional(timeoutString = "${shop.timeout.write-seconds}")
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product();
        copyRequestToProduct(request, product);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(timeoutString = "${shop.timeout.write-seconds}")
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        Product product = findProductOrThrow(productId);
        copyRequestToProduct(request, product);
        // saveAndFlush writes the change now, so updatedAt in the response is the new time
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional(timeoutString = "${shop.timeout.write-seconds}")
    public void deleteProduct(Long productId) {
        Product product = findProductOrThrow(productId);

        // Rule: a product that is in any order can't be deleted
        if (orderItemRepository.existsByProductId(productId)) {
            throw new BusinessRuleException("Product " + productId + " is in an order and can't be deleted");
        }
        productRepository.delete(product);
    }

    private Product findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found"));
    }

    private void copyRequestToProduct(ProductRequest request, Product product) {
        product.setName(request.name());
        product.setType(request.type());
        product.setPrice(request.price());
    }
}
