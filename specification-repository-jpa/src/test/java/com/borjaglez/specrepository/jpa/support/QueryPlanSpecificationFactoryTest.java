package com.borjaglez.specrepository.jpa.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.borjaglez.specrepository.core.FetchInstruction;
import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.JoinInstruction;
import com.borjaglez.specrepository.core.JoinMode;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.jpa.spi.OperatorContext;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;

@SuppressWarnings("unchecked")
class QueryPlanSpecificationFactoryTest {

  private OperatorRegistry operatorRegistry;
  private ValueConversionService valueConversionService;
  private PathResolver pathResolver;
  private QueryPlanSpecificationFactory factory;
  private Root<Object> root;
  private CriteriaQuery<Object> query;
  private CriteriaBuilder cb;

  @BeforeEach
  void setUp() {
    operatorRegistry = mock(OperatorRegistry.class);
    valueConversionService = mock(ValueConversionService.class);
    pathResolver = mock(PathResolver.class);
    factory =
        new QueryPlanSpecificationFactory(operatorRegistry, valueConversionService, pathResolver);
    root = mock(Root.class);
    query = mock(CriteriaQuery.class);
    cb = mock(CriteriaBuilder.class);
  }

  @Test
  void shouldReturnNullPredicateWhenNoConditions() {
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.AND, List.of());
    QueryPlan<Object> plan = plan(rootCondition);

    Specification<Object> spec = factory.create(plan);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNull();
    verify(query, never()).where(any(Predicate.class));
  }

  @Test
  void shouldApplyDistinct() {
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.AND, List.of());
    QueryPlan<Object> plan =
        new QueryPlan<>(
            Object.class,
            rootCondition,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Sort.unsorted(),
            true);

    Specification<Object> spec = factory.create(plan);
    spec.toPredicate(root, query, cb);

    verify(query).distinct(true);
  }

  @Test
  void shouldNotApplyDistinctWhenFalse() {
    QueryPlan<Object> plan = plan(new GroupCondition(LogicalOperator.AND, List.of()));

    Specification<Object> spec = factory.create(plan);
    spec.toPredicate(root, query, cb);

    verify(query, never()).distinct(true);
  }

  @Test
  void shouldApplyJoins() {
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.AND, List.of());
    List<JoinInstruction> joins = List.of(new JoinInstruction("profile", JoinMode.LEFT));
    QueryPlan<Object> plan =
        new QueryPlan<>(
            Object.class,
            rootCondition,
            joins,
            List.of(),
            List.of(),
            List.of(),
            Sort.unsorted(),
            false);

    Specification<Object> spec = factory.create(plan);
    spec.toPredicate(root, query, cb);

    verify(pathResolver)
        .join(eq(root), any(AssociationRegistry.class), eq("profile"), eq(JoinMode.LEFT));
  }

  @Test
  void shouldApplyFetchesWhenNotCountQuery() {
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.AND, List.of());
    List<FetchInstruction> fetches = List.of(new FetchInstruction("profile", JoinMode.LEFT));
    QueryPlan<Object> plan =
        new QueryPlan<>(
            Object.class,
            rootCondition,
            List.of(),
            fetches,
            List.of(),
            List.of(),
            Sort.unsorted(),
            false);

    doReturn(Object.class).when(query).getResultType();

    Specification<Object> spec = factory.create(plan);
    spec.toPredicate(root, query, cb);

    verify(pathResolver)
        .fetch(eq(root), any(AssociationRegistry.class), eq("profile"), eq(JoinMode.LEFT));
  }

  @Test
  void shouldSkipFetchesForLongCountQuery() {
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.AND, List.of());
    List<FetchInstruction> fetches = List.of(new FetchInstruction("profile", JoinMode.LEFT));
    QueryPlan<Object> plan =
        new QueryPlan<>(
            Object.class,
            rootCondition,
            List.of(),
            fetches,
            List.of(),
            List.of(),
            Sort.unsorted(),
            false);

    doReturn(Long.class).when(query).getResultType();

    Specification<Object> spec = factory.create(plan);
    spec.toPredicate(root, query, cb);

    verify(pathResolver, never()).fetch(any(), any(), any(), any());
  }

  @Test
  void shouldSkipFetchesForPrimitiveLongCountQuery() {
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.AND, List.of());
    List<FetchInstruction> fetches = List.of(new FetchInstruction("profile", JoinMode.LEFT));
    QueryPlan<Object> plan =
        new QueryPlan<>(
            Object.class,
            rootCondition,
            List.of(),
            fetches,
            List.of(),
            List.of(),
            Sort.unsorted(),
            false);

    doReturn(long.class).when(query).getResultType();

    Specification<Object> spec = factory.create(plan);
    spec.toPredicate(root, query, cb);

    verify(pathResolver, never()).fetch(any(), any(), any(), any());
  }

  @Test
  void shouldApplyGroupBy() {
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.AND, List.of());
    List<String> groupBy = List.of("status", "name");
    QueryPlan<Object> plan =
        new QueryPlan<>(
            Object.class,
            rootCondition,
            List.of(),
            List.of(),
            List.of(),
            groupBy,
            Sort.unsorted(),
            false);

    Path<?> statusPath = mock(Path.class);
    Path<?> namePath = mock(Path.class);
    doReturn(statusPath)
        .when(pathResolver)
        .resolve(eq(root), any(AssociationRegistry.class), eq("status"), eq(JoinMode.LEFT));
    doReturn(namePath)
        .when(pathResolver)
        .resolve(eq(root), any(AssociationRegistry.class), eq("name"), eq(JoinMode.LEFT));

    Specification<Object> spec = factory.create(plan);
    spec.toPredicate(root, query, cb);

    verify(query).groupBy(List.of(statusPath, namePath));
  }

  @Test
  void shouldNotApplyGroupByWhenEmpty() {
    QueryPlan<Object> plan = plan(new GroupCondition(LogicalOperator.AND, List.of()));

    Specification<Object> spec = factory.create(plan);
    spec.toPredicate(root, query, cb);

    verify(query, never()).groupBy(any(List.class));
  }

  @Test
  void shouldBuildAndPredicateForMultipleConditions() {
    Path<?> namePath = mock(Path.class);
    doReturn(String.class).when(namePath).getJavaType();
    Path<?> statusPath = mock(Path.class);
    doReturn(String.class).when(statusPath).getJavaType();

    doReturn(namePath).when(pathResolver).resolve(eq(root), any(), eq("name"), eq(JoinMode.LEFT));
    doReturn(statusPath)
        .when(pathResolver)
        .resolve(eq(root), any(), eq("status"), eq(JoinMode.LEFT));
    when(valueConversionService.convert("Borja", String.class, Operators.EQUALS))
        .thenReturn("Borja");
    when(valueConversionService.convert("ACTIVE", String.class, Operators.EQUALS))
        .thenReturn("ACTIVE");

    Predicate p1 = mock(Predicate.class);
    Predicate p2 = mock(Predicate.class);
    Predicate combined = mock(Predicate.class);

    OperatorHandler eqHandler = mock(OperatorHandler.class);
    when(operatorRegistry.get(Operators.EQUALS)).thenReturn(eqHandler);
    when(eqHandler.create(any(OperatorContext.class))).thenReturn(p1, p2);
    when(cb.and(any(Predicate[].class))).thenReturn(combined);

    PredicateCondition c1 = new PredicateCondition("name", Operators.EQUALS, "Borja", false, false);
    PredicateCondition c2 =
        new PredicateCondition("status", Operators.EQUALS, "ACTIVE", false, false);
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.AND, List.of(c1, c2));
    QueryPlan<Object> plan = plan(rootCondition);

    Specification<Object> spec = factory.create(plan);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isSameAs(combined);
    verify(cb).and(any(Predicate[].class));
  }

  @Test
  void shouldBuildOrPredicateForOrGroup() {
    Path<?> namePath = mock(Path.class);
    doReturn(String.class).when(namePath).getJavaType();

    doReturn(namePath).when(pathResolver).resolve(eq(root), any(), eq("name"), eq(JoinMode.LEFT));
    when(valueConversionService.convert("Borja", String.class, Operators.EQUALS))
        .thenReturn("Borja");

    Predicate p1 = mock(Predicate.class);
    Predicate combined = mock(Predicate.class);

    OperatorHandler eqHandler = mock(OperatorHandler.class);
    when(operatorRegistry.get(Operators.EQUALS)).thenReturn(eqHandler);
    when(eqHandler.create(any(OperatorContext.class))).thenReturn(p1);
    when(cb.or(any(Predicate[].class))).thenReturn(combined);

    PredicateCondition c1 = new PredicateCondition("name", Operators.EQUALS, "Borja", false, false);
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.OR, List.of(c1));
    QueryPlan<Object> plan = plan(rootCondition);

    Specification<Object> spec = factory.create(plan);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isSameAs(combined);
    verify(cb).or(any(Predicate[].class));
  }

  @Test
  void shouldHandleNestedGroupConditions() {
    Path<?> namePath = mock(Path.class);
    doReturn(String.class).when(namePath).getJavaType();

    doReturn(namePath).when(pathResolver).resolve(eq(root), any(), eq("name"), eq(JoinMode.LEFT));
    when(valueConversionService.convert("Borja", String.class, Operators.EQUALS))
        .thenReturn("Borja");

    Predicate p1 = mock(Predicate.class);
    Predicate innerCombined = mock(Predicate.class);
    Predicate outerCombined = mock(Predicate.class);

    OperatorHandler eqHandler = mock(OperatorHandler.class);
    when(operatorRegistry.get(Operators.EQUALS)).thenReturn(eqHandler);
    when(eqHandler.create(any(OperatorContext.class))).thenReturn(p1);
    when(cb.or(any(Predicate[].class))).thenReturn(innerCombined);
    when(cb.and(any(Predicate[].class))).thenReturn(outerCombined);

    PredicateCondition c1 = new PredicateCondition("name", Operators.EQUALS, "Borja", false, false);
    GroupCondition nested = new GroupCondition(LogicalOperator.OR, List.of(c1));
    GroupCondition rootCondition =
        new GroupCondition(LogicalOperator.AND, List.of((QueryCondition) nested));
    QueryPlan<Object> plan = plan(rootCondition);

    Specification<Object> spec = factory.create(plan);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isSameAs(outerCombined);
  }

  @Test
  void shouldHandleEmptyNestedGroupCondition() {
    GroupCondition nested = new GroupCondition(LogicalOperator.OR, List.of());
    GroupCondition rootCondition =
        new GroupCondition(LogicalOperator.AND, List.of((QueryCondition) nested));
    QueryPlan<Object> plan = plan(rootCondition);

    Specification<Object> spec = factory.create(plan);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNull();
  }

  @Test
  void shouldApplyIncludeNullsOrCondition() {
    Path<?> namePath = mock(Path.class);
    doReturn(String.class).when(namePath).getJavaType();

    doReturn(namePath).when(pathResolver).resolve(eq(root), any(), eq("name"), eq(JoinMode.LEFT));
    when(valueConversionService.convert("Borja", String.class, Operators.EQUALS))
        .thenReturn("Borja");

    Predicate eqPredicate = mock(Predicate.class);
    Predicate isNullPredicate = mock(Predicate.class);
    Predicate orPredicate = mock(Predicate.class);
    Predicate combined = mock(Predicate.class);

    OperatorHandler eqHandler = mock(OperatorHandler.class);
    when(operatorRegistry.get(Operators.EQUALS)).thenReturn(eqHandler);
    when(eqHandler.create(any(OperatorContext.class))).thenReturn(eqPredicate);
    when(cb.isNull(namePath)).thenReturn(isNullPredicate);
    when(cb.or(eqPredicate, isNullPredicate)).thenReturn(orPredicate);
    when(cb.and(any(Predicate[].class))).thenReturn(combined);

    PredicateCondition c1 = new PredicateCondition("name", Operators.EQUALS, "Borja", false, true);
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.AND, List.of(c1));
    QueryPlan<Object> plan = plan(rootCondition);

    Specification<Object> spec = factory.create(plan);
    spec.toPredicate(root, query, cb);

    verify(cb).or(eqPredicate, isNullPredicate);
  }

  @Test
  void shouldSetWherePredicateOnQuery() {
    Path<?> namePath = mock(Path.class);
    doReturn(String.class).when(namePath).getJavaType();

    doReturn(namePath).when(pathResolver).resolve(eq(root), any(), eq("name"), eq(JoinMode.LEFT));
    when(valueConversionService.convert("Borja", String.class, Operators.EQUALS))
        .thenReturn("Borja");

    Predicate p1 = mock(Predicate.class);
    Predicate combined = mock(Predicate.class);

    OperatorHandler eqHandler = mock(OperatorHandler.class);
    when(operatorRegistry.get(Operators.EQUALS)).thenReturn(eqHandler);
    when(eqHandler.create(any(OperatorContext.class))).thenReturn(p1);
    when(cb.and(any(Predicate[].class))).thenReturn(combined);

    PredicateCondition c1 = new PredicateCondition("name", Operators.EQUALS, "Borja", false, false);
    GroupCondition rootCondition = new GroupCondition(LogicalOperator.AND, List.of(c1));
    QueryPlan<Object> plan = plan(rootCondition);

    Specification<Object> spec = factory.create(plan);
    spec.toPredicate(root, query, cb);

    verify(query).where(combined);
  }

  private QueryPlan<Object> plan(GroupCondition rootCondition) {
    return new QueryPlan<>(
        Object.class,
        rootCondition,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Sort.unsorted(),
        false);
  }
}
