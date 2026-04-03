package com.borjaglez.specrepository.examples.boot3postgres.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.borjaglez.specrepository.examples.boot3postgres.entity.Product;
import com.borjaglez.specrepository.examples.boot3postgres.service.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping
  public List<Product> findAll() {
    return productService.findAll();
  }

  @GetMapping("/{id}")
  public ResponseEntity<Product> findById(@PathVariable Long id) {
    return productService
        .findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/status/{status}")
  public List<Product> findByStatus(@PathVariable String status) {
    return productService.findByStatus(status);
  }

  @GetMapping("/search")
  public List<Product> search(@RequestParam String q) {
    return productService.search(q);
  }

  @GetMapping("/search/name")
  public List<Product> searchByName(@RequestParam String q) {
    return productService.searchByName(q);
  }

  @GetMapping("/search/name-starts-with")
  public List<Product> findByNameStartingWith(@RequestParam String prefix) {
    return productService.findByNameStartingWith(prefix);
  }

  @GetMapping("/price-range")
  public List<Product> findByPriceRange(@RequestParam String min, @RequestParam String max) {
    return productService.findByPriceRange(min, max);
  }

  @GetMapping("/category/{categoryName}")
  public List<Product> findByCategoryName(@PathVariable String categoryName) {
    return productService.findByCategoryName(categoryName);
  }

  @GetMapping("/category/{categoryName}/price-range")
  public List<Product> findActiveInCategory(
      @PathVariable String categoryName, @RequestParam String min, @RequestParam String max) {
    return productService.findActiveInCategoryWithPriceRange(categoryName, min, max);
  }

  @GetMapping("/paginated")
  public Page<Product> findPaginated(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
    return productService.findPaginated(page, size);
  }

  @GetMapping("/count/{status}")
  public long countByStatus(@PathVariable String status) {
    return productService.countByStatus(status);
  }

  @GetMapping("/without-description")
  public List<Product> findWithoutDescription() {
    return productService.findWithoutDescription();
  }

  @GetMapping("/in-categories")
  public List<Product> findInCategories(@RequestParam List<String> categories) {
    return productService.findInCategories(categories);
  }

  @GetMapping("/cheapest-active")
  public ResponseEntity<Product> findCheapestActive() {
    return productService
        .findCheapestActive()
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
