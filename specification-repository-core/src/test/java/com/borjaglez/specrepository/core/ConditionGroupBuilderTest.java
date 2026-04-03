package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
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
}
