package com.borjaglez.specrepository.jpa.spi;

import static org.apiguardian.api.API.Status.MAINTAINED;

import org.apiguardian.api.API;

import com.borjaglez.specrepository.core.FilterOperator;

@API(status = MAINTAINED, since = "1.0.0")
public interface ValueConverter {
  boolean supports(Class<?> targetType, FilterOperator operator);

  Object convert(Object value, Class<?> targetType, FilterOperator operator);
}
