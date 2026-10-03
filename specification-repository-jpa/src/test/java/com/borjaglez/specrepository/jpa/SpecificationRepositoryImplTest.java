package com.borjaglez.specrepository.jpa;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;
import com.borjaglez.specrepository.jpa.support.SpecificationRepositoryConfiguration;

class SpecificationRepositoryImplTest {

  @Test
  void shouldRejectMissingProjectionTypeMetadata() {
    QueryPlan<Object> plan =
        SpecificationQueryBuilder.forEntity(Object.class).select("name").build();

    assertThatIllegalStateException()
        .isThrownBy(() -> SpecificationRepositoryImpl.requiredProjectionType(plan))
        .withMessage("projectionType must not be null");
  }

  @Test
  void shouldRejectNullConfigurationInRepositoryConstructor() {
    JpaEntityInformation<Object, ?> entityInformation = mock(JpaEntityInformation.class);
    EntityManager entityManager = mock(EntityManager.class, RETURNS_DEEP_STUBS);

    assertThatThrownBy(
            () ->
                new SpecificationRepositoryImpl<>(
                    entityInformation, entityManager, (SpecificationRepositoryConfiguration) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("configuration must not be null");
  }

  @Test
  void shouldAllowCustomConfigurationInRepositoryConstructor() {
    JpaEntityInformation<Object, ?> entityInformation = mock(JpaEntityInformation.class);
    EntityManager entityManager = mock(EntityManager.class, RETURNS_DEEP_STUBS);

    assertThatNoException()
        .isThrownBy(
            () ->
                new SpecificationRepositoryImpl<>(
                    entityInformation,
                    entityManager,
                    SpecificationRepositoryConfiguration.defaultConfiguration()));
  }

  @Test
  void shouldRejectADisallowedPageableSortBeforeSendingAnyQuery() {
    JpaEntityInformation<Object, ?> entityInformation = mock(JpaEntityInformation.class);
    EntityManager entityManager = mock(EntityManager.class, RETURNS_DEEP_STUBS);
    SpecificationRepositoryImpl<Object, ?> repository =
        new SpecificationRepositoryImpl<>(
            entityInformation,
            entityManager,
            SpecificationRepositoryConfiguration.defaultConfiguration());
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name"), Set.of("name"));
    QueryPlan<Object> entityPlan =
        SpecificationQueryBuilder.forEntity(Object.class).allowedFields(policy).build();
    QueryPlan<Object> projectedPlan =
        SpecificationQueryBuilder.forEntity(Object.class)
            .allowedFields(policy)
            .select("name")
            .selectInto(Projection.class)
            .build();
    Pageable pageable = PageRequest.of(0, 5, Sort.by("name").and(Sort.by("secret")));

    assertThatThrownBy(() -> repository.findAll(entityPlan, pageable))
        .isInstanceOf(DisallowedFieldException.class)
        .hasMessage("Field 'secret' is not allowed for sorting");
    assertThatThrownBy(() -> repository.findSlice(entityPlan, pageable))
        .isInstanceOf(DisallowedFieldException.class);
    assertThatThrownBy(() -> repository.findAllProjected(projectedPlan, pageable))
        .isInstanceOf(DisallowedFieldException.class);
    assertThatThrownBy(() -> repository.findSliceProjected(projectedPlan, pageable))
        .isInstanceOf(DisallowedFieldException.class);
    verify(entityManager, never()).getCriteriaBuilder();
    verify(entityManager, never()).createQuery(any(CriteriaQuery.class));
  }

  private record Projection(String name) {}
}
