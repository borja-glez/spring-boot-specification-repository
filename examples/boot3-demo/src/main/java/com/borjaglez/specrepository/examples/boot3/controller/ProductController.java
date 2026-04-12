package com.borjaglez.specrepository.examples.boot3.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.examples.boot3.entity.Product;
import com.borjaglez.specrepository.examples.boot3.service.ProductAggregateSummaryResponse;
import com.borjaglez.specrepository.examples.boot3.service.ProductService;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

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

  @GetMapping("/aggregates/active-summary")
  public ProductAggregateSummaryResponse findActiveAggregateSummary() {
    return productService.findActiveAggregateSummary();
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

  @GetMapping("/created-between")
  public List<Product> findByCreatedBetween(@RequestParam String from, @RequestParam String to) {
    return productService.findByCreatedBetween(from, to);
  }

  @GetMapping("/names")
  public List<?> findProductNames() {
    return productService.findProductNames();
  }

  @GetMapping("/name-and-price")
  public List<?> findProductNameAndPrice(@RequestParam String status) {
    return productService.findProductNameAndPrice(status);
  }

  @GetMapping("/count/grouped-by-status")
  public long countGroupedByStatus() {
    return productService.countGroupedByStatus();
  }

  @GetMapping("/count/grouped-by-category")
  public long countGroupedByCategory() {
    return productService.countGroupedByCategory();
  }

  @GetMapping("/reporting/status-revenue-above")
  public List<GroupedRow> findStatusRevenueAbove(@RequestParam("threshold") BigDecimal threshold) {
    return productService.findStatusRevenueAbove(threshold);
  }

  @GetMapping("/filter")
  public Page<Product> filter(
      @FilterableQuery(
              value = Product.class,
              filterableFields = {
                "name",
                "status",
                "price",
                "description",
                "category.name",
                "createdAt"
              },
              sortableFields = {"name", "price", "createdAt"})
          QueryPlan<Product> query,
      Pageable pageable) {
    return productService.findByQueryPlan(query, pageable);
  }
}
