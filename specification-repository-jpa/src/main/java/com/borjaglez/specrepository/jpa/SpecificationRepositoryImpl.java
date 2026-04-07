package com.borjaglez.specrepository.jpa;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.format.support.DefaultFormattingConversionService;

import com.borjaglez.specrepository.core.AggregateSelection;
import com.borjaglez.specrepository.core.FieldSelection;
import com.borjaglez.specrepository.core.JoinMode;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.jpa.support.AssociationRegistry;
import com.borjaglez.specrepository.jpa.support.DefaultOperatorHandlers;
import com.borjaglez.specrepository.jpa.support.DefaultValueConverters;
import com.borjaglez.specrepository.jpa.support.OperatorRegistry;
import com.borjaglez.specrepository.jpa.support.PathResolver;
import com.borjaglez.specrepository.jpa.support.QueryPlanSpecificationFactory;
import com.borjaglez.specrepository.jpa.support.ValueConversionService;

public class SpecificationRepositoryImpl<T, ID extends Serializable>
    extends SimpleJpaRepository<T, ID> implements SpecificationRepository<T, ID> {

  private final EntityManager entityManager;
  private final PathResolver pathResolver;
  private final QueryPlanSpecificationFactory specificationFactory;

  public SpecificationRepositoryImpl(
      JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
    super(entityInformation, entityManager);
    this.entityManager = entityManager;
    this.pathResolver = new PathResolver();
    this.specificationFactory =
        new QueryPlanSpecificationFactory(
            new OperatorRegistry(DefaultOperatorHandlers.defaults()),
            new ValueConversionService(
                new DefaultFormattingConversionService(),
                List.of(
                    DefaultValueConverters.localDateConverter(),
                    DefaultValueConverters.localDateTimeConverter(),
                    DefaultValueConverters.datePassthroughConverter())),
            pathResolver);
  }

  @Override
  public SpecificationExecutableQuery<T> query() {
    return new SpecificationExecutableQuery<>(getDomainClass(), this);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<T> findAll(QueryPlan<T> plan) {
    if (plan.hasSelections()) {
      return (List<T>) executeProjectedQuery(plan, null);
    }
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<T> query = builder.createQuery(getDomainClass());
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(plan).toPredicate(root, query, builder);
    query.select(root);
    if (plan.sort().isSorted()) {
      query.orderBy(QueryUtils.toOrders(plan.sort(), root, builder));
    }
    return entityManager.createQuery(query).getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Page<T> findAll(QueryPlan<T> plan, Pageable pageable) {
    if (plan.hasSelections()) {
      List<?> content = executeProjectedQuery(plan, pageable);
      return new PageImpl<>((List<T>) content, pageable, countSelectedRows(plan));
    }
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<T> query = builder.createQuery(getDomainClass());
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(plan).toPredicate(root, query, builder);
    query.select(root);
    if (pageable.getSort().isSorted()) {
      query.orderBy(QueryUtils.toOrders(pageable.getSort(), root, builder));
    } else if (plan.sort().isSorted()) {
      query.orderBy(QueryUtils.toOrders(plan.sort(), root, builder));
    }
    TypedQuery<T> typedQuery = entityManager.createQuery(query);
    typedQuery.setFirstResult((int) pageable.getOffset());
    typedQuery.setMaxResults(pageable.getPageSize());
    return new PageImpl<>(typedQuery.getResultList(), pageable, count(plan));
  }

  @Override
  public Optional<T> findOne(QueryPlan<T> plan) {
    List<T> results = findAll(plan);
    return results.stream().findFirst();
  }

  @Override
  public long count(QueryPlan<T> plan) {
    if (!plan.groupBy().isEmpty()) {
      return countGrouped(plan);
    }

    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(plan).toPredicate(root, query, builder);
    query.select(plan.distinct() ? builder.countDistinct(root) : builder.count(root));
    return entityManager.createQuery(query).getSingleResult();
  }

  private long countSelectedRows(QueryPlan<T> plan) {
    if (!plan.hasAggregates()) {
      return count(plan);
    }
    return plan.groupBy().isEmpty() ? 1L : countGrouped(plan);
  }

  private long countGrouped(QueryPlan<T> plan) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(plan).toPredicate(root, query, builder);
    query.select(builder.literal(1L));
    return entityManager.createQuery(query).getResultList().size();
  }

  private List<?> executeProjectedQuery(QueryPlan<T> plan, Pageable pageable) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<?> query =
        plan.selections().size() == 1
            ? builder.createQuery(Object.class)
            : builder.createQuery(Object[].class);
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(plan).toPredicate(root, query, builder);
    applyProjection(plan, root, query);
    applySort(plan, pageable, builder, root, query);

    TypedQuery<?> typedQuery = entityManager.createQuery(query);
    if (pageable != null) {
      typedQuery.setFirstResult((int) pageable.getOffset());
      typedQuery.setMaxResults(pageable.getPageSize());
    }
    return typedQuery.getResultList();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void applyProjection(QueryPlan<T> plan, Root<T> root, CriteriaQuery<?> query) {
    AssociationRegistry registry = new AssociationRegistry();
    List<Selection<?>> projections = new ArrayList<>();
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    plan.selections()
        .forEach(selection -> projections.add(toSelection(selection, builder, root, registry)));
    CriteriaQuery rawQuery = query;

    if (projections.size() == 1) {
      rawQuery.select(projections.getFirst());
      return;
    }
    rawQuery.multiselect(projections);
  }

  private Selection<?> toSelection(
      com.borjaglez.specrepository.core.Selection selection,
      CriteriaBuilder builder,
      Root<T> root,
      AssociationRegistry registry) {
    if (selection instanceof FieldSelection fieldSelection) {
      return pathResolver.resolve(root, registry, fieldSelection.field(), JoinMode.LEFT);
    }
    AggregateSelection aggregateSelection = (AggregateSelection) selection;
    Path<?> path = pathResolver.resolve(root, registry, aggregateSelection.field(), JoinMode.LEFT);
    return toAggregateExpression(builder, aggregateSelection, path);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Expression<?> toAggregateExpression(
      CriteriaBuilder builder, AggregateSelection selection, Path<?> path) {
    return switch (selection.function()) {
      case SUM -> builder.sum(asNumberExpression(path, selection));
      case AVG -> builder.avg(asNumberExpression(path, selection));
      case MIN -> minExpression(builder, path);
      case MAX -> maxExpression(builder, path);
      case COUNT -> builder.count(path);
    };
  }

  @SuppressWarnings("unchecked")
  private Expression<? extends Number> asNumberExpression(
      Path<?> path, AggregateSelection selection) {
    if (!Number.class.isAssignableFrom(path.getJavaType())) {
      throw new IllegalArgumentException(
          selection.function() + " requires a numeric field: " + selection.field());
    }
    return (Expression<? extends Number>) path;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Expression<?> minExpression(CriteriaBuilder builder, Path<?> path) {
    if (Number.class.isAssignableFrom(path.getJavaType())) {
      return builder.min((Expression<? extends Number>) path);
    }
    if (Comparable.class.isAssignableFrom(path.getJavaType())) {
      return builder.least((Expression<? extends Comparable>) path);
    }
    throw new IllegalArgumentException("MIN requires a comparable field: " + path.getJavaType());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Expression<?> maxExpression(CriteriaBuilder builder, Path<?> path) {
    if (Number.class.isAssignableFrom(path.getJavaType())) {
      return builder.max((Expression<? extends Number>) path);
    }
    if (Comparable.class.isAssignableFrom(path.getJavaType())) {
      return builder.greatest((Expression<? extends Comparable>) path);
    }
    throw new IllegalArgumentException("MAX requires a comparable field: " + path.getJavaType());
  }

  private void applySort(
      QueryPlan<T> plan,
      Pageable pageable,
      CriteriaBuilder builder,
      Root<T> root,
      CriteriaQuery<?> query) {
    if (pageable != null && pageable.getSort().isSorted()) {
      query.orderBy(QueryUtils.toOrders(pageable.getSort(), root, builder));
      return;
    }
    if (plan.sort().isSorted()) {
      query.orderBy(QueryUtils.toOrders(plan.sort(), root, builder));
    }
  }
}
