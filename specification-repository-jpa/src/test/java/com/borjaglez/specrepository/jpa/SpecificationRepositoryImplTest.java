package com.borjaglez.specrepository.jpa;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.QueryPlan;

class SpecificationRepositoryImplTest {

  @SuppressWarnings("unchecked")
  private final SpecificationRepository<Object, Long> repository =
      mock(SpecificationRepository.class, CALLS_REAL_METHODS);

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

  @Test
  void shouldRejectProjectedFindAllWhenRepositoryDoesNotSupportIt() {
    assertThatThrownBy(() -> repository.findAllProjected(projectedPlan()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Projected queries are not supported by this repository");
  }

  @Test
  void shouldRejectProjectedFindAllPageWhenRepositoryDoesNotSupportIt() {
    assertThatThrownBy(() -> repository.findAllProjected(projectedPlan(), PageRequest.of(0, 1)))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Projected queries are not supported by this repository");
  }

  @Test
  void shouldRejectProjectedFindOneWhenRepositoryDoesNotSupportIt() {
    assertThatThrownBy(() -> repository.findOneProjected(projectedPlan()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Projected queries are not supported by this repository");
  }

  private QueryPlan<Object> projectedPlan() {
    return new QueryPlan<>(
        Object.class,
        new GroupCondition(LogicalOperator.AND, List.of()),
        List.of(),
        List.of(),
        List.of("name"),
        List.of(),
        Projection.class,
        List.of(),
        Sort.unsorted(),
        false);
  }

  private record Projection(String name) {}
}
