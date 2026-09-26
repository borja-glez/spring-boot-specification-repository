package com.borjaglez.specrepository.jpa.support;

import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.ManagedType;

import org.springframework.core.convert.ConversionException;
import org.springframework.data.jpa.domain.Specification;

import com.borjaglez.specrepository.core.CorrelationMode;
import com.borjaglez.specrepository.core.CorrelationPair;
import com.borjaglez.specrepository.core.FetchInstruction;
import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.HavingCondition;
import com.borjaglez.specrepository.core.InvalidFilterException;
import com.borjaglez.specrepository.core.InvalidFilterValueException;
import com.borjaglez.specrepository.core.JoinInstruction;
import com.borjaglez.specrepository.core.JoinMode;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.SubqueryCondition;
import com.borjaglez.specrepository.jpa.spi.OperatorContext;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;

public class QueryPlanSpecificationFactory {
  private final OperatorRegistry operatorRegistry;
  private final ValueConversionService valueConversionService;

  /** Operators that test the collection itself, not its elements. */
  private static final Set<FilterOperator> WHOLE_COLLECTION_OPERATORS =
      Set.of(Operators.IS_EMPTY, Operators.IS_NOT_EMPTY);

  /**
   * Negative operators and their positive operator. On a collection path a negative operator means
   * "no element matches the positive operator".
   */
  private static final Map<FilterOperator, FilterOperator> NEGATED_OPERATORS =
      Map.of(
          Operators.NOT_EQUALS, Operators.EQUALS,
          Operators.NOT_IN, Operators.IN,
          Operators.NOT_CONTAINS, Operators.CONTAINS);

  /**
   * Field that names the element itself in an {@code exists} / {@code notExists} over a collection
   * of basic values, such as {@code exists("tags", sub -> sub.where("value", EQUALS, "vip"))}.
   */
  static final String BASIC_ELEMENT_FIELD = "value";

  private final PathResolver pathResolver;

  public QueryPlanSpecificationFactory(
      OperatorRegistry operatorRegistry,
      ValueConversionService valueConversionService,
      PathResolver pathResolver) {
    this.operatorRegistry = operatorRegistry;
    this.valueConversionService = valueConversionService;
    this.pathResolver = pathResolver;
  }

  public OperatorRegistry operatorRegistry() {
    return operatorRegistry;
  }

  public ValueConversionService valueConversionService() {
    return valueConversionService;
  }

  public PathResolver pathResolver() {
    return pathResolver;
  }

  public <T> Specification<T> create(QueryPlan<T> plan) {
    plan.allowedFieldsPolicy().validate(plan);
    // Grouped and projected queries keep every condition on the shared join, which is the join
    // their groupBy and select read.
    CollectionConditions collections =
        collectionConditions(
            !plan.hasSelections() && plan.groupBy().isEmpty(),
            new GroupCondition(
                LogicalOperator.AND, List.of(plan.rootCondition(), plan.serverCondition())));
    return (root, query, criteriaBuilder) -> {
      AssociationRegistry registry = new AssociationRegistry();
      // Fetches first: Hibernate's plain joins also implement Fetch, so a fetch requested on a
      // path that was already joined would reuse the join and never load the association.
      // Created in this order, the join reuses the real fetch instead.
      applyFetches(root, query, registry, plan.fetches());
      applyJoins(root, registry, plan.joins());
      applyGrouping(root, query, registry, plan.groupBy());
      applyHaving(root, query, registry, plan.having(), criteriaBuilder);

      Predicate predicate =
          and(
              criteriaBuilder,
              toPredicate(
                  plan.rootCondition(),
                  root,
                  root.getModel(),
                  query,
                  criteriaBuilder,
                  registry,
                  collections),
              toPredicate(
                  plan.serverCondition(),
                  root,
                  root.getModel(),
                  query,
                  criteriaBuilder,
                  registry,
                  collections));
      if (predicate != null) {
        query.where(predicate);
      }
      if (plan.distinct() || (joinsACollection(root) && countsRoots(plan, query, root))) {
        query.distinct(true);
      }
      return predicate;
    };
  }

  /**
   * The client conditions ANDed with the server conditions, so an OR in the client conditions
   * cannot widen the server ones. Either may be {@code null} when it has no conditions.
   */
  private static Predicate and(
      CriteriaBuilder criteriaBuilder, Predicate client, Predicate server) {
    if (server == null) {
      return client;
    }
    if (client == null) {
      return server;
    }
    return criteriaBuilder.and(client, server);
  }

  /**
   * Whether the query returns (or counts) root entities: a join over a collection repeats a root
   * once per matching element, so those queries need {@code distinct} to return and count each root
   * once. Projections and grouped queries keep their rows as they are.
   */
  private static boolean countsRoots(QueryPlan<?> plan, CriteriaQuery<?> query, Root<?> root) {
    if (!plan.groupBy().isEmpty()) {
      return false;
    }
    Class<?> resultType = query.getResultType();
    return resultType.equals(root.getJavaType()) || Long.class.equals(resultType);
  }

  private static boolean joinsACollection(From<?, ?> from) {
    for (Join<?, ?> join : from.getJoins()) {
      if (join.getAttribute().isCollection() || joinsACollection(join)) {
        return true;
      }
    }
    return false;
  }

  private void applyJoins(
      Root<?> root, AssociationRegistry registry, List<JoinInstruction> instructions) {
    instructions.forEach(
        instruction -> pathResolver.join(root, registry, instruction.path(), instruction.mode()));
  }

  private void applyFetches(
      Root<?> root,
      CriteriaQuery<?> query,
      AssociationRegistry registry,
      List<FetchInstruction> instructions) {
    if (Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType())) {
      applyInnerFetchesAsJoins(root, registry, instructions);
      return;
    }
    instructions.forEach(
        instruction -> pathResolver.fetch(root, registry, instruction.path(), instruction.mode()));
  }

  /**
   * A count cannot fetch, but an inner fetch still drops the roots without the association: it
   * becomes an inner join, so the count matches the rows. When it crosses a collection the query is
   * then counted with {@code count(distinct root)}. Left fetches never change the number of roots
   * and are left out.
   */
  private void applyInnerFetchesAsJoins(
      Root<?> root, AssociationRegistry registry, List<FetchInstruction> instructions) {
    for (FetchInstruction instruction : instructions) {
      if (instruction.mode() == JoinMode.INNER) {
        pathResolver.join(root, registry, instruction.path(), JoinMode.INNER);
      }
    }
  }

  private void applyGrouping(
      Root<?> root, CriteriaQuery<?> query, AssociationRegistry registry, List<String> fields) {
    if (fields.isEmpty()) {
      return;
    }
    List<Expression<?>> expressions = new ArrayList<>();
    fields.forEach(
        field -> expressions.add(pathResolver.resolve(root, registry, field, JoinMode.LEFT)));
    query.groupBy(expressions);
  }

  private void applyHaving(
      Root<?> root,
      CriteriaQuery<?> query,
      AssociationRegistry registry,
      List<HavingCondition> havingConditions,
      CriteriaBuilder criteriaBuilder) {
    if (havingConditions.isEmpty()) {
      return;
    }
    List<Predicate> predicates = new ArrayList<>();
    for (HavingCondition condition : havingConditions) {
      predicates.add(buildHavingPredicate(root, registry, condition, criteriaBuilder));
    }
    query.having(criteriaBuilder.and(predicates.toArray(Predicate[]::new)));
  }

  private Predicate buildHavingPredicate(
      Root<?> root, AssociationRegistry registry, HavingCondition condition, CriteriaBuilder cb) {
    Path<?> path = pathResolver.resolve(root, registry, condition.field(), JoinMode.LEFT);
    Expression<?> aggregate =
        AggregateExpressionFactory.create(cb, condition.function(), condition.field(), path);
    Class<?> targetType =
        AggregateExpressionFactory.resultType(condition.function(), path.getJavaType());
    return havingPredicate(cb, aggregate, targetType, condition);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Predicate havingPredicate(
      CriteriaBuilder cb, Expression<?> aggregate, Class<?> targetType, HavingCondition condition) {
    FilterOperator operator = condition.operator();
    if (Operators.IS_NULL.equals(operator)) {
      return cb.isNull(aggregate);
    }
    if (Operators.IS_NOT_NULL.equals(operator)) {
      return cb.isNotNull(aggregate);
    }
    if (Operators.BETWEEN.equals(operator)) {
      List<?> bounds = havingRangeValues(condition.value());
      Object lower = convertValue(condition.field(), bounds.get(0), targetType, operator);
      Object upper = convertValue(condition.field(), bounds.get(1), targetType, operator);
      return cb.between((Expression<Comparable>) aggregate, (Comparable) lower, (Comparable) upper);
    }
    if (!isSupportedComparator(operator)) {
      throw new IllegalArgumentException(
          "Unsupported operator for having clause: " + operator.value());
    }
    Object converted = convert(condition.field(), condition.value(), targetType, operator);
    if (Operators.EQUALS.equals(operator)) {
      return cb.equal(aggregate, converted);
    }
    if (Operators.NOT_EQUALS.equals(operator)) {
      return cb.notEqual(aggregate, converted);
    }
    if (Operators.GREATER_THAN.equals(operator)) {
      return cb.greaterThan((Expression<Comparable>) aggregate, (Comparable) converted);
    }
    if (Operators.GREATER_THAN_OR_EQUAL.equals(operator)) {
      return cb.greaterThanOrEqualTo((Expression<Comparable>) aggregate, (Comparable) converted);
    }
    if (Operators.LESS_THAN.equals(operator)) {
      return cb.lessThan((Expression<Comparable>) aggregate, (Comparable) converted);
    }
    return cb.lessThanOrEqualTo((Expression<Comparable>) aggregate, (Comparable) converted);
  }

  private boolean isSupportedComparator(FilterOperator operator) {
    return Operators.EQUALS.equals(operator)
        || Operators.NOT_EQUALS.equals(operator)
        || Operators.GREATER_THAN.equals(operator)
        || Operators.GREATER_THAN_OR_EQUAL.equals(operator)
        || Operators.LESS_THAN.equals(operator)
        || Operators.LESS_THAN_OR_EQUAL.equals(operator);
  }

  private List<?> havingRangeValues(Object value) {
    if (!(value instanceof Iterable<?> iterable)) {
      throw new IllegalArgumentException("BETWEEN having requires exactly 2 values");
    }
    List<?> values =
        iterable instanceof List<?> list
            ? list
            : java.util.stream.StreamSupport.stream(iterable.spliterator(), false).toList();
    if (values.size() != 2) {
      throw new IllegalArgumentException("BETWEEN having requires exactly 2 values");
    }
    return values;
  }

  private Predicate toPredicate(
      GroupCondition condition,
      From<?, ?> from,
      ManagedType<?> fromType,
      CommonAbstractCriteria parent,
      CriteriaBuilder criteriaBuilder,
      AssociationRegistry registry,
      CollectionConditions collections) {
    List<Predicate> predicates = new ArrayList<>();
    for (QueryCondition queryCondition : condition.conditions()) {
      if (queryCondition instanceof GroupCondition groupCondition) {
        Predicate nested =
            toPredicate(
                groupCondition, from, fromType, parent, criteriaBuilder, registry, collections);
        if (nested != null) {
          predicates.add(nested);
        }
        continue;
      }
      if (queryCondition instanceof SubqueryCondition subqueryCondition) {
        predicates.add(
            translateSubquery(
                subqueryCondition, from, fromType, parent, criteriaBuilder, registry));
        continue;
      }
      PredicateCondition predicateCondition = (PredicateCondition) queryCondition;
      if (collections.ownElement(predicateCondition)
          && !registry.isBasicElement(from)
          && pathResolver.crossesCollection(fromType, predicateCondition.field())) {
        predicates.add(
            ownElementPredicate(predicateCondition, from, fromType, parent, criteriaBuilder));
        continue;
      }
      OperatorHandler handler = handlerFor(predicateCondition);
      Path<?> path =
          resolvePath(
              from,
              fromType,
              registry,
              predicateCondition.field(),
              !WHOLE_COLLECTION_OPERATORS.contains(predicateCondition.operator()));
      predicates.add(predicate(predicateCondition, handler, path, criteriaBuilder));
    }

    if (predicates.isEmpty()) {
      return null;
    }

    Predicate[] predicateArray = predicates.toArray(Predicate[]::new);
    return condition.logicalOperator() == LogicalOperator.OR
        ? criteriaBuilder.or(predicateArray)
        : criteriaBuilder.and(predicateArray);
  }

  private Predicate predicate(
      PredicateCondition condition,
      OperatorHandler handler,
      Path<?> path,
      CriteriaBuilder criteriaBuilder) {
    Object convertedValue =
        convert(condition.field(), condition.value(), path.getJavaType(), condition.operator());
    Predicate predicate =
        handler.create(
            new OperatorContext(criteriaBuilder, path, convertedValue, condition.ignoreCase()));
    return condition.includeNulls()
        ? criteriaBuilder.or(predicate, criteriaBuilder.isNull(path))
        : predicate;
  }

  /**
   * A condition on a collection path tested on an element of its own, instead of the element of the
   * shared join: {@code EXISTS} an element that matches it or, for a negative operator, {@code NOT
   * EXISTS} an element that matches the positive operator, so roots without elements match too and
   * {@code includeNulls} adds nothing.
   */
  private Predicate ownElementPredicate(
      PredicateCondition condition,
      From<?, ?> from,
      ManagedType<?> fromType,
      CommonAbstractCriteria parent,
      CriteriaBuilder criteriaBuilder) {
    FilterOperator positive = NEGATED_OPERATORS.get(condition.operator());
    PredicateCondition matching =
        positive == null
            ? condition
            : new PredicateCondition(
                condition.field(), positive, condition.value(), condition.ignoreCase(), false);
    OperatorHandler handler = handlerFor(matching);
    Subquery<Integer> sub = parent.subquery(Integer.class);
    Path<?> element =
        pathResolver.resolve(
            correlateOuter(sub, from),
            fromType,
            new AssociationRegistry(),
            condition.field(),
            JoinMode.INNER);
    sub.select(criteriaBuilder.literal(1))
        .where(predicate(matching, handler, element, criteriaBuilder));
    Predicate exists = criteriaBuilder.exists(sub);
    return positive == null ? exists : criteriaBuilder.not(exists);
  }

  /**
   * Which conditions on a collection path test an element of their own instead of sharing one join:
   * negative operators, and conditions on a field that is used more than once in the same AND
   * (their closest common group is an AND group). {@code enabled} is {@code false} for grouped and
   * projected queries, which keep every condition on the shared join.
   */
  private record CollectionConditions(boolean enabled, Set<PredicateCondition> repeated) {
    boolean ownElement(PredicateCondition condition) {
      return enabled
          && (NEGATED_OPERATORS.containsKey(condition.operator()) || repeated.contains(condition));
    }
  }

  private static CollectionConditions collectionConditions(
      boolean enabled, GroupCondition condition) {
    // By identity: the same condition written twice is still two conditions.
    Set<PredicateCondition> repeated = Collections.newSetFromMap(new IdentityHashMap<>());
    collectRepeated(condition, repeated);
    return new CollectionConditions(enabled, repeated);
  }

  /**
   * Returns the conditions below {@code condition} that would share a join, by field, and adds to
   * {@code repeated} those whose field is used again under the same AND group. A subquery body is
   * examined on its own when it is translated.
   */
  private static Map<String, List<PredicateCondition>> collectRepeated(
      QueryCondition condition, Set<PredicateCondition> repeated) {
    if (condition instanceof PredicateCondition predicate) {
      FilterOperator operator = predicate.operator();
      boolean sharesJoin =
          !WHOLE_COLLECTION_OPERATORS.contains(operator)
              && !NEGATED_OPERATORS.containsKey(operator);
      return sharesJoin ? Map.of(predicate.field(), List.of(predicate)) : Map.of();
    }
    if (!(condition instanceof GroupCondition group)) {
      return Map.of();
    }
    Map<String, List<List<PredicateCondition>>> byField = new LinkedHashMap<>();
    for (QueryCondition child : group.conditions()) {
      collectRepeated(child, repeated)
          .forEach(
              (field, conditions) ->
                  byField.computeIfAbsent(field, key -> new ArrayList<>()).add(conditions));
    }
    boolean and = group.logicalOperator() == LogicalOperator.AND;
    Map<String, List<PredicateCondition>> below = new LinkedHashMap<>();
    byField.forEach(
        (field, perChild) -> {
          if (and && perChild.size() > 1) {
            perChild.forEach(repeated::addAll);
          }
          below.put(field, perChild.stream().flatMap(List::stream).toList());
        });
    return below;
  }

  /**
   * Converts a filter value to the field's type, element by element for lists, so a failure reports
   * the value that could not be converted.
   */
  private Object convert(String field, Object value, Class<?> targetType, FilterOperator operator) {
    if (!(value instanceof Iterable<?> iterable)) {
      return convertValue(field, value, targetType, operator);
    }
    List<Object> converted = new ArrayList<>();
    for (Object element : iterable) {
      converted.add(convertValue(field, element, targetType, operator));
    }
    return converted;
  }

  /**
   * Converts one value, reporting a conversion failure as an {@link InvalidFilterValueException}.
   * Other runtime exceptions indicate a bug and propagate unchanged.
   */
  private Object convertValue(
      String field, Object value, Class<?> targetType, FilterOperator operator) {
    try {
      return valueConversionService.convert(value, targetType, operator);
    } catch (ConversionException | DateTimeException | IllegalArgumentException e) {
      throw new InvalidFilterValueException(field, value, targetType, e);
    }
  }

  private Predicate translateSubquery(
      SubqueryCondition sc,
      From<?, ?> outer,
      ManagedType<?> outerType,
      CommonAbstractCriteria parent,
      CriteriaBuilder cb,
      AssociationRegistry outerRegistry) {
    return switch (sc.kind()) {
      case EXISTS -> buildExists(sc, outer, outerType, parent, cb, outerRegistry, false);
      case NOT_EXISTS -> buildExists(sc, outer, outerType, parent, cb, outerRegistry, true);
      case IN -> buildIn(sc, outer, outerType, parent, cb, outerRegistry, false);
      case NOT_IN -> buildIn(sc, outer, outerType, parent, cb, outerRegistry, true);
    };
  }

  private Predicate buildExists(
      SubqueryCondition sc,
      From<?, ?> outer,
      ManagedType<?> outerType,
      CommonAbstractCriteria parent,
      CriteriaBuilder cb,
      AssociationRegistry outerRegistry,
      boolean negated) {
    Subquery<Integer> sub = parent.subquery(Integer.class);
    AssociationRegistry subRegistry = new AssociationRegistry();
    SubRootContext context =
        buildSubRoot(sc, outer, outerType, sub, cb, outerRegistry, subRegistry);
    applyBody(sc, cb, sub, context, subRegistry);
    sub.select(cb.literal(1));
    Predicate existsPredicate = cb.exists(sub);
    return negated ? cb.not(existsPredicate) : existsPredicate;
  }

  private <U> Predicate buildIn(
      SubqueryCondition sc,
      From<?, ?> outer,
      ManagedType<?> outerType,
      CommonAbstractCriteria parent,
      CriteriaBuilder cb,
      AssociationRegistry outerRegistry,
      boolean negated) {
    Path<?> outerPath = resolvePath(outer, outerType, outerRegistry, sc.outerField());
    @SuppressWarnings("unchecked")
    Class<U> projectedType = (Class<U>) outerPath.getJavaType();
    Subquery<U> sub = parent.subquery(projectedType);
    AssociationRegistry subRegistry = new AssociationRegistry();
    SubRootContext context =
        buildSubRoot(sc, outer, outerType, sub, cb, outerRegistry, subRegistry);
    applyBody(sc, cb, sub, context, subRegistry);
    Path<?> innerSelect =
        resolvePath(context.subRoot(), context.subRootType(), subRegistry, sc.subSelectField());
    @SuppressWarnings("unchecked")
    Expression<U> innerExpression = (Expression<U>) innerSelect;
    sub.select(innerExpression);
    Predicate inPredicate = outerPath.in(sub);
    return negated ? cb.not(inPredicate) : inPredicate;
  }

  private SubRootContext buildSubRoot(
      SubqueryCondition sc,
      From<?, ?> outer,
      ManagedType<?> outerType,
      Subquery<?> sub,
      CriteriaBuilder cb,
      AssociationRegistry outerRegistry,
      AssociationRegistry subRegistry) {
    if (sc.correlationMode() == CorrelationMode.ASSOCIATION) {
      From<?, ?> correlatedOuter = correlateOuter(sub, outer);
      String[] segments = sc.associationPath().split("\\.");
      From<?, ?> navigated = correlatedOuter;
      ManagedType<?> currentType = outerType;
      for (String segment : segments) {
        Join<?, ?> join = navigated.join(segment, AssociationRegistry.toJoinType(JoinMode.INNER));
        navigated = join;
        currentType = pathResolver.resolveAssociationTarget(currentType, segment);
      }
      if (currentType == null) {
        // The path ends in a collection of basic values: the subquery root is the element itself.
        subRegistry.markBasicElement(navigated);
      }
      return new SubRootContext(navigated, currentType, null);
    }
    @SuppressWarnings("unchecked")
    Class<Object> entityClass = (Class<Object>) sc.subEntity();
    Root<Object> from = sub.from(entityClass);
    ManagedType<?> subRootType = from.getModel();
    List<Predicate> correlationPredicates = new ArrayList<>();
    for (CorrelationPair pair : sc.correlations()) {
      Path<?> outerPath = resolvePath(outer, outerType, outerRegistry, pair.outerField());
      Path<?> innerPath = resolvePath(from, subRootType, subRegistry, pair.innerField());
      correlationPredicates.add(cb.equal(innerPath, outerPath));
    }
    Predicate correlationPredicate =
        correlationPredicates.isEmpty()
            ? null
            : cb.and(correlationPredicates.toArray(Predicate[]::new));
    return new SubRootContext(from, subRootType, correlationPredicate);
  }

  private void applyBody(
      SubqueryCondition sc,
      CriteriaBuilder cb,
      Subquery<?> sub,
      SubRootContext context,
      AssociationRegistry subRegistry) {
    Predicate body =
        toPredicate(
            sc.subCondition(),
            context.subRoot(),
            context.subRootType(),
            sub,
            cb,
            subRegistry,
            collectionConditions(true, sc.subCondition()));
    List<Predicate> whereParts = new ArrayList<>();
    if (context.correlationPredicate() != null) {
      whereParts.add(context.correlationPredicate());
    }
    if (body != null) {
      whereParts.add(body);
    }
    if (!whereParts.isEmpty()) {
      sub.where(whereParts.toArray(Predicate[]::new));
    }
  }

  private record SubRootContext(
      From<?, ?> subRoot, ManagedType<?> subRootType, Predicate correlationPredicate) {}

  private OperatorHandler handlerFor(PredicateCondition condition) {
    return operatorRegistry
        .find(condition.operator())
        .orElseThrow(
            () ->
                new InvalidFilterException(
                    condition.field(), "unknown operator '" + condition.operator().value() + "'"));
  }

  private Path<?> resolvePath(
      From<?, ?> from, ManagedType<?> fromType, AssociationRegistry registry, String field) {
    return resolvePath(from, fromType, registry, field, true);
  }

  private Path<?> resolvePath(
      From<?, ?> from,
      ManagedType<?> fromType,
      AssociationRegistry registry,
      String field,
      boolean joinBasicCollections) {
    if (registry.isBasicElement(from)) {
      return basicElement(from, field);
    }
    if (!joinBasicCollections) {
      return pathResolver.resolve(from, fromType, registry, field, JoinMode.LEFT, false);
    }
    if (from instanceof Root<?> rootFrom) {
      return pathResolver.resolve(rootFrom, registry, field, JoinMode.LEFT);
    }
    return pathResolver.resolve(from, fromType, registry, field, JoinMode.LEFT);
  }

  /**
   * Resolves a field inside an {@code exists} / {@code notExists} over a collection of basic
   * values. The subquery root is the joined element itself, which has no attributes, so the only
   * valid field is {@value #BASIC_ELEMENT_FIELD}.
   */
  private Path<?> basicElement(From<?, ?> element, String field) {
    if (!BASIC_ELEMENT_FIELD.equals(field)) {
      throw new IllegalArgumentException(
          "Subqueries over a collection of basic values compare the element itself: use the"
              + " field '"
              + BASIC_ELEMENT_FIELD
              + "' instead of '"
              + field
              + "'");
    }
    return element;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private From<?, ?> correlateOuter(Subquery<?> sub, From<?, ?> outer) {
    if (outer instanceof Root<?> outerRoot) {
      return sub.correlate((Root) outerRoot);
    }
    return sub.correlate((Join) outer);
  }
}
