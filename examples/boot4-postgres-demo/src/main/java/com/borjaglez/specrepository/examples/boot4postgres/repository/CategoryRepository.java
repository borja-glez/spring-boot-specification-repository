package com.borjaglez.specrepository.examples.boot4postgres.repository;

import com.borjaglez.specrepository.examples.boot4postgres.entity.Category;
import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface CategoryRepository extends SpecificationRepository<Category, Long> {}
