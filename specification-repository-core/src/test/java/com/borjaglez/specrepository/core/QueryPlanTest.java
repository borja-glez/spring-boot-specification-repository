package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class QueryPlanTest {

  private static final GroupCondition EMPTY_AND =
      new GroupCondition(LogicalOperator.AND, List.of());

  private static QueryPlan<String> plan() {
    return SpecificationQueryBuilder.forEntity(String.class)
        .where("status", Operators.EQUALS, "PLACED")
        .leftJoin("customer")
        .leftFetch("lines")
        .groupBy("status")
        .select("status")
        .count("id")
        .having(AggregateFunction.COUNT, "id", Operators.GREATER_THAN, 1)
        .sort(Sort.by("status"))
        .distinct()
        .allowedFields(AllowedFieldsPolicy.allowAll())
        .selectInto(Integer.class)
        .build();
  }

  @Test
  void shouldHaveNoPublicConstructor() {
    assertThat(QueryPlan.class.getConstructors()).isEmpty();
    assertThat(Modifier.isFinal(QueryPlan.class.getModifiers())).isTrue();
    assertThat(QueryPlan.class.isRecord()).isFalse();
    assertThat(
            Arrays.stream(QueryPlan.class.getDeclaredConstructors())
                .map(Constructor::getModifiers)
                .noneMatch(modifiers -> Modifier.isPublic(modifiers)))
        .isTrue();
  }

  @Test
  void equalPlansShouldBeEqualAndHaveTheSameHashCode() {
    QueryPlan<String> plan = plan();
    QueryPlan<String> same = plan();

    assertThat(plan).isEqualTo(plan).isEqualTo(same).hasSameHashCodeAs(same);
  }

  @Test
  void plansThatDifferShouldNotBeEqual() {
    QueryPlan<String> plan = plan();

    assertThat(plan).isNotEqualTo(plan.toBuilder().where("id", Operators.EQUALS, 1).build());
    assertThat(plan).isNotEqualTo(plan.toBuilder().lock(LockMode.PESSIMISTIC_WRITE).build());
    assertThat(plan).isNotEqualTo(plan.toBuilder().sort(plan.sort()).build());
    assertThat(plan).isNotEqualTo(SpecificationQueryBuilder.forEntity(String.class).build());
    assertThat(plan).isNotEqualTo("plan");
    assertThat(plan).isNotEqualTo(null);
  }

  @Test
  void toStringShouldNameEveryComponent() {
    assertThat(plan().toString())
        .startsWith("QueryPlan[entityType=class java.lang.String, rootCondition=")
        .contains(
            ", serverCondition=",
            ", joins=[JoinInstruction[path=customer, mode=LEFT]]",
            ", fetches=[FetchInstruction[path=lines, mode=LEFT]]",
            ", projections=[status]",
            ", selections=",
            ", projectionType=class java.lang.Integer",
            ", groupBy=[status]",
            ", having=",
            ", sort=status: ASC",
            ", serverSort=false",
            ", distinct=true",
            ", allowedFieldsPolicy=",
            ", lock=QueryLock[mode=NONE, lockWait=WAIT]")
        .endsWith("]");
  }

  @Test
  void aSortSetOnADerivedBuilderShouldBeAServerSort() {
    QueryPlan<String> plan = plan();

    assertThat(plan.serverSort()).isFalse();
    assertThat(plan.toBuilder().build().serverSort()).isFalse();
    assertThat(plan.toBuilder().sort(Sort.by("id")).build().serverSort()).isTrue();
    assertThat(plan.toBuilder().sort(Sort.by("id")).build().withoutFetches().serverSort()).isTrue();
  }

  @Test
  void withoutFetchesShouldDropOnlyTheFetches() {
    QueryPlan<String> plan = plan();

    QueryPlan<String> withoutFetches = plan.withoutFetches();

    assertThat(withoutFetches.fetches()).isEmpty();
    assertThat(withoutFetches.toBuilder().leftFetch("lines").build()).isEqualTo(plan);
  }

  @Test
  void shouldRejectANullServerCondition() {
    assertThatNullPointerException()
        .isThrownBy(() -> plan(null, List.of(), AllowedFieldsPolicy.allowAll(), QueryLock.NONE))
        .withMessage("serverCondition must not be null");
  }

  @Test
  void shouldRejectAServerConditionThatIsNotAnAndGroup() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                plan(
                    new GroupCondition(LogicalOperator.OR, List.of()),
                    List.of(),
                    AllowedFieldsPolicy.allowAll(),
                    QueryLock.NONE))
        .withMessage("serverCondition must be an AND group");
  }

  @Test
  void shouldRejectNullHaving() {
    assertThatNullPointerException()
        .isThrownBy(() -> plan(EMPTY_AND, null, AllowedFieldsPolicy.allowAll(), QueryLock.NONE))
        .withMessage("having must not be null");
  }

  @Test
  void shouldRejectANullAllowedFieldsPolicy() {
    assertThatNullPointerException()
        .isThrownBy(() -> plan(EMPTY_AND, List.of(), null, QueryLock.NONE))
        .withMessage("allowedFieldsPolicy must not be null");
  }

  @Test
  void shouldRejectANullLock() {
    assertThatNullPointerException()
        .isThrownBy(() -> plan(EMPTY_AND, List.of(), AllowedFieldsPolicy.allowAll(), null))
        .withMessage("lock must not be null");
  }

  private static QueryPlan<String> plan(
      GroupCondition serverCondition,
      List<HavingCondition> having,
      AllowedFieldsPolicy policy,
      QueryLock lock) {
    return new QueryPlan<>(
        String.class,
        EMPTY_AND,
        serverCondition,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of(),
        having,
        Sort.unsorted(),
        false,
        false,
        policy,
        lock);
  }
}
