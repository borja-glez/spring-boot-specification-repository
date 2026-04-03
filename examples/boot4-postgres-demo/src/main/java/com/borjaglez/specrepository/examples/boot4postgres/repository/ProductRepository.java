package com.borjaglez.specrepository.examples.boot4postgres.repository;

import com.borjaglez.specrepository.examples.boot4postgres.entity.Product;
import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface ProductRepository extends SpecificationRepository<Product, Long> {}
