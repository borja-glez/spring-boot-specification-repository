package com.borjaglez.specrepository.examples.boot3.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.examples.boot3.entity.Product;
import com.borjaglez.specrepository.examples.boot3.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
public class ProductService {

  private final ProductRepository productRepository;

  public ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  /** Find all products. */
  public List<Product> findAll() {
    return productRepository.findAll();
  }

  /** Find by id. */
  public Optional<Product> findById(Long id) {
    return productRepository.findById(id);
  }

  /** Simple equals filter. */
  public List<Product> findByStatus(String status) {
    return productRepository.query().where("status", Operators.EQUALS, status).findAll();
  }

  /** Name search with CONTAINS. */
  public List<Product> searchByName(String name) {
    return productRepository
        .query()
        .where("name", Operators.CONTAINS, name)
        .sort(Sort.by("name"))
        .findAll();
  }

  /** Price range with BETWEEN operator. */
  public List<Product> findByPriceRange(String minPrice, String maxPrice) {
    return productRepository
        .query()
        .where("price", Operators.BETWEEN, List.of(minPrice, maxPrice))
        .sort(Sort.by("price"))
        .findAll();
  }

  /** Filter by category name using nested path (join). */
  public List<Product> findByCategoryName(String categoryName) {
    return productRepository
        .query()
        .where("category.name", Operators.EQUALS, categoryName)
        .leftFetch("category")
        .findAll();
  }

  /** Complex OR query: search in name OR description. */
  public List<Product> search(String keyword) {
    return productRepository
        .query()
        .or(
            group ->
                group
                    .where("name", Operators.CONTAINS, keyword)
                    .where("description", Operators.CONTAINS, keyword))
        .sort(Sort.by("name"))
        .findAll();
  }

  /** Combined filters: active products in a category with price range. */
  public List<Product> findActiveInCategoryWithPriceRange(
      String category, String minPrice, String maxPrice) {
    return productRepository
        .query()
        .where("status", Operators.EQUALS, "ACTIVE")
        .where("category.name", Operators.EQUALS, category)
        .where("price", Operators.GREATER_THAN_OR_EQUAL, minPrice)
        .where("price", Operators.LESS_THAN_OR_EQUAL, maxPrice)
        .leftFetch("category")
        .sort(Sort.by("price"))
        .findAll();
  }

  /** Paginated results. */
  public Page<Product> findPaginated(int page, int size) {
    return productRepository
        .query()
        .where("status", Operators.NOT_EQUALS, "DISCONTINUED")
        .sort(Sort.by(Sort.Direction.DESC, "createdAt"))
        .findAll(PageRequest.of(page, size));
  }

  /** Count products by status. */
  public long countByStatus(String status) {
    return productRepository.query().where("status", Operators.EQUALS, status).count();
  }

  /** Aggregate demo: summary of active product prices plus field-level count. */
  public ProductAggregateSummaryResponse findActiveAggregateSummary() {
    return new ProductAggregateSummaryResponse(
        toBigDecimal(
            productRepository
                .query()
                .where("status", Operators.EQUALS, "ACTIVE")
                .sum("price")
                .findOne()),
        toDouble(
            productRepository
                .query()
                .where("status", Operators.EQUALS, "ACTIVE")
                .avg("price")
                .findOne()),
        toBigDecimal(
            productRepository
                .query()
                .where("status", Operators.EQUALS, "ACTIVE")
                .min("price")
                .findOne()),
        toBigDecimal(
            productRepository
                .query()
                .where("status", Operators.EQUALS, "ACTIVE")
                .max("price")
                .findOne()),
        toLong(
            productRepository
                .query()
                .where("status", Operators.EQUALS, "ACTIVE")
                .count("description")
                .findOne()));
  }

  /** Find products with null description. */
  public List<Product> findWithoutDescription() {
    return productRepository.query().where("description", Operators.IS_NULL, null).findAll();
  }

  /** Find products whose name starts with a prefix. */
  public List<Product> findByNameStartingWith(String prefix) {
    return productRepository
        .query()
        .where("name", Operators.STARTS_WITH, prefix)
        .sort(Sort.by("name"))
        .findAll();
  }

  /** Find distinct products by multiple categories using IN operator. */
  public List<Product> findInCategories(List<String> categoryNames) {
    return productRepository
        .query()
        .where("category.name", Operators.IN, categoryNames)
        .leftFetch("category")
        .distinct()
        .findAll();
  }

  /** Find single product: cheapest active product. */
  public Optional<Product> findCheapestActive() {
    return productRepository
        .query()
        .where("status", Operators.EQUALS, "ACTIVE")
        .sort(Sort.by("price"))
        .findOne();
  }

  /** Date range with BETWEEN operator. */
  public List<Product> findByCreatedBetween(String from, String to) {
    return productRepository
        .query()
        .where("createdAt", Operators.BETWEEN, List.of(from, to))
        .sort(Sort.by("createdAt"))
        .findAll();
  }

  /** Select projection: product names only. */
  public List<?> findProductNames() {
    return productRepository
        .query()
        .where("status", Operators.EQUALS, "ACTIVE")
        .sort(Sort.by("name"))
        .select("name")
        .findAll();
  }

  /** Multi-field projection: name and price. */
  public List<?> findProductNameAndPrice(String status) {
    return productRepository
        .query()
        .where("status", Operators.EQUALS, status)
        .sort(Sort.by("price"))
        .select("name", "price")
        .findAll();
  }

  /** Grouped count: number of distinct statuses. */
  public long countGroupedByStatus() {
    return productRepository
        .query()
        .where("status", Operators.IS_NOT_NULL, null)
        .groupBy("status")
        .count();
  }

  /** Grouped count: number of distinct categories with products. */
  public long countGroupedByCategory() {
    return productRepository.query().groupBy("category.name").count();
  }

  /** Execute a pre-built QueryPlan from HTTP filter parsing. */
  public Page<Product> findByQueryPlan(QueryPlan<Product> queryPlan, Pageable pageable) {
    return productRepository.findAll(queryPlan, pageable);
  }

  private static BigDecimal toBigDecimal(Optional<?> value) {
    return value.map(BigDecimal.class::cast).orElse(null);
  }

  private static Double toDouble(Optional<?> value) {
    return value.map(Double.class::cast).orElse(null);
  }

  private static long toLong(Optional<?> value) {
    return value.map(Long.class::cast).orElse(0L);
  }
}
