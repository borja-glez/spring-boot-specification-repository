package com.borjaglez.specrepository.jpa.spi;

import com.borjaglez.specrepository.core.FilterOperator;

public interface ValueConverter {
  boolean supports(Class<?> targetType, FilterOperator operator);

  Object convert(Object value, Class<?> targetType, FilterOperator operator);
}
