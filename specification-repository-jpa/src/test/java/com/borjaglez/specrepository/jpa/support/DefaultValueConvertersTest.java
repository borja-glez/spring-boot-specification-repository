package com.borjaglez.specrepository.jpa.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.spi.ValueConverter;

class DefaultValueConvertersTest {

  @Test
  void localDateConverterShouldSupportLocalDate() {
    ValueConverter converter = DefaultValueConverters.localDateConverter();

    assertThat(converter.supports(LocalDate.class, Operators.EQUALS)).isTrue();
  }

  @Test
  void localDateConverterShouldNotSupportOtherTypes() {
    ValueConverter converter = DefaultValueConverters.localDateConverter();

    assertThat(converter.supports(String.class, Operators.EQUALS)).isFalse();
  }

  @Test
  void localDateConverterShouldPassthroughLocalDate() {
    ValueConverter converter = DefaultValueConverters.localDateConverter();
    LocalDate date = LocalDate.of(2024, 1, 15);

    Object result = converter.convert(date, LocalDate.class, Operators.EQUALS);

    assertThat(result).isEqualTo(date);
  }

  @Test
  void localDateConverterShouldParseString() {
    ValueConverter converter = DefaultValueConverters.localDateConverter();

    Object result = converter.convert("2024-01-15", LocalDate.class, Operators.EQUALS);

    assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
  }

  @Test
  void localDateTimeConverterShouldSupportLocalDateTime() {
    ValueConverter converter = DefaultValueConverters.localDateTimeConverter();

    assertThat(converter.supports(LocalDateTime.class, Operators.EQUALS)).isTrue();
  }

  @Test
  void localDateTimeConverterShouldNotSupportOtherTypes() {
    ValueConverter converter = DefaultValueConverters.localDateTimeConverter();

    assertThat(converter.supports(String.class, Operators.EQUALS)).isFalse();
  }

  @Test
  void localDateTimeConverterShouldPassthroughLocalDateTime() {
    ValueConverter converter = DefaultValueConverters.localDateTimeConverter();
    LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30);

    Object result = converter.convert(dateTime, LocalDateTime.class, Operators.EQUALS);

    assertThat(result).isEqualTo(dateTime);
  }

  @Test
  void localDateTimeConverterShouldParseString() {
    ValueConverter converter = DefaultValueConverters.localDateTimeConverter();

    Object result = converter.convert("2024-01-15T10:30:00", LocalDateTime.class, Operators.EQUALS);

    assertThat(result).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
  }

  @Test
  void datePassthroughConverterShouldSupportDate() {
    ValueConverter converter = DefaultValueConverters.datePassthroughConverter();

    assertThat(converter.supports(Date.class, Operators.EQUALS)).isTrue();
  }

  @Test
  void datePassthroughConverterShouldNotSupportOtherTypes() {
    ValueConverter converter = DefaultValueConverters.datePassthroughConverter();

    assertThat(converter.supports(String.class, Operators.EQUALS)).isFalse();
  }

  @Test
  void datePassthroughConverterShouldReturnSameValue() {
    ValueConverter converter = DefaultValueConverters.datePassthroughConverter();
    Date date = new Date();

    Object result = converter.convert(date, Date.class, Operators.EQUALS);

    assertThat(result).isSameAs(date);
  }

  @Test
  void datePassthroughConverterShouldSupportSqlDateSubclass() {
    ValueConverter converter = DefaultValueConverters.datePassthroughConverter();

    assertThat(converter.supports(java.sql.Date.class, Operators.EQUALS)).isTrue();
  }
}
