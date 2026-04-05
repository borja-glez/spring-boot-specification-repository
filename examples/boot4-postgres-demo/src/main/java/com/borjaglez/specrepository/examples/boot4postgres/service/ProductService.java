package com.borjaglez.specrepository.examples.boot4postgres.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.examples.boot4postgres.entity.Product;
import com.borjaglez.specrepository.examples.boot4postgres.repository.ProductRepository;

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

  /** Name search with CONTAINS (case-insensitive + unaccent on PostgreSQL). */
  public List<Product> searchByName(String name) {
    return productRepository
        .query()
        .where("name", Operators.CONTAINS, name, true, false)
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

  /** Complex OR query: search in name OR description (case-insensitive + unaccent). */
  public List<Product> search(String keyword) {
    return productRepository
        .query()
        .or(
            group ->
                group
                    .where("name", Operators.CONTAINS, keyword, true, false)
                    .where("description", Operators.CONTAINS, keyword, true, false))
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

  /**
   * Advanced filter demo: nested path + and/or groups + explicit join/fetch + reusable query plan.
   */
  public AdvancedProductSearchResponse findAdvancedFilterDemo(
      String keyword, String category, String minPrice, String maxPrice) {
    QueryPlan<Product> plan =
        productRepository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .and(
                group ->
                    group
                        .where("category.name", Operators.EQUALS, category)
                        .or(
                            or ->
                                or.where("name", Operators.CONTAINS, keyword, true, false)
                                    .where(
                                        "description", Operators.CONTAINS, keyword, true, false)))
            .where("price", Operators.GREATER_THAN_OR_EQUAL, minPrice)
            .where("price", Operators.LESS_THAN_OR_EQUAL, maxPrice)
            .leftJoin("category")
            .leftFetch("category")
            .sort(Sort.by("price"))
            .plan();

    return new AdvancedProductSearchResponse(
        productRepository.count(plan), productRepository.findAll(plan));
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

  /** Find products with null description. */
  public List<Product> findWithoutDescription() {
    return productRepository.query().where("description", Operators.IS_NULL, null).findAll();
  }

  /** Find products whose name starts with a prefix (case-insensitive + unaccent). */
  public List<Product> findByNameStartingWith(String prefix) {
    return productRepository
        .query()
        .where("name", Operators.STARTS_WITH, prefix, true, false)
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
}
