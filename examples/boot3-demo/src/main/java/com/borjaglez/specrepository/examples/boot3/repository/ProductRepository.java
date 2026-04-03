package com.borjaglez.specrepository.examples.boot3.repository;

import com.borjaglez.specrepository.examples.boot3.entity.Product;
import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface ProductRepository extends SpecificationRepository<Product, Long> {}
