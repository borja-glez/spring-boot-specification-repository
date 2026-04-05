package com.borjaglez.specrepository.examples.boot4postgres.service;

import java.util.List;

import com.borjaglez.specrepository.examples.boot4postgres.entity.Product;

public record AdvancedProductSearchResponse(long totalMatches, List<Product> results) {}
