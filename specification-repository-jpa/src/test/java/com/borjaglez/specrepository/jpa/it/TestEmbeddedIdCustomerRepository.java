package com.borjaglez.specrepository.jpa.it;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface TestEmbeddedIdCustomerRepository
    extends SpecificationRepository<TestEmbeddedIdCustomer, TestEmbeddedIdCustomer.Key> {}
