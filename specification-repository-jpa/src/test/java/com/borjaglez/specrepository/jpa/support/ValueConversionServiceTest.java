package com.borjaglez.specrepository.jpa.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionService;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.spi.ValueConverter;

class ValueConversionServiceTest {

  private final ConversionService conversionService = mock(ConversionService.class);

  @Test
  void shouldReturnNullWhenValueIsNull() {
    ValueConversionService service = new ValueConversionService(conversionService, List.of());

    Object result = service.convert(null, String.class, Operators.EQUALS);

    assertThat(result).isNull();
    verifyNoInteractions(conversionService);
  }

  @Test
  void shouldUseCustomConverterWhenSupported() {
    ValueConverter converter = mock(ValueConverter.class);
    when(converter.supports(LocalDate.class, Operators.EQUALS)).thenReturn(true);
    when(converter.convert("2024-01-15", LocalDate.class, Operators.EQUALS))
        .thenReturn(LocalDate.of(2024, 1, 15));

    ValueConversionService service =
        new ValueConversionService(conversionService, List.of(converter));

    Object result = service.convert("2024-01-15", LocalDate.class, Operators.EQUALS);

    assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
    verifyNoInteractions(conversionService);
  }

  @Test
  void shouldFallbackToConversionServiceWhenNoCustomConverterSupports() {
    ValueConverter converter = mock(ValueConverter.class);
    when(converter.supports(Integer.class, Operators.EQUALS)).thenReturn(false);
    when(conversionService.canConvert(String.class, Integer.class)).thenReturn(true);
    when(conversionService.convert("42", Integer.class)).thenReturn(42);

    ValueConversionService service =
        new ValueConversionService(conversionService, List.of(converter));

    Object result = service.convert("42", Integer.class, Operators.EQUALS);

    assertThat(result).isEqualTo(42);
  }

  @Test
  void shouldReturnOriginalValueWhenNothingCanConvert() {
    ValueConversionService service = new ValueConversionService(conversionService, List.of());
    when(conversionService.canConvert(String.class, Integer.class)).thenReturn(false);

    Object result = service.convert("hello", Integer.class, Operators.EQUALS);

    assertThat(result).isEqualTo("hello");
  }

  @Test
  void shouldConvertIterableElementsIndividually() {
    when(conversionService.canConvert(String.class, Integer.class)).thenReturn(true);
    when(conversionService.convert("1", Integer.class)).thenReturn(1);
    when(conversionService.convert("2", Integer.class)).thenReturn(2);
    when(conversionService.convert("3", Integer.class)).thenReturn(3);

    ValueConversionService service = new ValueConversionService(conversionService, List.of());

    Object result = service.convert(List.of("1", "2", "3"), Integer.class, Operators.IN);

    assertThat(result).isEqualTo(List.of(1, 2, 3));
  }

  @Test
  void shouldReturnIterableElementsUnchangedWhenNoConversion() {
    when(conversionService.canConvert(String.class, String.class)).thenReturn(false);

    ValueConversionService service = new ValueConversionService(conversionService, List.of());

    Object result = service.convert(List.of("a", "b"), String.class, Operators.IN);

    assertThat(result).isEqualTo(List.of("a", "b"));
  }

  @Test
  void shouldConvertIterableWithCustomConverter() {
    ValueConverter converter = mock(ValueConverter.class);
    when(converter.supports(String.class, Operators.IN)).thenReturn(true);
    when(converter.convert("x", String.class, Operators.IN)).thenReturn("X");
    when(converter.convert("y", String.class, Operators.IN)).thenReturn("Y");

    ValueConversionService service =
        new ValueConversionService(conversionService, List.of(converter));

    Object result = service.convert(List.of("x", "y"), String.class, Operators.IN);

    assertThat(result).isEqualTo(List.of("X", "Y"));
  }

  @Test
  void shouldUseFirstMatchingCustomConverter() {
    ValueConverter first = mock(ValueConverter.class);
    ValueConverter second = mock(ValueConverter.class);
    FilterOperator op = Operators.EQUALS;

    when(first.supports(String.class, op)).thenReturn(true);
    when(first.convert("val", String.class, op)).thenReturn("FIRST");

    ValueConversionService service =
        new ValueConversionService(conversionService, List.of(first, second));

    Object result = service.convert("val", String.class, op);

    assertThat(result).isEqualTo("FIRST");
    verifyNoInteractions(second);
  }
}
