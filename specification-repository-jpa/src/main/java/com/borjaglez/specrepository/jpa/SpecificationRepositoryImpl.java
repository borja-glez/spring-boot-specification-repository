package com.borjaglez.specrepository.jpa;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import com.borjaglez.specrepository.core.AggregateSelection;
import com.borjaglez.specrepository.core.FieldSelection;
import com.borjaglez.specrepository.core.JoinMode;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.jpa.support.AssociationRegistry;
import com.borjaglez.specrepository.jpa.support.PathResolver;
import com.borjaglez.specrepository.jpa.support.QueryPlanSpecificationFactory;
import com.borjaglez.specrepository.jpa.support.SpecificationRepositoryConfiguration;

public class SpecificationRepositoryImpl<T, ID extends Serializable>
    extends SimpleJpaRepository<T, ID> implements SpecificationRepository<T, ID> {

  private final EntityManager entityManager;
  private final PathResolver pathResolver;
  private final QueryPlanSpecificationFactory specificationFactory;

  public SpecificationRepositoryImpl(
      JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
    this(
        entityInformation,
        entityManager,
        SpecificationRepositoryConfiguration.defaultConfiguration());
  }

  public SpecificationRepositoryImpl(
      JpaEntityInformation<T, ?> entityInformation,
      EntityManager entityManager,
      SpecificationRepositoryConfiguration configuration) {
    super(entityInformation, entityManager);
    this.entityManager = entityManager;
    SpecificationRepositoryConfiguration repositoryConfiguration =
        Objects.requireNonNull(configuration, "configuration must not be null");
    this.pathResolver = repositoryConfiguration.pathResolver();
    this.specificationFactory = repositoryConfiguration.specificationFactory();
  }

  @Override
  public SpecificationExecutableQuery<T> query() {
    return new SpecificationExecutableQuery<>(getDomainClass(), this);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<T> findAll(QueryPlan<T> plan) {
    if (plan.projectionType() != null) {
      return (List<T>) findAllProjected(plan);
    }
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
  public <P> List<P> findAllProjected(QueryPlan<T> plan) {
    return executeProjectedQuery(plan, null, requiredProjectionType(plan));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Page<T> findAll(QueryPlan<T> plan, Pageable pageable) {
    if (plan.projectionType() != null) {
      return (Page<T>) findAllProjected(plan, pageable);
    }
    if (plan.hasSelections()) {
      List<?> content = executeProjectedQuery(plan, pageable, 0);
      return new PageImpl<>((List<T>) content, pageable, countSelectedRows(plan));
    }
    List<T> content = fetchEntityPage(plan, pageable, 0);
    return new PageImpl<>(content, pageable, count(plan));
  }

  @Override
  public <P> Page<P> findAllProjected(QueryPlan<T> plan, Pageable pageable) {
    List<P> content = executeProjectedQuery(plan, pageable, requiredProjectionType(plan));
    return new PageImpl<>(content, pageable, countSelectedRows(plan));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Slice<T> findSlice(QueryPlan<T> plan, Pageable pageable) {
    if (plan.projectionType() != null) {
      return (Slice<T>) findSliceProjected(plan, pageable);
    }
    if (plan.hasSelections()) {
      List<?> fetched = executeProjectedQuery(plan, pageable, 1);
      return toSlice((List<T>) fetched, pageable);
    }
    List<T> fetched = fetchEntityPage(plan, pageable, 1);
    return toSlice(fetched, pageable);
  }

  @Override
  public <P> Slice<P> findSliceProjected(QueryPlan<T> plan, Pageable pageable) {
    List<P> fetched = executeProjectedSliceQuery(plan, pageable, requiredProjectionType(plan));
    return toSlice(fetched, pageable);
  }

  private List<T> fetchEntityPage(QueryPlan<T> plan, Pageable pageable, int extraLimit) {
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
    typedQuery.setMaxResults(pageable.getPageSize() + extraLimit);
    return typedQuery.getResultList();
  }

  private <R> Slice<R> toSlice(List<R> fetched, Pageable pageable) {
    int pageSize = pageable.getPageSize();
    boolean hasNext = fetched.size() > pageSize;
    List<R> content = hasNext ? new ArrayList<>(fetched.subList(0, pageSize)) : fetched;
    return new SliceImpl<>(content, pageable, hasNext);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<T> findOne(QueryPlan<T> plan) {
    List<T> results =
        plan.projectionType() != null
            ? (List<T>) executeProjectedQuery(plan, null, requiredProjectionType(plan), 1)
            : findAll(plan);
    return results.stream().findFirst();
  }

  @Override
  public <P> Optional<P> findOneProjected(QueryPlan<T> plan) {
    List<P> results = executeProjectedQuery(plan, null, requiredProjectionType(plan), 1);
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
    return executeProjectedQuery(plan, pageable, 0);
  }

  private List<?> executeProjectedQuery(QueryPlan<T> plan, Pageable pageable, int extraLimit) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<?> query =
        plan.selections().size() == 1
            ? builder.createQuery(Object.class)
            : builder.createQuery(Object[].class);
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(plan).toPredicate(root, query, builder);
    applyProjection(plan, builder, root, query);
    applySort(plan, pageable, builder, root, query);

    TypedQuery<?> typedQuery = entityManager.createQuery(query);
    if (pageable != null) {
      typedQuery.setFirstResult((int) pageable.getOffset());
      typedQuery.setMaxResults(pageable.getPageSize() + extraLimit);
    }
    return typedQuery.getResultList();
  }

  private <P> List<P> executeProjectedQuery(
      QueryPlan<T> plan, Pageable pageable, Class<P> resultType) {
    return executeProjectedQuery(plan, pageable, resultType, null);
  }

  private <P> List<P> executeProjectedQuery(
      QueryPlan<T> plan, Pageable pageable, Class<P> resultType, Integer maxResults) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<P> query = builder.createQuery(resultType);
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(plan).toPredicate(root, query, builder);
    applyProjection(plan, builder, root, query, resultType);
    applySort(plan, pageable, builder, root, query);

    TypedQuery<P> typedQuery = entityManager.createQuery(query);
    if (pageable != null) {
      typedQuery.setFirstResult((int) pageable.getOffset());
      typedQuery.setMaxResults(pageable.getPageSize());
    } else if (maxResults != null) {
      typedQuery.setMaxResults(maxResults);
    }
    return typedQuery.getResultList();
  }

  private <P> List<P> executeProjectedSliceQuery(
      QueryPlan<T> plan, Pageable pageable, Class<P> resultType) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<P> query = builder.createQuery(resultType);
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(plan).toPredicate(root, query, builder);
    applyProjection(plan, builder, root, query, resultType);
    applySort(plan, pageable, builder, root, query);

    TypedQuery<P> typedQuery = entityManager.createQuery(query);
    typedQuery.setFirstResult((int) pageable.getOffset());
    typedQuery.setMaxResults(pageable.getPageSize() + 1);
    return typedQuery.getResultList();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void applyProjection(
      QueryPlan<T> plan, CriteriaBuilder builder, Root<T> root, CriteriaQuery<?> query) {
    AssociationRegistry registry = new AssociationRegistry();
    List<Selection<?>> projections = new ArrayList<>();
    plan.selections()
        .forEach(selection -> projections.add(toSelection(selection, builder, root, registry)));
    CriteriaQuery rawQuery = query;

    if (projections.size() == 1) {
      rawQuery.select(projections.getFirst());
      return;
    }
    rawQuery.multiselect(projections);
  }

  private <P> void applyProjection(
      QueryPlan<T> plan,
      CriteriaBuilder builder,
      Root<T> root,
      CriteriaQuery<P> query,
      Class<P> projectionType) {
    AssociationRegistry registry = new AssociationRegistry();
    List<Selection<?>> projections = new ArrayList<>();
    plan.selections()
        .forEach(selection -> projections.add(toSelection(selection, builder, root, registry)));
    query.select(builder.construct(projectionType, projections.toArray(Selection[]::new)));
  }

  @SuppressWarnings("unchecked")
  static <P> Class<P> requiredProjectionType(QueryPlan<?> plan) {
    if (plan.projectionType() == null) {
      throw new IllegalStateException("projectionType must not be null");
    }
    return (Class<P>) plan.projectionType();
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
      case MIN -> {
        if (Number.class.isAssignableFrom(path.getJavaType())) {
          yield builder.min((Expression<? extends Number>) path);
        }
        if (Comparable.class.isAssignableFrom(path.getJavaType())) {
          yield builder.least((Expression<? extends Comparable>) path);
        }
        throw new IllegalArgumentException(
            "MIN requires a comparable field: " + path.getJavaType());
      }
      case MAX -> {
        if (Number.class.isAssignableFrom(path.getJavaType())) {
          yield builder.max((Expression<? extends Number>) path);
        }
        if (Comparable.class.isAssignableFrom(path.getJavaType())) {
          yield builder.greatest((Expression<? extends Comparable>) path);
        }
        throw new IllegalArgumentException(
            "MAX requires a comparable field: " + path.getJavaType());
      }
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
