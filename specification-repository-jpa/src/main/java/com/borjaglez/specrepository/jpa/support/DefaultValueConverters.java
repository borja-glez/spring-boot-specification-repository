package com.borjaglez.specrepository.jpa.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.jpa.spi.ValueConverter;

public final class DefaultValueConverters {
  private DefaultValueConverters() {}

  public static Collection<ValueConverter> defaults() {
    return List.of(localDateConverter(), localDateTimeConverter(), datePassthroughConverter());
  }

  public static ValueConverter localDateConverter() {
    return new SimpleValueConverter(
        LocalDate.class,
        value ->
            value instanceof LocalDate localDate ? localDate : LocalDate.parse(value.toString()));
  }

  public static ValueConverter localDateTimeConverter() {
    return new SimpleValueConverter(
        LocalDateTime.class,
        value ->
            value instanceof LocalDateTime localDateTime
                ? localDateTime
                : LocalDateTime.parse(value.toString()));
  }

  public static ValueConverter datePassthroughConverter() {
    return new SimpleValueConverter(Date.class, value -> value);
  }

  private record SimpleValueConverter(Class<?> targetType, Function<Object, Object> converter)
      implements ValueConverter {
    @Override
    public boolean supports(Class<?> candidateType, FilterOperator operator) {
      return targetType.isAssignableFrom(candidateType);
    }

    @Override
    public Object convert(Object value, Class<?> candidateType, FilterOperator operator) {
      return converter.apply(value);
    }
  }
}
