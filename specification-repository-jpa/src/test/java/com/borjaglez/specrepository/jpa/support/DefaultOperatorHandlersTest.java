package com.borjaglez.specrepository.jpa.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.spi.OperatorContext;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;

class DefaultOperatorHandlersTest {

  private CriteriaBuilder cb;
  private Path<?> path;
  private Predicate predicate;
  private OperatorRegistry registry;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    cb = mock(CriteriaBuilder.class);
    path = mock(Path.class);
    predicate = mock(Predicate.class);
    registry = new OperatorRegistry(DefaultOperatorHandlers.defaults());

    // Common stubs
    doReturn((Expression<String>) (Expression<?>) path).when(path).as(String.class);
  }

  @Test
  void shouldContainAllSixteenOperators() {
    Collection<OperatorHandler> handlers = DefaultOperatorHandlers.defaults();
    assertThat(handlers).hasSize(16);
  }

  // EQUALS

  @Test
  void equalsShouldCreateEqualPredicate() {
    when(cb.equal(path, "ACTIVE")).thenReturn(predicate);

    Predicate result = registry.get(Operators.EQUALS).create(context("ACTIVE"));

    assertThat(result).isSameAs(predicate);
  }

  @SuppressWarnings("unchecked")
  @Test
  void equalsShouldIgnoreCaseWhenRequested() {
    Expression<String> upper = mock(Expression.class);
    Expression<String> unaccent = mock(Expression.class);
    when(cb.upper(any())).thenReturn(upper);
    when(cb.function(eq("unaccent"), eq(String.class), any())).thenReturn(unaccent);
    when(cb.equal(unaccent, "ACTIVE")).thenReturn(predicate);

    Predicate result = registry.get(Operators.EQUALS).create(contextIgnoreCase("active"));

    assertThat(result).isSameAs(predicate);
  }

  @SuppressWarnings("unchecked")
  @Test
  void equalsShouldHandleNullValueWithIgnoreCase() {
    Expression<String> upper = mock(Expression.class);
    Expression<String> unaccent = mock(Expression.class);
    when(cb.upper(any())).thenReturn(upper);
    when(cb.function(eq("unaccent"), eq(String.class), any())).thenReturn(unaccent);

    // When ignoreCase is true and value is null, the value stays null (not uppercased)
    // but the expression is still normalized. Verify it does not throw.
    Predicate result = registry.get(Operators.EQUALS).create(contextIgnoreCase(null));

    // cb.equal returns null when not stubbed, which is fine for verification
    assertThat(result).isNull();
  }

  // NOT_EQUALS

  @Test
  void notEqualsShouldCreateNotEqualPredicate() {
    when(cb.notEqual(path, "INACTIVE")).thenReturn(predicate);

    Predicate result = registry.get(Operators.NOT_EQUALS).create(context("INACTIVE"));

    assertThat(result).isSameAs(predicate);
  }

  @SuppressWarnings("unchecked")
  @Test
  void notEqualsShouldIgnoreCaseWhenRequested() {
    Expression<String> upper = mock(Expression.class);
    Expression<String> unaccent = mock(Expression.class);
    when(cb.upper(any())).thenReturn(upper);
    when(cb.function(eq("unaccent"), eq(String.class), any())).thenReturn(unaccent);
    when(cb.notEqual(unaccent, "INACTIVE")).thenReturn(predicate);

    Predicate result = registry.get(Operators.NOT_EQUALS).create(contextIgnoreCase("inactive"));

    assertThat(result).isSameAs(predicate);
  }

  // IS_NULL

  @Test
  void isNullShouldCreateIsNullPredicate() {
    when(cb.isNull(path)).thenReturn(predicate);

    Predicate result = registry.get(Operators.IS_NULL).create(context(null));

    assertThat(result).isSameAs(predicate);
  }

  // IS_NOT_NULL

  @Test
  void isNotNullShouldCreateIsNotNullPredicate() {
    when(cb.isNotNull(path)).thenReturn(predicate);

    Predicate result = registry.get(Operators.IS_NOT_NULL).create(context(null));

    assertThat(result).isSameAs(predicate);
  }

  // IS_EMPTY

  @SuppressWarnings("unchecked")
  @Test
  void isEmptyShouldCreateIsEmptyPredicate() {
    when(cb.isEmpty(any(Expression.class))).thenReturn(predicate);

    Predicate result = registry.get(Operators.IS_EMPTY).create(context(null));

    assertThat(result).isSameAs(predicate);
  }

  // IS_NOT_EMPTY

  @SuppressWarnings("unchecked")
  @Test
  void isNotEmptyShouldCreateIsNotEmptyPredicate() {
    when(cb.isNotEmpty(any(Expression.class))).thenReturn(predicate);

    Predicate result = registry.get(Operators.IS_NOT_EMPTY).create(context(null));

    assertThat(result).isSameAs(predicate);
  }

  // CONTAINS

  @SuppressWarnings("unchecked")
  @Test
  void containsShouldCreateLikePredicate() {
    when(cb.like(any(Expression.class), eq("%test%"))).thenReturn(predicate);

    Predicate result = registry.get(Operators.CONTAINS).create(context("test"));

    assertThat(result).isSameAs(predicate);
  }

  @SuppressWarnings("unchecked")
  @Test
  void containsShouldIgnoreCaseWhenRequested() {
    Expression<String> upper = mock(Expression.class);
    Expression<String> unaccent = mock(Expression.class);
    when(cb.upper(any())).thenReturn(upper);
    when(cb.function(eq("unaccent"), eq(String.class), any())).thenReturn(unaccent);
    when(cb.like(eq(unaccent), eq("%TEST%"))).thenReturn(predicate);

    Predicate result = registry.get(Operators.CONTAINS).create(contextIgnoreCase("test"));

    assertThat(result).isSameAs(predicate);
  }

  // NOT_CONTAINS

  @SuppressWarnings("unchecked")
  @Test
  void notContainsShouldCreateNegatedLikePredicate() {
    when(cb.like(any(Expression.class), eq("%test%"))).thenReturn(predicate);
    Predicate negated = mock(Predicate.class);
    when(predicate.not()).thenReturn(negated);

    Predicate result = registry.get(Operators.NOT_CONTAINS).create(context("test"));

    assertThat(result).isSameAs(negated);
  }

  // STARTS_WITH

  @SuppressWarnings("unchecked")
  @Test
  void startsWithShouldCreateLikePredicate() {
    when(cb.like(any(Expression.class), eq("test%"))).thenReturn(predicate);

    Predicate result = registry.get(Operators.STARTS_WITH).create(context("test"));

    assertThat(result).isSameAs(predicate);
  }

  // ENDS_WITH

  @SuppressWarnings("unchecked")
  @Test
  void endsWithShouldCreateLikePredicate() {
    when(cb.like(any(Expression.class), eq("%test"))).thenReturn(predicate);

    Predicate result = registry.get(Operators.ENDS_WITH).create(context("test"));

    assertThat(result).isSameAs(predicate);
  }

  // GREATER_THAN

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void greaterThanShouldCreateGtPredicate() {
    when(cb.greaterThan(any(Expression.class), any(Comparable.class))).thenReturn(predicate);

    Predicate result = registry.get(Operators.GREATER_THAN).create(context(10));

    assertThat(result).isSameAs(predicate);
  }

  // GREATER_THAN_OR_EQUAL

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void greaterThanOrEqualShouldCreateGtePredicate() {
    when(cb.greaterThanOrEqualTo(any(Expression.class), any(Comparable.class)))
        .thenReturn(predicate);

    Predicate result = registry.get(Operators.GREATER_THAN_OR_EQUAL).create(context(10));

    assertThat(result).isSameAs(predicate);
  }

  // LESS_THAN

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void lessThanShouldCreateLtPredicate() {
    when(cb.lessThan(any(Expression.class), any(Comparable.class))).thenReturn(predicate);

    Predicate result = registry.get(Operators.LESS_THAN).create(context(10));

    assertThat(result).isSameAs(predicate);
  }

  // LESS_THAN_OR_EQUAL

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void lessThanOrEqualShouldCreateLtePredicate() {
    when(cb.lessThanOrEqualTo(any(Expression.class), any(Comparable.class))).thenReturn(predicate);

    Predicate result = registry.get(Operators.LESS_THAN_OR_EQUAL).create(context(10));

    assertThat(result).isSameAs(predicate);
  }

  // IN

  @SuppressWarnings("unchecked")
  @Test
  void inShouldCreateInPredicateWithIterable() {
    CriteriaBuilder.In<Object> inPredicate = mock(CriteriaBuilder.In.class);
    doReturn(inPredicate).when(cb).in(path);
    doReturn(inPredicate).when(inPredicate).value(any());

    registry.get(Operators.IN).create(context(List.of("A", "B")));

    verify(inPredicate).value("A");
    verify(inPredicate).value("B");
  }

  @SuppressWarnings("unchecked")
  @Test
  void inShouldCreateInPredicateWithSingleValue() {
    CriteriaBuilder.In<Object> inPredicate = mock(CriteriaBuilder.In.class);
    doReturn(inPredicate).when(cb).in(path);
    doReturn(inPredicate).when(inPredicate).value(any());

    registry.get(Operators.IN).create(context("single"));

    verify(inPredicate).value("single");
  }

  // NOT_IN

  @SuppressWarnings("unchecked")
  @Test
  void notInShouldCreateNegatedInPredicate() {
    CriteriaBuilder.In<Object> inPredicate = mock(CriteriaBuilder.In.class);
    doReturn(inPredicate).when(cb).in(path);
    doReturn(inPredicate).when(inPredicate).value(any());
    Predicate negated = mock(Predicate.class);
    when(inPredicate.not()).thenReturn(negated);

    Predicate result = registry.get(Operators.NOT_IN).create(context(List.of("X")));

    assertThat(result).isSameAs(negated);
  }

  private OperatorContext context(Object value) {
    return new OperatorContext(cb, path, value, false);
  }

  private OperatorContext contextIgnoreCase(Object value) {
    return new OperatorContext(cb, path, value, true);
  }
}
