package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ConditionGroupBuilderTest {

  @Test
  void shouldRejectNullLogicalOperator() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ConditionGroupBuilder<>(null))
        .withMessage("logicalOperator must not be null");
  }

  @Test
  void whereWithThreeArgsShouldAddConditionWithDefaults() {
    GroupCondition group =
        new ConditionGroupBuilder<>(LogicalOperator.AND)
            .where("name", Operators.EQUALS, "test")
            .build();

    assertThat(group.logicalOperator()).isEqualTo(LogicalOperator.AND);
    assertThat(group.conditions()).hasSize(1);
    PredicateCondition pc = (PredicateCondition) group.conditions().get(0);
    assertThat(pc.field()).isEqualTo("name");
    assertThat(pc.operator()).isEqualTo(Operators.EQUALS);
    assertThat(pc.value()).isEqualTo("test");
    assertThat(pc.ignoreCase()).isFalse();
    assertThat(pc.includeNulls()).isFalse();
  }

  @Test
  void whereWithFiveArgsShouldPassIgnoreCaseAndIncludeNulls() {
    GroupCondition group =
        new ConditionGroupBuilder<>(LogicalOperator.OR)
            .where("email", Operators.CONTAINS, "@", true, true)
            .build();

    PredicateCondition pc = (PredicateCondition) group.conditions().get(0);
    assertThat(pc.ignoreCase()).isTrue();
    assertThat(pc.includeNulls()).isTrue();
  }

  @Test
  void andShouldAddNestedGroupWithAndOperator() {
    GroupCondition group =
        new ConditionGroupBuilder<>(LogicalOperator.AND)
            .where("a", Operators.EQUALS, 1)
            .and(nested -> nested.where("b", Operators.EQUALS, 2))
            .build();

    assertThat(group.conditions()).hasSize(2);
    GroupCondition nested = (GroupCondition) group.conditions().get(1);
    assertThat(nested.logicalOperator()).isEqualTo(LogicalOperator.AND);
    assertThat(nested.conditions()).hasSize(1);
  }

  @Test
  void orShouldAddNestedGroupWithOrOperator() {
    GroupCondition group =
        new ConditionGroupBuilder<>(LogicalOperator.AND)
            .or(nested -> nested.where("x", Operators.EQUALS, 1).where("y", Operators.EQUALS, 2))
            .build();

    assertThat(group.conditions()).hasSize(1);
    GroupCondition nested = (GroupCondition) group.conditions().get(0);
    assertThat(nested.logicalOperator()).isEqualTo(LogicalOperator.OR);
    assertThat(nested.conditions()).hasSize(2);
  }

  @Test
  void buildShouldReturnImmutableConditionList() {
    GroupCondition group =
        new ConditionGroupBuilder<>(LogicalOperator.AND).where("a", Operators.EQUALS, 1).build();

    assertThat(group.conditions()).isUnmodifiable();
  }

  @Test
  void existsAssociationShouldAddSubqueryCondition() {
    GroupCondition group =
        new ConditionGroupBuilder<Object>(LogicalOperator.AND)
            .exists("orders", sub -> sub.where("total", Operators.GREATER_THAN, 10))
            .build();

    SubqueryCondition sc = (SubqueryCondition) group.conditions().get(0);
    assertThat(sc.kind()).isEqualTo(SubqueryKind.EXISTS);
    assertThat(sc.correlationMode()).isEqualTo(CorrelationMode.ASSOCIATION);
    assertThat(sc.associationPath()).isEqualTo("orders");
    assertThat(sc.subEntity()).isNull();
    assertThat(sc.subCondition().conditions()).hasSize(1);
  }

  @Test
  void existsAssociationShouldRejectCorrelateCalls() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ConditionGroupBuilder<Object>(LogicalOperator.AND)
                    .<Object>exists("orders", sub -> sub.correlate("id", "customer.id")))
        .withMessageContaining("correlate")
        .withMessageContaining("orders");
  }

  @Test
  void notExistsAssociationShouldAddSubqueryCondition() {
    GroupCondition group =
        new ConditionGroupBuilder<Object>(LogicalOperator.AND)
            .notExists("orders", sub -> sub.where("status", Operators.EQUALS, "CANCELLED"))
            .build();

    SubqueryCondition sc = (SubqueryCondition) group.conditions().get(0);
    assertThat(sc.kind()).isEqualTo(SubqueryKind.NOT_EXISTS);
    assertThat(sc.correlationMode()).isEqualTo(CorrelationMode.ASSOCIATION);
  }

  @Test
  void existsEntityShouldAddSubqueryConditionWithCorrelations() {
    GroupCondition group =
        new ConditionGroupBuilder<Object>(LogicalOperator.AND)
            .exists(
                String.class,
                sub -> sub.correlate("id", "customer.id").where("status", Operators.EQUALS, "PAID"))
            .build();

    SubqueryCondition sc = (SubqueryCondition) group.conditions().get(0);
    assertThat(sc.kind()).isEqualTo(SubqueryKind.EXISTS);
    assertThat(sc.correlationMode()).isEqualTo(CorrelationMode.ENTITY);
    assertThat(sc.subEntity()).isEqualTo(String.class);
    assertThat(sc.correlations())
        .singleElement()
        .satisfies(
            pair -> {
              assertThat(pair.outerField()).isEqualTo("id");
              assertThat(pair.innerField()).isEqualTo("customer.id");
            });
  }

  @Test
  void notExistsEntityShouldAddSubqueryCondition() {
    GroupCondition group =
        new ConditionGroupBuilder<Object>(LogicalOperator.AND)
            .notExists(String.class, sub -> sub.where("status", Operators.EQUALS, "X"))
            .build();

    SubqueryCondition sc = (SubqueryCondition) group.conditions().get(0);
    assertThat(sc.kind()).isEqualTo(SubqueryKind.NOT_EXISTS);
    assertThat(sc.correlationMode()).isEqualTo(CorrelationMode.ENTITY);
    assertThat(sc.correlations()).isEmpty();
  }

  @Test
  void inSubqueryShouldAddSubqueryCondition() {
    GroupCondition group =
        new ConditionGroupBuilder<Object>(LogicalOperator.AND)
            .inSubquery(
                "id", String.class, "customer.id", sub -> sub.where("vip", Operators.EQUALS, true))
            .build();

    SubqueryCondition sc = (SubqueryCondition) group.conditions().get(0);
    assertThat(sc.kind()).isEqualTo(SubqueryKind.IN);
    assertThat(sc.outerField()).isEqualTo("id");
    assertThat(sc.subSelectField()).isEqualTo("customer.id");
  }

  @Test
  void notInSubqueryShouldAddSubqueryCondition() {
    GroupCondition group =
        new ConditionGroupBuilder<Object>(LogicalOperator.AND)
            .notInSubquery(
                "id", String.class, "customer.id", sub -> sub.where("vip", Operators.EQUALS, true))
            .build();

    SubqueryCondition sc = (SubqueryCondition) group.conditions().get(0);
    assertThat(sc.kind()).isEqualTo(SubqueryKind.NOT_IN);
  }

  @Test
  void subqueryBuilderShouldSupportAllCompositionMethods() {
    GroupCondition group =
        new ConditionGroupBuilder<Object>(LogicalOperator.AND)
            .exists(
                "orders",
                sub ->
                    sub.where("total", Operators.GREATER_THAN, 10)
                        .where("status", Operators.EQUALS, "PAID", true, true)
                        .and(inner -> inner.where("vip", Operators.EQUALS, true))
                        .or(inner -> inner.where("vip", Operators.EQUALS, false)))
            .build();

    SubqueryCondition sc = (SubqueryCondition) group.conditions().get(0);
    assertThat(sc.subCondition().conditions()).hasSize(4);
  }
}
