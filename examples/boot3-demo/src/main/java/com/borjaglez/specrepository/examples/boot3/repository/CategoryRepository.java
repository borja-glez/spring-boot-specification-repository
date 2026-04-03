package com.borjaglez.specrepository.examples.boot3.repository;

import com.borjaglez.specrepository.examples.boot3.entity.Category;
import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface CategoryRepository extends SpecificationRepository<Category, Long> {}
