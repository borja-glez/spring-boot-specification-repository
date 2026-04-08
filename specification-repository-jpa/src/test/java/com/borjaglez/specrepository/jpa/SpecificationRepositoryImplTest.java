package com.borjaglez.specrepository.jpa;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.QueryPlan;

class SpecificationRepositoryImplTest {

  @Test
  void shouldRejectMissingProjectionTypeMetadata() {
    QueryPlan<Object> plan =
        new QueryPlan<>(
            Object.class,
            new GroupCondition(LogicalOperator.AND, List.of()),
            List.of(),
            List.of(),
            List.of("name"),
            List.of(),
            null,
            List.of(),
            Sort.unsorted(),
            false);

    assertThatIllegalStateException()
        .isThrownBy(() -> SpecificationRepositoryImpl.requiredProjectionType(plan))
        .withMessage("projectionType must not be null");
  }
}
