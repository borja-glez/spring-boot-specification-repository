package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class QueryPlanDerivationTest {

  private static final AllowedFieldsPolicy CLIENT_POLICY =
      AllowedFieldsPolicy.of(Set.of("status", "total"), Set.of("placedAt"));

  private static QueryPlan<String> clientPlan() {
    return SpecificationQueryBuilder.forEntity(String.class)
        .where("status", Operators.EQUALS, "PLACED")
        .or(
            group ->
                group
                    .where("total", Operators.GREATER_THAN, 10)
                    .where("total", Operators.IS_NULL, null))
        .leftJoin("customer")
        .leftFetch("lines")
        .sort(Sort.by("placedAt"))
        .distinct()
        .allowedFields(CLIENT_POLICY)
        .build();
  }

  @Test
  void aNewPlanShouldHaveNoServerConditions() {
    QueryPlan<String> plan = clientPlan();

    assertThat(plan.serverCondition().logicalOperator()).isEqualTo(LogicalOperator.AND);
    assertThat(plan.serverCondition().conditions()).isEmpty();
  }

  @Test
  void derivingShouldKeepEveryComponent() {
    QueryPlan<String> plan =
        SpecificationQueryBuilder.forEntity(String.class)
            .where("status", Operators.EQUALS, "PLACED")
            .innerJoin("customer")
            .rightFetch("lines")
            .groupBy("status")
            .select("status")
            .countAs("orders", "id")
            .having(AggregateFunction.COUNT, "id", Operators.GREATER_THAN, 1)
            .sort(Sort.by("status"))
            .distinct()
            .allowedFields(CLIENT_POLICY)
            .selectInto(Integer.class)
            .build();

    QueryPlan<String> derived = plan.toBuilder().build();

    assertThat(derived).isEqualTo(plan);
  }

  @Test
  void addedConditionsShouldBeServerConditionsAndLeaveTheClientConditionsIntact() {
    QueryPlan<String> client = clientPlan();

    QueryPlan<String> derived =
        client.toBuilder().where("customerId", Operators.EQUALS, "u1").build();

    assertThat(derived.rootCondition()).isEqualTo(client.rootCondition());
    assertThat(derived.serverCondition())
        .isEqualTo(
            new GroupCondition(
                LogicalOperator.AND,
                List.of(
                    new PredicateCondition("customerId", Operators.EQUALS, "u1", false, false))));
    assertThat(derived.joins()).isEqualTo(client.joins());
    assertThat(derived.fetches()).isEqualTo(client.fetches());
    assertThat(derived.sort()).isEqualTo(client.sort());
    assertThat(derived.distinct()).isTrue();
    assertThat(derived.allowedFieldsPolicy()).isSameAs(CLIENT_POLICY);
  }

  @Test
  void derivingShouldNotChangeTheOriginalPlan() {
    QueryPlan<String> client = clientPlan();
    QueryPlan<String> copy = clientPlan();

    client.toBuilder()
        .where("customerId", Operators.EQUALS, "u1")
        .leftFetch("payments")
        .sort(Sort.by("id"))
        .build();

    assertThat(client).isEqualTo(copy);
    assertThat(client.serverCondition().conditions()).isEmpty();
  }

  @Test
  void derivingTwiceShouldKeepTheEarlierServerConditions() {
    QueryPlan<String> once =
        clientPlan().toBuilder().where("customerId", Operators.EQUALS, "u1").build();

    QueryPlan<String> twice =
        once.toBuilder().and(group -> group.where("deleted", Operators.EQUALS, false)).build();

    assertThat(twice.rootCondition()).isEqualTo(once.rootCondition());
    assertThat(twice.serverCondition().conditions())
        .containsExactly(
            new PredicateCondition("customerId", Operators.EQUALS, "u1", false, false),
            new GroupCondition(
                LogicalOperator.AND,
                List.of(new PredicateCondition("deleted", Operators.EQUALS, false, false, false))));
  }

  @Test
  void anOrAddedOnADerivedBuilderShouldStayInsideTheServerCondition() {
    QueryPlan<String> derived =
        clientPlan().toBuilder()
            .or(
                group ->
                    group
                        .where("channel", Operators.EQUALS, "web")
                        .where("channel", Operators.EQUALS, "app"))
            .build();

    assertThat(derived.serverCondition().logicalOperator()).isEqualTo(LogicalOperator.AND);
    assertThat(derived.serverCondition().conditions())
        .singleElement()
        .isInstanceOfSatisfying(
            GroupCondition.class,
            group -> assertThat(group.logicalOperator()).isEqualTo(LogicalOperator.OR));
  }

  @Test
  void subqueriesAddedOnADerivedBuilderShouldBeServerConditions() {
    QueryPlan<String> derived =
        clientPlan().toBuilder()
            .exists("lines", sub -> sub.where("sku", Operators.EQUALS, "A"))
            .build();

    assertThat(derived.serverCondition().conditions())
        .singleElement()
        .isInstanceOf(SubqueryCondition.class);
  }

  @Test
  void derivedBuilderShouldAddFetchesAndJoins() {
    QueryPlan<String> derived =
        clientPlan().toBuilder().leftFetch("payments").innerJoin("seller").build();

    assertThat(derived.fetches())
        .containsExactly(
            new FetchInstruction("lines", JoinMode.LEFT),
            new FetchInstruction("payments", JoinMode.LEFT));
    assertThat(derived.joins())
        .containsExactly(
            new JoinInstruction("customer", JoinMode.LEFT),
            new JoinInstruction("seller", JoinMode.INNER));
  }

  @Test
  void sortOnADerivedBuilderShouldReplaceTheClientSort() {
    QueryPlan<String> derived = clientPlan().toBuilder().sort(Sort.by("id")).build();

    assertThat(derived.sort()).isEqualTo(Sort.by("id"));
  }

  @Test
  void sortedByDefaultShouldKeepTheClientSort() {
    QueryPlan<String> derived =
        clientPlan().toBuilder().sortedByDefault(Sort.by(Sort.Direction.DESC, "id")).build();

    assertThat(derived.sort()).isEqualTo(Sort.by("placedAt"));
  }

  @Test
  void sortedByDefaultShouldApplyWhenTheClientSentNoSort() {
    QueryPlan<String> client =
        SpecificationQueryBuilder.forEntity(String.class)
            .where("status", Operators.EQUALS, "PLACED")
            .build();

    QueryPlan<String> derived =
        client.toBuilder().sortedByDefault(Sort.by(Sort.Direction.DESC, "id")).build();

    assertThat(derived.sort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
  }

  @Test
  void sortedByDefaultShouldRejectNull() {
    assertThatNullPointerException()
        .isThrownBy(() -> clientPlan().toBuilder().sortedByDefault(null))
        .withMessage("sort must not be null");
  }

  @Test
  void fromShouldDeriveLikeToBuilder() {
    QueryPlan<String> client = clientPlan();

    QueryPlan<String> derived =
        SpecificationQueryBuilder.from(client).where("customerId", Operators.EQUALS, "u1").build();

    assertThat(derived)
        .isEqualTo(client.toBuilder().where("customerId", Operators.EQUALS, "u1").build());
  }

  @Test
  void derivingShouldRejectANullPlan() {
    assertThatNullPointerException()
        .isThrownBy(() -> new QueryPlanBuilder<>((QueryPlan<Object>) null))
        .withMessage("plan must not be null");
  }
}
