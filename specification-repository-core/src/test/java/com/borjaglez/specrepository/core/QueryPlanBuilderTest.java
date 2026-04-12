package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class QueryPlanBuilderTest {

  @Test
  void shouldBuildImmutableQueryPlan() {
    QueryPlan<String> plan =
        SpecificationQueryBuilder.forEntity(String.class)
            .where("name", Operators.CONTAINS, "Borja", true, false)
            .and(group -> group.where("email", Operators.CONTAINS, "@example.com"))
            .leftJoin("profile")
            .leftFetch("roles")
            .groupBy("status")
            .select("name", "email")
            .count("id")
            .sort(Sort.by("name"))
            .distinct()
            .build();

    assertThat(plan.entityType()).isEqualTo(String.class);
    assertThat(plan.rootCondition().conditions()).hasSize(2);
    assertThat(plan.joins()).containsExactly(new JoinInstruction("profile", JoinMode.LEFT));
    assertThat(plan.fetches()).containsExactly(new FetchInstruction("roles", JoinMode.LEFT));
    assertThat(plan.groupBy()).containsExactly("status");
    assertThat(plan.projections()).containsExactly("name", "email");
    assertThat(plan.projectionType()).isNull();
    assertThat(plan.selections())
        .containsExactly(
            new FieldSelection("name"),
            new FieldSelection("email"),
            new AggregateSelection(AggregateFunction.COUNT, "id"));
    assertThat(plan.sort().getOrderFor("name")).isNotNull();
    assertThat(plan.distinct()).isTrue();
    assertThat(plan.hasSelections()).isTrue();
    assertThat(plan.hasAggregates()).isTrue();
  }

  @Test
  void shouldAddAggregateSelectionsInDeclarationOrder() {
    QueryPlan<String> plan =
        new QueryPlanBuilder<>(String.class)
            .select("status")
            .sum("age")
            .avg("score")
            .min("createdAt")
            .max("updatedAt")
            .count("id")
            .build();

    assertThat(plan.selections())
        .containsExactly(
            new FieldSelection("status"),
            new AggregateSelection(AggregateFunction.SUM, "age"),
            new AggregateSelection(AggregateFunction.AVG, "score"),
            new AggregateSelection(AggregateFunction.MIN, "createdAt"),
            new AggregateSelection(AggregateFunction.MAX, "updatedAt"),
            new AggregateSelection(AggregateFunction.COUNT, "id"));
  }

  @Test
  void shouldRejectNullEntityType() {
    assertThatNullPointerException()
        .isThrownBy(() -> new QueryPlanBuilder<>(null))
        .withMessage("entityType must not be null");
  }

  @Test
  void shouldStoreProjectionTypeWhenSelectingIntoRecord() {
    QueryPlan<String> plan =
        new QueryPlanBuilder<>(String.class)
            .select("name", "email")
            .selectInto(NameEmailProjection.class)
            .build();

    assertThat(plan.projectionType()).isEqualTo(NameEmailProjection.class);
    assertThat(plan.projections()).containsExactly("name", "email");
  }

  @Test
  void shouldRequireSelectBeforeSelectInto() {
    assertThatIllegalStateException()
        .isThrownBy(
            () -> new QueryPlanBuilder<>(String.class).selectInto(NameEmailProjection.class))
        .withMessage("select and/or aggregate selection methods must be called before selectInto");
  }

  @Test
  void shouldAllowSelectIntoAfterAggregateSelection() {
    QueryPlan<String> plan =
        new QueryPlanBuilder<>(String.class).count("id").selectInto(CountProjection.class).build();

    assertThat(plan.projectionType()).isEqualTo(CountProjection.class);
    assertThat(plan.selections())
        .containsExactly(new AggregateSelection(AggregateFunction.COUNT, "id"));
  }

  @Test
  void shouldRejectNullProjectionType() {
    assertThatNullPointerException()
        .isThrownBy(() -> new QueryPlanBuilder<>(String.class).select("name").selectInto(null))
        .withMessage("projectionType must not be null");
  }

  @Test
  void shouldReturnProjectedWrapperWithoutMutationMethods() {
    assertThat(
            Arrays.stream(ProjectedQueryPlanBuilder.class.getMethods())
                .map(method -> method.getName()))
        .contains("build")
        .doesNotContain("where", "and", "or", "select", "sum", "groupBy", "sort", "distinct");
  }

  @Test
  void whereWithThreeArgsShouldDelegateToRootGroup() {
    QueryPlan<String> plan =
        new QueryPlanBuilder<>(String.class).where("field", Operators.EQUALS, "val").build();

    assertThat(plan.rootCondition().conditions()).hasSize(1);
    PredicateCondition pc = (PredicateCondition) plan.rootCondition().conditions().get(0);
    assertThat(pc.ignoreCase()).isFalse();
    assertThat(pc.includeNulls()).isFalse();
  }

  @Test
  void orShouldAddNestedOrGroup() {
    QueryPlan<String> plan =
        new QueryPlanBuilder<>(String.class)
            .or(g -> g.where("a", Operators.EQUALS, 1).where("b", Operators.EQUALS, 2))
            .build();

    GroupCondition nested = (GroupCondition) plan.rootCondition().conditions().get(0);
    assertThat(nested.logicalOperator()).isEqualTo(LogicalOperator.OR);
    assertThat(nested.conditions()).hasSize(2);
  }

  @Test
  void innerJoinShouldAddJoinsWithInnerMode() {
    QueryPlan<String> plan = new QueryPlanBuilder<>(String.class).innerJoin("a", "b").build();

    assertThat(plan.joins())
        .containsExactly(
            new JoinInstruction("a", JoinMode.INNER), new JoinInstruction("b", JoinMode.INNER));
  }

  @Test
  void rightJoinShouldAddJoinsWithRightMode() {
    QueryPlan<String> plan = new QueryPlanBuilder<>(String.class).rightJoin("x").build();

    assertThat(plan.joins()).containsExactly(new JoinInstruction("x", JoinMode.RIGHT));
  }

  @Test
  void innerFetchShouldAddFetchesWithInnerMode() {
    QueryPlan<String> plan = new QueryPlanBuilder<>(String.class).innerFetch("a", "b").build();

    assertThat(plan.fetches())
        .containsExactly(
            new FetchInstruction("a", JoinMode.INNER), new FetchInstruction("b", JoinMode.INNER));
  }

  @Test
  void rightFetchShouldAddFetchesWithRightMode() {
    QueryPlan<String> plan = new QueryPlanBuilder<>(String.class).rightFetch("z").build();

    assertThat(plan.fetches()).containsExactly(new FetchInstruction("z", JoinMode.RIGHT));
  }

  @Test
  void sortShouldRejectNull() {
    assertThatNullPointerException()
        .isThrownBy(() -> new QueryPlanBuilder<>(String.class).sort(null))
        .withMessage("sort must not be null");
  }

  @Test
  void buildWithoutDistinctShouldDefaultToFalse() {
    QueryPlan<String> plan = new QueryPlanBuilder<>(String.class).build();
    assertThat(plan.distinct()).isFalse();
    assertThat(plan.sort()).isEqualTo(Sort.unsorted());
    assertThat(plan.joins()).isEmpty();
    assertThat(plan.fetches()).isEmpty();
    assertThat(plan.projections()).isEmpty();
    assertThat(plan.selections()).isEmpty();
    assertThat(plan.groupBy()).isEmpty();
    assertThat(plan.hasSelections()).isFalse();
    assertThat(plan.hasAggregates()).isFalse();
    assertThat(plan.allowedFieldsPolicy()).isSameAs(AllowedFieldsPolicy.allowAll());
  }

  @Test
  void shouldStoreAllowedFieldsPolicy() {
    AllowedFieldsPolicy policy =
        AllowedFieldsPolicy.of(java.util.Set.of("name"), java.util.Set.of("name"));
    QueryPlan<String> plan = new QueryPlanBuilder<>(String.class).allowedFields(policy).build();

    assertThat(plan.allowedFieldsPolicy()).isSameAs(policy);
  }

  @Test
  void allowedFieldsShouldRejectNull() {
    assertThatNullPointerException()
        .isThrownBy(() -> new QueryPlanBuilder<>(String.class).allowedFields(null))
        .withMessage("allowedFieldsPolicy must not be null");
  }

  @Test
  void queryPlanCompatibilityConstructorShouldDefaultToAllowAll() {
    QueryPlan<String> plan =
        new QueryPlan<>(
            String.class,
            new GroupCondition(LogicalOperator.AND, java.util.List.of()),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            null,
            java.util.List.of(),
            Sort.unsorted(),
            false);

    assertThat(plan.allowedFieldsPolicy()).isSameAs(AllowedFieldsPolicy.allowAll());
  }

  @Test
  void queryPlanShouldRejectNullAllowedFieldsPolicy() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new QueryPlan<>(
                    String.class,
                    new GroupCondition(LogicalOperator.AND, java.util.List.of()),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(),
                    null,
                    java.util.List.of(),
                    Sort.unsorted(),
                    false,
                    null))
        .withMessage("allowedFieldsPolicy must not be null");
  }

  @Test
  void specificationQueryBuilderForEntityShouldReturnBuilder() {
    QueryPlanBuilder<Integer> builder = SpecificationQueryBuilder.forEntity(Integer.class);
    QueryPlan<Integer> plan = builder.build();
    assertThat(plan.entityType()).isEqualTo(Integer.class);
  }

  @Test
  void shouldExposeSubqueryDslOnQueryPlanBuilder() {
    QueryPlan<String> plan =
        SpecificationQueryBuilder.forEntity(String.class)
            .<Integer>exists("orders", sub -> sub.where("total", Operators.GREATER_THAN, 10))
            .<Integer>notExists("drafts", sub -> sub.where("x", Operators.EQUALS, 1))
            .exists(Integer.class, sub -> sub.correlate("id", "ref"))
            .notExists(Integer.class, sub -> sub.where("y", Operators.EQUALS, 2))
            .inSubquery("id", Integer.class, "ref", sub -> sub.where("z", Operators.EQUALS, 3))
            .notInSubquery("id", Integer.class, "ref", sub -> sub.where("w", Operators.EQUALS, 4))
            .build();

    assertThat(plan.rootCondition().conditions()).hasSize(6);
    assertThat(plan.rootCondition().conditions())
        .allMatch(condition -> condition instanceof SubqueryCondition);
    assertThat(
            plan.rootCondition().conditions().stream()
                .map(c -> ((SubqueryCondition) c).kind())
                .toList())
        .containsExactly(
            SubqueryKind.EXISTS,
            SubqueryKind.NOT_EXISTS,
            SubqueryKind.EXISTS,
            SubqueryKind.NOT_EXISTS,
            SubqueryKind.IN,
            SubqueryKind.NOT_IN);
  }

  @Test
  void shouldBuildAggregateWithAlias() {
    QueryPlan<String> plan =
        new QueryPlanBuilder<>(String.class)
            .groupBy("status")
            .sumAs("total", "amount")
            .avgAs("avgScore", "score")
            .minAs("minDate", "createdAt")
            .maxAs("maxDate", "updatedAt")
            .countAs("items", "id")
            .aggregate(AggregateFunction.SUM, "extra", "extraTotal")
            .build();

    assertThat(plan.selections())
        .containsExactly(
            new AggregateSelection(AggregateFunction.SUM, "amount", "total"),
            new AggregateSelection(AggregateFunction.AVG, "score", "avgScore"),
            new AggregateSelection(AggregateFunction.MIN, "createdAt", "minDate"),
            new AggregateSelection(AggregateFunction.MAX, "updatedAt", "maxDate"),
            new AggregateSelection(AggregateFunction.COUNT, "id", "items"),
            new AggregateSelection(AggregateFunction.SUM, "extra", "extraTotal"));
  }

  @Test
  void shouldStoreHavingConditionsInBuildOrder() {
    QueryPlan<String> plan =
        new QueryPlanBuilder<>(String.class)
            .groupBy("status")
            .sum("amount")
            .having(AggregateFunction.SUM, "amount", Operators.GREATER_THAN, 100)
            .having(AggregateFunction.COUNT, "id", Operators.LESS_THAN, 10)
            .build();

    assertThat(plan.having())
        .containsExactly(
            new HavingCondition(AggregateFunction.SUM, "amount", Operators.GREATER_THAN, 100),
            new HavingCondition(AggregateFunction.COUNT, "id", Operators.LESS_THAN, 10));
  }

  @Test
  void shouldRejectHavingWithoutGroupBy() {
    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                new QueryPlanBuilder<>(String.class)
                    .sum("amount")
                    .having(AggregateFunction.SUM, "amount", Operators.GREATER_THAN, 1)
                    .build())
        .withMessage("having requires at least one groupBy field");
  }

  @Test
  void queryPlanLegacyConstructorWithPolicyShouldDefaultHavingToEmpty() {
    AllowedFieldsPolicy policy =
        AllowedFieldsPolicy.of(java.util.Set.of("name"), java.util.Set.of("name"));
    QueryPlan<String> plan =
        new QueryPlan<>(
            String.class,
            new GroupCondition(LogicalOperator.AND, java.util.List.of()),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            null,
            java.util.List.of(),
            Sort.unsorted(),
            false,
            policy);
    assertThat(plan.having()).isEmpty();
    assertThat(plan.allowedFieldsPolicy()).isSameAs(policy);
  }

  @Test
  void queryPlanShouldRejectNullHaving() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new QueryPlan<>(
                    String.class,
                    new GroupCondition(LogicalOperator.AND, java.util.List.of()),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(),
                    null,
                    java.util.List.of(),
                    null,
                    Sort.unsorted(),
                    false,
                    AllowedFieldsPolicy.allowAll()))
        .withMessage("having must not be null");
  }

  private record NameEmailProjection(String name, String email) {}

  private record CountProjection(Long count) {}
}
