package com.borjaglez.specrepository.examples.boot4;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.borjaglez.specrepository.examples.boot4.entity.Category;
import com.borjaglez.specrepository.examples.boot4.entity.Product;
import com.borjaglez.specrepository.examples.boot4.entity.ProductStatus;
import com.borjaglez.specrepository.examples.boot4.repository.CategoryRepository;
import com.borjaglez.specrepository.examples.boot4.repository.ProductRepository;

@Component
public class DataInitializer implements CommandLineRunner {

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;

  public DataInitializer(
      CategoryRepository categoryRepository, ProductRepository productRepository) {
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
  }

  @Override
  public void run(String... args) {
    Category electronics = createCategory("Electronics", "Electronic devices and gadgets");
    Category books = createCategory("Books", "Physical and digital books");
    Category clothing = createCategory("Clothing", "Apparel and accessories");
    Category sports = createCategory("Sports", "Sports equipment and gear");

    createProduct(
        "MacBook Pro 16",
        "Apple laptop with M3 chip",
        new BigDecimal("2499.99"),
        ProductStatus.ACTIVE,
        electronics,
        LocalDate.of(2024, 1, 15));
    createProduct(
        "iPhone 15 Pro",
        "Latest iPhone model",
        new BigDecimal("1199.99"),
        ProductStatus.ACTIVE,
        electronics,
        LocalDate.of(2024, 3, 10));
    createProduct(
        "Samsung Galaxy S24",
        "Android flagship phone",
        new BigDecimal("899.99"),
        ProductStatus.ACTIVE,
        electronics,
        LocalDate.of(2024, 2, 20));
    createProduct(
        "iPad Air",
        null,
        new BigDecimal("599.99"),
        ProductStatus.ACTIVE,
        electronics,
        LocalDate.of(2024, 4, 1));
    createProduct(
        "Sony WH-1000XM5",
        "Noise cancelling headphones",
        new BigDecimal("349.99"),
        ProductStatus.INACTIVE,
        electronics,
        LocalDate.of(2023, 6, 15));
    createProduct(
        "Nokia 3310",
        "Classic phone",
        new BigDecimal("49.99"),
        ProductStatus.DISCONTINUED,
        electronics,
        LocalDate.of(2020, 1, 1));

    createProduct(
        "Clean Code",
        "A handbook of agile software craftsmanship",
        new BigDecimal("39.99"),
        ProductStatus.ACTIVE,
        books,
        LocalDate.of(2023, 1, 10));
    createProduct(
        "Domain-Driven Design",
        "Tackling complexity in the heart of software",
        new BigDecimal("54.99"),
        ProductStatus.ACTIVE,
        books,
        LocalDate.of(2023, 5, 20));
    createProduct(
        "Spring in Action",
        "Spring framework guide",
        new BigDecimal("44.99"),
        ProductStatus.ACTIVE,
        books,
        LocalDate.of(2024, 1, 5));
    createProduct(
        "Java Concurrency",
        null,
        new BigDecimal("49.99"),
        ProductStatus.INACTIVE,
        books,
        LocalDate.of(2022, 8, 12));

    createProduct(
        "Running Shoes Pro",
        "Professional running shoes",
        new BigDecimal("129.99"),
        ProductStatus.ACTIVE,
        sports,
        LocalDate.of(2024, 2, 1));
    createProduct(
        "Yoga Mat Premium",
        "Non-slip yoga mat",
        new BigDecimal("29.99"),
        ProductStatus.ACTIVE,
        sports,
        LocalDate.of(2024, 3, 15));
    createProduct(
        "Tennis Racket",
        "Professional grade racket",
        new BigDecimal("199.99"),
        ProductStatus.DISCONTINUED,
        sports,
        LocalDate.of(2021, 7, 20));

    createProduct(
        "Winter Jacket",
        "Warm winter jacket",
        new BigDecimal("89.99"),
        ProductStatus.ACTIVE,
        clothing,
        LocalDate.of(2024, 1, 20));
    createProduct(
        "Summer T-Shirt",
        "Cotton t-shirt",
        new BigDecimal("19.99"),
        ProductStatus.ACTIVE,
        clothing,
        LocalDate.of(2024, 4, 10));
  }

  private Category createCategory(String name, String description) {
    Category category = new Category();
    category.setName(name);
    category.setDescription(description);
    return categoryRepository.save(category);
  }

  private void createProduct(
      String name,
      String description,
      BigDecimal price,
      ProductStatus status,
      Category category,
      LocalDate createdAt) {
    Product product = new Product();
    product.setName(name);
    product.setDescription(description);
    product.setPrice(price);
    product.setStatus(status);
    product.setCategory(category);
    product.setCreatedAt(createdAt);
    productRepository.save(product);
  }
}
