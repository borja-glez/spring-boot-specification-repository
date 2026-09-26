package com.borjaglez.specrepository.jpa;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.Attribute;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import com.borjaglez.specrepository.core.AggregateSelection;
import com.borjaglez.specrepository.core.FetchInstruction;
import com.borjaglez.specrepository.core.FieldSelection;
import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.JoinMode;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.jpa.support.AggregateExpressionFactory;
import com.borjaglez.specrepository.jpa.support.AssociationRegistry;
import com.borjaglez.specrepository.jpa.support.PathResolver;
import com.borjaglez.specrepository.jpa.support.QueryPlanSpecificationFactory;
import com.borjaglez.specrepository.jpa.support.SpecificationRepositoryConfiguration;

public class SpecificationRepositoryImpl<T, ID extends Serializable>
    extends SimpleJpaRepository<T, ID> implements SpecificationRepository<T, ID> {

  private final JpaEntityInformation<T, ?> entityInformation;
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
    this.entityInformation = entityInformation;
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
    return fetchEntities(plan, 0);
  }

  /** The entities of the plan, at most {@code limit} of them ({@code 0}: all). */
  private List<T> fetchEntities(QueryPlan<T> plan, int limit) {
    return fetchEntityWindow(plan, plan.sort(), 0, limit);
  }

  /**
   * The entities of the plan in the given order, skipping {@code offset} of them and returning at
   * most {@code limit} ({@code 0}: all).
   *
   * <p>A limit over a fetched collection cannot be applied by the database, since each root takes
   * one row per element: Hibernate would read every row and paginate in memory (or fail, with
   * {@code hibernate.query.fail_on_pagination_over_collection_fetch}). Then the window is taken
   * over the root ids first, and a second query loads those roots with their fetches.
   */
  private List<T> fetchEntityWindow(QueryPlan<T> plan, Sort sort, long offset, int limit) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<T> query = builder.createQuery(getDomainClass());
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(plan).toPredicate(root, query, builder);
    query.select(root);
    if (sort.isSorted()) {
      query.orderBy(QueryUtils.toOrders(sort, root, builder));
    }
    keepDistinctWorkable(plan, query, sort);
    if (limit > 0 && fetchesACollection(root) && hasSingleBasicId()) {
      return fetchEntitiesByIds(plan, sort, offset, limit, builder, query, root);
    }
    TypedQuery<T> typedQuery = entityManager.createQuery(query);
    if (limit > 0) {
      typedQuery.setFirstResult((int) offset);
      typedQuery.setMaxResults(limit);
    }
    return typedQuery.getResultList();
  }

  /**
   * Runs the entity query restricted to the ids of the requested window, which a query without
   * fetches reads with the offset and limit, and returns the entities in the order of those ids.
   */
  private List<T> fetchEntitiesByIds(
      QueryPlan<T> plan,
      Sort sort,
      long offset,
      int limit,
      CriteriaBuilder builder,
      CriteriaQuery<T> query,
      Root<T> root) {
    List<Object> ids = fetchRootIds(plan, sort, offset, limit, builder);
    if (ids.isEmpty()) {
      return List.of();
    }
    Predicate inWindow = root.get(idAttributeName()).in(ids);
    Predicate restriction = query.getRestriction();
    query.where(restriction == null ? inWindow : builder.and(restriction, inWindow));
    // The ids already carry the order, and without an ORDER BY the query never breaks the
    // PostgreSQL rule for SELECT DISTINCT.
    query.orderBy(List.of());
    Map<Object, T> byId = new HashMap<>();
    for (T entity : entityManager.createQuery(query).getResultList()) {
      byId.put(entityInformation.getId(entity), entity);
    }
    return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
  }

  /**
   * The ids of the roots in the window: the plan's conditions and joins without its fetches, with
   * the sort. The sort expressions are selected next to the id, so the query stays valid with
   * {@code distinct} on databases such as PostgreSQL that only order a {@code SELECT DISTINCT} by
   * selected columns. An inner fetch still drops the roots without the association, so it becomes
   * an inner join here.
   */
  private List<Object> fetchRootIds(
      QueryPlan<T> plan, Sort sort, long offset, int limit, CriteriaBuilder builder) {
    CriteriaQuery<Tuple> query = builder.createTupleQuery();
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(withoutFetches(plan)).toPredicate(root, query, builder);
    for (FetchInstruction fetch : plan.fetches()) {
      if (fetch.mode() == JoinMode.INNER) {
        pathResolver.join(root, new AssociationRegistry(), fetch.path(), JoinMode.INNER);
      }
    }
    List<Selection<?>> selections = new ArrayList<>();
    selections.add(root.get(idAttributeName()));
    if (sort.isSorted()) {
      List<Order> orders = QueryUtils.toOrders(sort, root, builder);
      orders.forEach(order -> selections.add(order.getExpression()));
      query.orderBy(orders);
    }
    query.multiselect(selections);
    query.distinct(plan.distinct() || joinsACollection(root));
    TypedQuery<Tuple> typedQuery = entityManager.createQuery(query);
    typedQuery.setFirstResult((int) offset);
    typedQuery.setMaxResults(limit);
    // A sort over a collection returns a root once per element: each id is kept once, in the
    // position of its first row.
    return typedQuery.getResultList().stream().map(row -> row.get(0)).distinct().toList();
  }

  private static <X> QueryPlan<X> withoutFetches(QueryPlan<X> plan) {
    return new QueryPlan<>(
        plan.entityType(),
        plan.rootCondition(),
        plan.joins(),
        List.of(),
        plan.projections(),
        plan.selections(),
        plan.projectionType(),
        plan.groupBy(),
        plan.having(),
        plan.sort(),
        plan.distinct(),
        plan.allowedFieldsPolicy());
  }

  /**
   * Whether the ids can be matched with {@code in}. Entities with a composite id ({@code @IdClass}
   * or {@code @EmbeddedId}) keep the single query, paginated in memory by Hibernate.
   */
  private boolean hasSingleBasicId() {
    return !entityInformation.hasCompositeId()
        && entityInformation.getIdAttribute().getPersistentAttributeType()
            == Attribute.PersistentAttributeType.BASIC;
  }

  private String idAttributeName() {
    return entityInformation.getIdAttribute().getName();
  }

  private static boolean fetchesACollection(FetchParent<?, ?> parent) {
    for (Fetch<?, ?> fetch : parent.getFetches()) {
      if (fetch.getAttribute().isCollection() || fetchesACollection(fetch)) {
        return true;
      }
    }
    return false;
  }

  private static boolean joinsACollection(From<?, ?> from) {
    for (Join<?, ?> join : from.getJoins()) {
      if (join.getAttribute().isCollection() || joinsACollection(join)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The specification asks for {@code distinct} when a filter crosses a collection. Databases such
   * as PostgreSQL reject {@code SELECT DISTINCT} ordered by a column outside the select list, which
   * an order on an association is: then the query keeps its repeated roots, as it did before.
   */
  private static void keepDistinctWorkable(QueryPlan<?> plan, CriteriaQuery<?> query, Sort sort) {
    if (!plan.distinct()
        && query.isDistinct()
        && sort.stream().anyMatch(order -> order.getProperty().contains("."))) {
      query.distinct(false);
    }
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
    List<P> fetched = executeProjectedQuery(plan, pageable, requiredProjectionType(plan), 1);
    return toSlice(fetched, pageable);
  }

  private List<T> fetchEntityPage(QueryPlan<T> plan, Pageable pageable, int extraLimit) {
    Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : plan.sort();
    return fetchEntityWindow(plan, sort, pageable.getOffset(), pageable.getPageSize() + extraLimit);
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
    // Only the first row is read: the database stops there instead of sending every match.
    List<T> results;
    if (plan.projectionType() != null) {
      results = (List<T>) executeProjectedQuery(plan, null, requiredProjectionType(plan), 1);
    } else if (plan.hasSelections()) {
      results = (List<T>) executeProjectedQuery(plan, null, 1);
    } else {
      results = fetchEntities(plan, 1);
    }
    return results.stream().findFirst();
  }

  @Override
  public <P> Optional<P> findOneProjected(QueryPlan<T> plan) {
    List<P> results = executeProjectedQuery(plan, null, requiredProjectionType(plan), 1);
    return results.stream().findFirst();
  }

  @Override
  public List<GroupedRow> findAllGrouped(QueryPlan<T> plan) {
    if (!plan.hasSelections()) {
      throw new IllegalStateException(
          "findAllGrouped requires at least one select() or aggregate selection");
    }
    List<String> columns = new ArrayList<>();
    for (com.borjaglez.specrepository.core.Selection selection : plan.selections()) {
      if (selection instanceof FieldSelection field) {
        columns.add(field.field());
      } else {
        columns.add(((AggregateSelection) selection).columnName());
      }
    }
    List<?> rawRows = executeProjectedQuery(plan, null);
    List<GroupedRow> rows = new ArrayList<>(rawRows.size());
    boolean singleColumn = plan.selections().size() == 1;
    for (Object raw : rawRows) {
      Object[] values = singleColumn ? new Object[] {raw} : (Object[]) raw;
      rows.add(new GroupedRow(columns, values));
    }
    return rows;
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
    // The specification asks for distinct when it must count each root once; for a count that
    // means count(distinct root), not a distinct count.
    boolean distinct = plan.distinct() || query.isDistinct();
    query.distinct(false);
    query.select(distinct ? builder.countDistinct(root) : builder.count(root));
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
    return executeProjectedQuery(plan, pageable, resultType, 0);
  }

  private <P> List<P> executeProjectedQuery(
      QueryPlan<T> plan, Pageable pageable, Class<P> resultType, int extraLimit) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<P> query = builder.createQuery(resultType);
    Root<T> root = query.from(getDomainClass());
    specificationFactory.create(plan).toPredicate(root, query, builder);
    applyProjection(plan, builder, root, query, resultType);
    applySort(plan, pageable, builder, root, query);

    TypedQuery<P> typedQuery = entityManager.createQuery(query);
    if (pageable != null) {
      typedQuery.setFirstResult((int) pageable.getOffset());
      typedQuery.setMaxResults(pageable.getPageSize() + extraLimit);
    } else if (extraLimit > 0) {
      typedQuery.setMaxResults(extraLimit);
    }
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
    return AggregateExpressionFactory.create(
        builder, aggregateSelection.function(), aggregateSelection.field(), path);
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
