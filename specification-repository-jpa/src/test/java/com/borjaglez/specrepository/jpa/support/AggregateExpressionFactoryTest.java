package com.borjaglez.specrepository.jpa.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.borjaglez.specrepository.core.AggregateFunction;

class AggregateExpressionFactoryTest {

  @Test
  void countAlwaysResolvesToLong() {
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.COUNT, String.class))
        .isEqualTo(Long.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.COUNT, Integer.class))
        .isEqualTo(Long.class);
  }

  @Test
  void avgAlwaysResolvesToDouble() {
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.AVG, Integer.class))
        .isEqualTo(Double.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.AVG, BigDecimal.class))
        .isEqualTo(Double.class);
  }

  @Test
  void sumWidensIntegralFieldsToLong() {
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, Integer.class))
        .isEqualTo(Long.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, int.class))
        .isEqualTo(Long.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, Short.class))
        .isEqualTo(Long.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, short.class))
        .isEqualTo(Long.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, Byte.class))
        .isEqualTo(Long.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, byte.class))
        .isEqualTo(Long.class);
  }

  @Test
  void sumWidensFloatToDouble() {
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, Float.class))
        .isEqualTo(Double.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, float.class))
        .isEqualTo(Double.class);
  }

  @Test
  void sumKeepsOtherNumericTypes() {
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, Long.class))
        .isEqualTo(Long.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, Double.class))
        .isEqualTo(Double.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, BigDecimal.class))
        .isEqualTo(BigDecimal.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.SUM, BigInteger.class))
        .isEqualTo(BigInteger.class);
  }

  @Test
  void minAndMaxKeepFieldType() {
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.MIN, LocalDate.class))
        .isEqualTo(LocalDate.class);
    assertThat(AggregateExpressionFactory.resultType(AggregateFunction.MAX, String.class))
        .isEqualTo(String.class);
  }
}
