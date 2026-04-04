package com.borjaglez.specrepository.examples.boot3.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.specrepository.core.Operators;
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

  /** Price range with GREATER_THAN_OR_EQUAL and LESS_THAN_OR_EQUAL. */
  public List<Product> findByPriceRange(String minPrice, String maxPrice) {
    return productRepository
        .query()
        .where("price", Operators.GREATER_THAN_OR_EQUAL, minPrice)
        .where("price", Operators.LESS_THAN_OR_EQUAL, maxPrice)
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
}
