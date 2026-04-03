package com.borjaglez.specrepository.jpa.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.jpa.SpecificationRepositoryImpl;

@DataJpaTest
@ContextConfiguration(classes = SpecificationRepositoryIntegrationTest.TestConfiguration.class)
class SpecificationRepositoryIntegrationTest {
  @org.springframework.beans.factory.annotation.Autowired private TestCustomerRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    repository.save(new TestCustomer("Borja", "ACTIVE", new TestProfile("Madrid")));
    repository.save(new TestCustomer("Lucia", "ACTIVE", new TestProfile("Barcelona")));
    repository.save(new TestCustomer("John", "INACTIVE", new TestProfile("Madrid")));
    repository.save(new TestCustomer("Anna", null, null));
  }

  // -- EQUALS / NOT_EQUALS --

  @Test
  void shouldFilterByEquals() {
    List<TestCustomer> results =
        repository.query().where("status", Operators.EQUALS, "ACTIVE").findAll();

    assertThat(results).hasSize(2);
  }

  @Test
  void shouldFilterByNotEquals() {
    List<TestCustomer> results =
        repository.query().where("status", Operators.NOT_EQUALS, "ACTIVE").findAll();

    assertThat(results).extracting(TestCustomer::getName).containsExactly("John");
  }

  // -- IS_NULL / IS_NOT_NULL --

  @Test
  void shouldFilterByIsNull() {
    long count = repository.query().where("status", Operators.IS_NULL, null).count();

    assertThat(count).isEqualTo(1);
  }

  @Test
  void shouldFilterByIsNotNull() {
    long count = repository.query().where("status", Operators.IS_NOT_NULL, null).count();

    assertThat(count).isEqualTo(3);
  }

  // -- CONTAINS / NOT_CONTAINS --

  @Test
  void shouldFilterByContains() {
    List<TestCustomer> results =
        repository.query().where("name", Operators.CONTAINS, "orj").findAll();

    assertThat(results).hasSize(1).first().extracting(TestCustomer::getName).isEqualTo("Borja");
  }

  @Test
  void shouldFilterByNotContains() {
    List<TestCustomer> results =
        repository.query().where("name", Operators.NOT_CONTAINS, "orj").findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Lucia", "John", "Anna");
  }

  // -- STARTS_WITH / ENDS_WITH --

  @Test
  void shouldFilterByStartsWith() {
    List<TestCustomer> results =
        repository.query().where("name", Operators.STARTS_WITH, "Bo").findAll();

    assertThat(results).hasSize(1).first().extracting(TestCustomer::getName).isEqualTo("Borja");
  }

  @Test
  void shouldFilterByEndsWith() {
    List<TestCustomer> results =
        repository.query().where("name", Operators.ENDS_WITH, "hn").findAll();

    assertThat(results).hasSize(1).first().extracting(TestCustomer::getName).isEqualTo("John");
  }

  // -- IN / NOT_IN (single value, no conversion needed) --

  @Test
  void shouldFilterByInWithSingleValue() {
    List<TestCustomer> results = repository.query().where("name", Operators.IN, "Borja").findAll();

    assertThat(results).hasSize(1).first().extracting(TestCustomer::getName).isEqualTo("Borja");
  }

  @Test
  void shouldFilterByNotInWithSingleValue() {
    List<TestCustomer> results =
        repository.query().where("name", Operators.NOT_IN, "Borja").findAll();

    assertThat(results).extracting(TestCustomer::getName).doesNotContain("Borja");
  }

  // -- Nested association + count --

  @Test
  void shouldFilterByNestedAssociationAndCount() {
    long count =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .where("profile.city", Operators.EQUALS, "Madrid")
            .count();

    assertThat(count).isEqualTo(1);
  }

  // -- Nested OR group + page --

  @Test
  void shouldHandleNestedOrGroupAndPageResults() {
    var page =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .or(
                group ->
                    group
                        .where("name", Operators.EQUALS, "John")
                        .where("profile.city", Operators.EQUALS, "Barcelona"))
            .leftJoin("profile")
            .findAll(PageRequest.of(0, 10));

    assertThat(page.getContent()).hasSize(1);
  }

  // -- Nested AND group --

  @Test
  void shouldHandleNestedAndGroup() {
    List<TestCustomer> results =
        repository
            .query()
            .and(
                group ->
                    group
                        .where("name", Operators.STARTS_WITH, "B")
                        .where("status", Operators.EQUALS, "ACTIVE"))
            .findAll();

    assertThat(results).hasSize(1).first().extracting(TestCustomer::getName).isEqualTo("Borja");
  }

  // -- Distinct --

  @Test
  void shouldReturnDistinctResults() {
    long count = repository.query().where("status", Operators.IS_NOT_NULL, null).distinct().count();

    assertThat(count).isEqualTo(3);
  }

  // -- findAll with sorting --

  @Test
  void shouldSortResults() {
    List<TestCustomer> results =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by(Sort.Direction.DESC, "name"))
            .findAll();

    assertThat(results).extracting(TestCustomer::getName).containsExactly("Lucia", "Borja");
  }

  // -- findOne --

  @Test
  void shouldFindOneResult() {
    Optional<TestCustomer> result =
        repository.query().where("name", Operators.EQUALS, "Borja").findOne();

    assertThat(result).isPresent().get().extracting(TestCustomer::getName).isEqualTo("Borja");
  }

  @Test
  void shouldReturnEmptyOptionalWhenNoMatch() {
    Optional<TestCustomer> result =
        repository.query().where("name", Operators.EQUALS, "NonExistent").findOne();

    assertThat(result).isEmpty();
  }

  // -- plan() --

  @Test
  void shouldReturnQueryPlanViaPlan() {
    QueryPlan<TestCustomer> plan =
        repository.query().where("name", Operators.EQUALS, "Borja").plan();

    assertThat(plan).isNotNull();
    assertThat(plan.entityType()).isEqualTo(TestCustomer.class);
  }

  // -- Pageable with sort on pageable --

  @Test
  void shouldUsePageableSortWhenProvided() {
    Page<TestCustomer> page =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "name")));

    assertThat(page.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Borja", "Lucia");
  }

  // -- Pageable without sort falls back to plan sort --

  @Test
  void shouldUsePlanSortWhenPageableUnsorted() {
    Page<TestCustomer> page =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by(Sort.Direction.DESC, "name"))
            .findAll(PageRequest.of(0, 10));

    assertThat(page.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Lucia", "Borja");
  }

  // -- Pageable with pagination --

  @Test
  void shouldPageResults() {
    Page<TestCustomer> page =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .findAll(PageRequest.of(0, 2, Sort.by("name")));

    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getTotalElements()).isEqualTo(3);
    assertThat(page.getTotalPages()).isEqualTo(2);
  }

  // -- includeNulls --

  @Test
  void shouldIncludeNullsWhenRequested() {
    List<TestCustomer> results =
        repository.query().where("status", Operators.EQUALS, "ACTIVE", false, true).findAll();

    // Should find ACTIVE (2) + null status (1) = 3
    assertThat(results).hasSize(3);
  }

  // -- Fluent API override methods return SpecificationExecutableQuery --

  @Test
  void shouldChainFluentMethods() {
    List<TestCustomer> results =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .leftJoin("profile")
            .innerJoin("profile")
            .rightJoin("profile")
            .leftFetch("profile")
            .innerFetch("profile")
            .rightFetch("profile")
            .groupBy("name", "status")
            .select("name")
            .distinct()
            .sort(Sort.by("name"))
            .findAll();

    // This tests that all fluent methods return SpecificationExecutableQuery
    assertThat(results).isNotNull();
  }

  // -- findAll and count with QueryPlan directly --

  @Test
  void shouldFindAllWithQueryPlan() {
    QueryPlan<TestCustomer> plan =
        repository.query().where("status", Operators.EQUALS, "ACTIVE").plan();

    List<TestCustomer> results = repository.findAll(plan);

    assertThat(results).hasSize(2);
  }

  @Test
  void shouldCountWithQueryPlan() {
    QueryPlan<TestCustomer> plan =
        repository.query().where("status", Operators.EQUALS, "ACTIVE").plan();

    long count = repository.count(plan);

    assertThat(count).isEqualTo(2);
  }

  @Test
  void shouldFindOneWithQueryPlan() {
    QueryPlan<TestCustomer> plan =
        repository.query().where("name", Operators.EQUALS, "Borja").plan();

    Optional<TestCustomer> result = repository.findOne(plan);

    assertThat(result).isPresent();
  }

  @Test
  void shouldFindAllPagedWithQueryPlan() {
    QueryPlan<TestCustomer> plan =
        repository.query().where("status", Operators.IS_NOT_NULL, null).plan();

    Page<TestCustomer> page = repository.findAll(plan, PageRequest.of(0, 2));

    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  // -- No conditions --

  @Test
  void shouldFindAllWhenNoConditions() {
    List<TestCustomer> results = repository.query().findAll();

    assertThat(results).hasSize(4);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableJpaRepositories(
      basePackageClasses = TestCustomerRepository.class,
      repositoryBaseClass = SpecificationRepositoryImpl.class)
  @EntityScan(basePackageClasses = TestCustomer.class)
  static class TestConfiguration {}
}
