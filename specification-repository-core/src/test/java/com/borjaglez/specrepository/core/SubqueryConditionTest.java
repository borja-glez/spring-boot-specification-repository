package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.Test;

class SubqueryConditionTest {

  private static final GroupCondition EMPTY_BODY =
      new GroupCondition(LogicalOperator.AND, List.of());

  @Test
  void associationConstructorRequiresAssociationPath() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SubqueryCondition(
                    SubqueryKind.EXISTS,
                    CorrelationMode.ASSOCIATION,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    EMPTY_BODY))
        .withMessage("associationPath must not be null for ASSOCIATION");
  }

  @Test
  void entityConstructorRequiresSubEntity() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SubqueryCondition(
                    SubqueryKind.EXISTS,
                    CorrelationMode.ENTITY,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    EMPTY_BODY))
        .withMessage("subEntity must not be null for ENTITY");
  }

  @Test
  void inKindRequiresOuterFieldAndSubSelectField() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SubqueryCondition(
                    SubqueryKind.IN,
                    CorrelationMode.ENTITY,
                    null,
                    String.class,
                    List.of(),
                    null,
                    "x",
                    EMPTY_BODY))
        .withMessage("outerField must not be null for IN/NOT_IN");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SubqueryCondition(
                    SubqueryKind.IN,
                    CorrelationMode.ENTITY,
                    null,
                    String.class,
                    List.of(),
                    "x",
                    null,
                    EMPTY_BODY))
        .withMessage("subSelectField must not be null for IN/NOT_IN");
  }

  @Test
  void constructorRequiresKindModeAndBody() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SubqueryCondition(
                    null,
                    CorrelationMode.ENTITY,
                    null,
                    String.class,
                    List.of(),
                    null,
                    null,
                    EMPTY_BODY));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SubqueryCondition(
                    SubqueryKind.EXISTS, null, "orders", null, List.of(), null, null, EMPTY_BODY));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new SubqueryCondition(
                    SubqueryKind.EXISTS,
                    CorrelationMode.ASSOCIATION,
                    "orders",
                    null,
                    List.of(),
                    null,
                    null,
                    null));
  }

  @Test
  void correlationsListShouldBeDefensivelyCopiedAndAllowNull() {
    SubqueryCondition fromNull =
        new SubqueryCondition(
            SubqueryKind.EXISTS,
            CorrelationMode.ENTITY,
            null,
            String.class,
            null,
            null,
            null,
            EMPTY_BODY);

    assertThat(fromNull.correlations()).isEmpty();
    assertThat(fromNull.correlations()).isUnmodifiable();
  }

  @Test
  void enumsShouldExposeExpectedValues() {
    assertThat(SubqueryKind.values())
        .containsExactly(
            SubqueryKind.EXISTS, SubqueryKind.NOT_EXISTS, SubqueryKind.IN, SubqueryKind.NOT_IN);
    assertThat(CorrelationMode.values())
        .containsExactly(CorrelationMode.ASSOCIATION, CorrelationMode.ENTITY);
    assertThat(SubqueryKind.valueOf("EXISTS")).isEqualTo(SubqueryKind.EXISTS);
    assertThat(CorrelationMode.valueOf("ENTITY")).isEqualTo(CorrelationMode.ENTITY);
  }
}
