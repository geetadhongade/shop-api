package com.example.shop.controller;

import com.example.shop.dto.ProductRequest;
import com.example.shop.dto.ProductResponse;
import com.example.shop.entity.Product;
import com.example.shop.entity.ProductType;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.service.ProductService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web test: real HTTP requests into ProductController (and GlobalExceptionHandler),
// but ProductService is a fake (mock), so no database is used
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private static final String VALID_PRODUCT_JSON = """
            { "name": "Laptop", "type": "ELECTRONICS", "price": 899.99 }
            """;

    // ---------- happy paths ----------

    @Test
    void listProducts_returnsPagedJson() throws Exception {
        Pageable pageable = PageRequest.of(0, 2);
        List<ProductResponse> products = List.of(
                buildResponse(1L, "Laptop", ProductType.ELECTRONICS, "899.99"),
                buildResponse(2L, "T-shirt", ProductType.CLOTHING, "15.50"));
        when(productService.listProducts(any(Pageable.class))).thenReturn(new PageImpl<>(products, pageable, 5));

        mockMvc.perform(get("/api/products").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Laptop"))
                .andExpect(jsonPath("$.content[1].type").value("CLOTHING"))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void getProduct_returnsProduct() throws Exception {
        when(productService.getProduct(1L)).thenReturn(buildResponse(1L, "Laptop", ProductType.ELECTRONICS, "899.99"));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Laptop"))
                .andExpect(jsonPath("$.type").value("ELECTRONICS"))
                .andExpect(jsonPath("$.price").value(899.99))
                .andExpect(jsonPath("$.createdAt").value("2026-10-18T10:00:00Z"));
    }

    @Test
    void createProduct_returns201() throws Exception {
        ProductRequest expectedRequest = new ProductRequest("Laptop", ProductType.ELECTRONICS, new BigDecimal("899.99"));
        when(productService.createProduct(expectedRequest))
                .thenReturn(buildResponse(1L, "Laptop", ProductType.ELECTRONICS, "899.99"));

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Laptop"));
    }

    @Test
    void updateProduct_returns200() throws Exception {
        ProductRequest expectedRequest = new ProductRequest("Laptop", ProductType.ELECTRONICS, new BigDecimal("899.99"));
        when(productService.updateProduct(1L, expectedRequest))
                .thenReturn(buildResponse(1L, "Laptop", ProductType.ELECTRONICS, "899.99"));

        mockMvc.perform(put("/api/products/1").contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deleteProduct_returns204() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(1L);
    }

    // ---------- 400 Bad Request ----------

    @Test
    void createProduct_withInvalidFields_returns400WithFieldErrors() throws Exception {
        String invalidJson = """
                { "name": "", "type": null, "price": -5 }
                """;

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("name is required"))
                .andExpect(jsonPath("$.fieldErrors.type").exists())
                .andExpect(jsonPath("$.fieldErrors.price").value("price must not be negative"));

        verifyNoInteractions(productService);
    }

    @Test
    void createProduct_withTooManyDecimals_returns400() throws Exception {
        String invalidJson = """
                { "name": "Laptop", "type": "ELECTRONICS", "price": 1.999 }
                """;

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price")
                        .value("price must have at most 8 digits before the dot and 2 after"));
    }

    @Test
    void createProduct_withUnknownType_returns400ListingAllowedValues() throws Exception {
        String invalidJson = """
                { "name": "Ball", "type": "TOYS", "price": 5 }
                """;

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Invalid value for 'type'. Allowed: ELECTRONICS, CLOTHING, FOOD, COSMETICS, DRUGS"))
                .andExpect(jsonPath("$.fieldErrors").value(nullValue()));
    }

    @Test
    void createProduct_withBrokenJson_returns400() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{ \"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or not valid JSON"));
    }

    @Test
    void createProduct_withTextPrice_returns400GenericMessage() throws Exception {
        // Wrong type for a field that is NOT a fixed list -> the generic message
        String invalidJson = """
                { "name": "Ball", "type": "FOOD", "price": "cheap" }
                """;

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or not valid JSON"));
    }

    @Test
    void getProduct_withNonNumberId_returns400() throws Exception {
        mockMvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'id' has an invalid value"));
    }

    // ---------- 404 / 405 / 409 ----------

    @Test
    void getProduct_whenMissing_returns404() throws Exception {
        when(productService.getProduct(99L)).thenThrow(new ResourceNotFoundException("Product 99 not found"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product 99 not found"));
    }

    @Test
    void unknownUrl_returns404() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No endpoint GET /api/unknown"));
    }

    @Test
    void patchProduct_returns405() throws Exception {
        mockMvc.perform(patch("/api/products/1").contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Method PATCH is not supported for this URL"));
    }

    @Test
    void deleteProduct_whenInAnOrder_returns409() throws Exception {
        doThrow(new BusinessRuleException("Product 1 is in an order and can't be deleted"))
                .when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product 1 is in an order and can't be deleted"));
    }

    @Test
    void updateProduct_whenChangedBySomeoneElse_returns409() throws Exception {
        ProductRequest expectedRequest = new ProductRequest("Laptop", ProductType.ELECTRONICS, new BigDecimal("899.99"));
        when(productService.updateProduct(1L, expectedRequest))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L));

        mockMvc.perform(put("/api/products/1").contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This record was changed by someone else, please reload and try again"));
    }

    // ---------- 503 / 500 ----------

    @Test
    void whenBulkheadIsFull_returns503WithRetryAfter() throws Exception {
        BulkheadFullException bulkheadFull = BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("api"));
        when(productService.getProduct(1L)).thenThrow(bulkheadFull);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.message").value("Server is busy, please try again shortly"));
    }

    @Test
    void whenUnexpectedError_returns500WithoutDetails() throws Exception {
        when(productService.getProduct(1L)).thenThrow(new IllegalStateException("database exploded"));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong"));
    }

    @Test
    void updateProduct_withInvalidBody_doesNotCallService() throws Exception {
        mockMvc.perform(put("/api/products/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(productService, never()).updateProduct(eq(1L), any());
    }

    // Builds a fake response, as the service would return it
    private ProductResponse buildResponse(Long id, String name, ProductType type, String price) {
        OffsetDateTime fixedTime = OffsetDateTime.parse("2026-10-18T10:00:00Z");
        return new ProductResponse(id, name, type, new BigDecimal(price), fixedTime, fixedTime);
    }
}
