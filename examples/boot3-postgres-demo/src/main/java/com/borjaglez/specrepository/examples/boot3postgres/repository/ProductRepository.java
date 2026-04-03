package com.borjaglez.specrepository.examples.boot3postgres.repository;

import com.borjaglez.specrepository.examples.boot3postgres.entity.Product;
import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface ProductRepository extends SpecificationRepository<Product, Long> {}
