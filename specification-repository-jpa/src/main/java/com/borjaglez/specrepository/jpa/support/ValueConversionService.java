package com.borjaglez.specrepository.jpa.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.StreamSupport;

import org.springframework.core.convert.ConversionService;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.jpa.spi.ValueConverter;

public class ValueConversionService {
  private final ConversionService conversionService;
  private final List<ValueConverter> converters;

  public ValueConversionService(
      ConversionService conversionService, Collection<ValueConverter> converters) {
    this.conversionService = conversionService;
    this.converters = new ArrayList<>(converters);
  }

  public Object convert(Object value, Class<?> targetType, FilterOperator operator) {
    if (value == null) {
      return null;
    }
    if (value instanceof Iterable<?> iterable) {
      return convertIterable(iterable, targetType, operator);
    }
    return convertSingle(value, targetType, operator);
  }

  private List<Object> convertIterable(
      Iterable<?> iterable, Class<?> targetType, FilterOperator operator) {
    return StreamSupport.stream(iterable.spliterator(), false)
        .map(element -> convertSingle(element, targetType, operator))
        .toList();
  }

  private Object convertSingle(Object value, Class<?> targetType, FilterOperator operator) {
    for (ValueConverter converter : converters) {
      if (converter.supports(targetType, operator)) {
        return converter.convert(value, targetType, operator);
      }
    }
    if (conversionService.canConvert(value.getClass(), targetType)) {
      return conversionService.convert(value, targetType);
    }
    return value;
  }
}
