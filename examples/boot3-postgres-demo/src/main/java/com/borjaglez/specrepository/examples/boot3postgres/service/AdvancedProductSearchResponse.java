package com.borjaglez.specrepository.examples.boot3postgres.service;

import java.util.List;

import com.borjaglez.specrepository.examples.boot3postgres.entity.Product;

public record AdvancedProductSearchResponse(long totalMatches, List<Product> results) {}
