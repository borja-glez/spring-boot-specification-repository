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

  private record NameEmailProjection(String name, String email) {}

  private record CountProjection(Long count) {}
}
