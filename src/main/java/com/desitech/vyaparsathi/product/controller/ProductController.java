
package com.desitech.vyaparsathi.product.controller;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.product.dto.ProductDto;
import com.desitech.vyaparsathi.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        try {
            List<ProductDto> products = productService.getAllProducts();
            logger.info("Fetched all products");
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            logger.error("Error fetching all products: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch products", e);
        }
    }
}