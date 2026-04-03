package com.borjaglez.specrepository.examples.boot3postgres.repository;

import com.borjaglez.specrepository.examples.boot3postgres.entity.Category;
import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface CategoryRepository extends SpecificationRepository<Category, Long> {}
