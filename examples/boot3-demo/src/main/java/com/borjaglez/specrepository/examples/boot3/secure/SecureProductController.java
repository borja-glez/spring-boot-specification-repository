package com.borjaglez.specrepository.examples.boot3.secure;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.examples.boot3.entity.Product;
import com.borjaglez.specrepository.examples.boot3.entity.ProductStatus;
import com.borjaglez.specrepository.examples.boot3.repository.ProductRepository;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * The secure controller example of {@code docs/security.md}: a public catalog search that exposes
 * the HTTP filter API with the protections the guide lists.
 *
 * <ul>
 *   <li>The client may filter and sort only by the declared fields.
 *   <li>The visibility rule ({@code status = ACTIVE}) is a server condition: the client cannot
 *       filter by {@code status}, and an {@code orFilter} cannot widen the rule.
 *   <li>The query returns a {@link Slice}, so no {@code COUNT(*)} runs, and it runs in a read-only
 *       transaction with a timeout.
 *   <li>The response is a DTO, so the entity and its associations are not serialized.
 * </ul>
 *
 * <p>The page size is capped by {@code spring.data.web.pageable.max-page-size} in {@code
 * application.yml}, and {@link FilterErrorHandler} answers the client errors with 400.
 */
@RestController
@RequestMapping("/api/catalog/products")
public class SecureProductController {

  private final ProductRepository productRepository;

  public SecureProductController(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @GetMapping
  @Transactional(readOnly = true, timeout = 5)
  public ProductSlice search(
      @FilterableQuery(
              value = Product.class,
              filterableFields = {"name", "price", "category.name"},
              sortableFields = {"name", "price"})
          QueryPlan<Product> query,
      Pageable pageable) {
    Slice<Product> slice =
        productRepository
            .query(query)
            .where("status", Operators.EQUALS, ProductStatus.ACTIVE)
            .leftFetch("category")
            .sortedByDefault(Sort.by("name"))
            .findSlice(pageable);
    return new ProductSlice(
        slice.getContent().stream().map(ProductView::of).toList(), slice.hasNext());
  }

  /** One page of results, without a total count. */
  public record ProductSlice(List<ProductView> content, boolean hasNext) {}

  /** The fields the endpoint returns. */
  public record ProductView(Long id, String name, BigDecimal price, String category) {
    static ProductView of(Product product) {
      return new ProductView(
          product.getId(), product.getName(), product.getPrice(), product.getCategory().getName());
    }
  }
}
