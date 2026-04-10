package com.borjaglez.specrepository.jpa.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
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
    repository.save(
        new TestCustomer(
            "Borja", "ACTIVE", 25, LocalDate.of(2024, 1, 10), new TestProfile("Madrid")));
    repository.save(
        new TestCustomer(
            "Lucia", "ACTIVE", 32, LocalDate.of(2024, 2, 15), new TestProfile("Barcelona")));
    repository.save(
        new TestCustomer(
            "John", "INACTIVE", 41, LocalDate.of(2024, 3, 20), new TestProfile("Madrid")));
    repository.save(new TestCustomer("Anna", null, 19, LocalDate.of(2024, 4, 5), null));
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

  // -- BETWEEN --

  @Test
  void shouldFilterByBetweenForNumericRange() {
    List<TestCustomer> results =
        repository.query().where("age", Operators.BETWEEN, List.of("20", "35")).findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "Lucia");
  }

  @Test
  void shouldFilterByBetweenForDateRange() {
    List<TestCustomer> results =
        repository
            .query()
            .where("createdAt", Operators.BETWEEN, List.of("2024-02-01", "2024-03-31"))
            .findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Lucia", "John");
  }

  @Test
  void shouldFailFastWhenBetweenRangeIsInvalid() {
    assertThatThrownBy(
            () -> repository.query().where("age", Operators.BETWEEN, List.of("20")).findAll())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("BETWEEN operator requires exactly 2 values");
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
    QueryPlan<TestCustomer> plan =
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
            .plan();

    // This tests that all fluent methods return SpecificationExecutableQuery
    assertThat(plan).isNotNull();
  }

  @Test
  void shouldProjectSingleSelectedField() {
    List<?> results =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .select("name")
            .findAll();

    assertThat(results).extracting(Object::toString).containsExactly("Borja", "Lucia");
  }

  @Test
  void shouldProjectMultipleSelectedFields() {
    List<?> results =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .select("name", "profile.city")
            .findAll();

    assertThat(results)
        .hasSize(2)
        .allSatisfy(result -> assertThat(result).isInstanceOf(Object[].class));
    assertThat((Object[]) results.get(0)).containsExactly("Borja", "Madrid");
    assertThat((Object[]) results.get(1)).containsExactly("Lucia", "Barcelona");
  }

  @Test
  void shouldProjectSelectedFieldsIntoDto() {
    List<NameCityDto> results =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .select("name", "profile.city")
            .selectInto(NameCityDto.class)
            .findAll();

    assertThat(results)
        .extracting(NameCityDto::name, NameCityDto::city)
        .containsExactly(tuple("Borja", "Madrid"), tuple("Lucia", "Barcelona"));
  }

  @Test
  void shouldProjectGroupedAggregatesIntoRecord() {
    List<CustomerStatusSummary> results =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .sum("age")
            .selectInto(CustomerStatusSummary.class)
            .findAll();

    assertThat(results)
        .containsExactly(
            new CustomerStatusSummary("ACTIVE", 2L, 57),
            new CustomerStatusSummary("INACTIVE", 1L, 41));
  }

  @Test
  void shouldProjectPagedResultsIntoDto() {
    Page<NameOnlyRecord> page =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .sort(Sort.by("name"))
            .select("name")
            .selectInto(NameOnlyRecord.class)
            .findAll(PageRequest.of(0, 2));

    assertThat(page.getContent())
        .containsExactly(new NameOnlyRecord("Borja"), new NameOnlyRecord("John"));
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void shouldProjectPagedResults() {
    Page<?> page =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .sort(Sort.by("name"))
            .select("name")
            .findAll(PageRequest.of(0, 2));

    assertThat(page.getContent()).extracting(Object::toString).containsExactly("Borja", "John");
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void shouldProjectPagedResultsUsingPageableSort() {
    Page<?> page =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .select("name")
            .findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "name")));

    assertThat(page.getContent()).extracting(Object::toString).containsExactly("Lucia", "Borja");
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void shouldProjectPagedResultsWithoutAnySort() {
    Page<?> page =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .select("name")
            .findAll(PageRequest.of(0, 10));

    assertThat(page.getContent()).hasSize(3);
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void shouldReturnSumAggregate() {
    Optional<?> result =
        repository.query().where("status", Operators.IS_NOT_NULL, null).sum("age").findOne();

    assertThat(result).hasValueSatisfying(value -> assertThat(value).isEqualTo(98));
  }

  @Test
  void shouldReturnAverageAggregate() {
    Optional<?> result = repository.query().avg("age").findOne();

    assertThat(result)
        .hasValueSatisfying(
            value -> assertThat((Double) value).isEqualTo((25D + 32D + 41D + 19D) / 4D));
  }

  @Test
  void shouldReturnMinimumComparableAggregate() {
    Optional<?> result = repository.query().min("createdAt").findOne();

    assertThat(result)
        .hasValueSatisfying(value -> assertThat(value).isEqualTo(LocalDate.of(2024, 1, 10)));
  }

  @Test
  void shouldReturnMinimumNumericAggregate() {
    Optional<?> result = repository.query().min("age").findOne();

    assertThat(result).hasValueSatisfying(value -> assertThat(value).isEqualTo(19));
  }

  @Test
  void shouldReturnMaximumAggregate() {
    Optional<?> result = repository.query().max("age").findOne();

    assertThat(result).hasValueSatisfying(value -> assertThat(value).isEqualTo(41));
  }

  @Test
  void shouldReturnMaximumComparableAggregate() {
    Optional<?> result = repository.query().max("createdAt").findOne();

    assertThat(result)
        .hasValueSatisfying(value -> assertThat(value).isEqualTo(LocalDate.of(2024, 4, 5)));
  }

  @Test
  void shouldReturnFieldLevelCountAggregate() {
    Optional<?> result = repository.query().count("status").findOne();

    assertThat(result).hasValueSatisfying(value -> assertThat(value).isEqualTo(3L));
  }

  @Test
  void shouldReturnGroupedAggregates() {
    List<?> results =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .sum("age")
            .findAll();

    assertThat(results)
        .hasSize(2)
        .allSatisfy(result -> assertThat(result).isInstanceOf(Object[].class));
    assertThat((Object[]) results.get(0)).containsExactly("ACTIVE", 2L, 57);
    assertThat((Object[]) results.get(1)).containsExactly("INACTIVE", 1L, 41);
  }

  @Test
  void shouldPageGroupedAggregateResults() {
    Page<?> page =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .findAll(PageRequest.of(0, 1));

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void shouldPageNonGroupedAggregateResultsAsSingleRow() {
    Page<?> page = repository.query().sum("age").findAll(PageRequest.of(0, 10));

    assertThat(page.getContent())
        .singleElement()
        .satisfies(value -> assertThat(((Number) value).intValue()).isEqualTo(117));
    assertThat(page.getTotalElements()).isEqualTo(1);
  }

  @Test
  void shouldRejectNonNumericSumField() {
    assertThatThrownBy(() -> repository.query().sum("status").findAll())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SUM requires a numeric field: status");
  }

  @Test
  void shouldRejectNonComparableMinimumField() {
    assertThatThrownBy(() -> repository.query().min("profile").findAll())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MIN requires a comparable field");
  }

  @Test
  void shouldRejectNonComparableMaximumField() {
    assertThatThrownBy(() -> repository.query().max("profile").findAll())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MAX requires a comparable field");
  }

  @Test
  void shouldProjectFindOneResult() {
    Optional<?> result =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .select("name")
            .findOne();

    assertThat(result).hasValueSatisfying(value -> assertThat(value).isEqualTo("Borja"));
  }

  @Test
  void shouldProjectFindOneIntoRecord() {
    Optional<NameOnlyRecord> result =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .select("name")
            .selectInto(NameOnlyRecord.class)
            .findOne();

    assertThat(result).hasValue(new NameOnlyRecord("Borja"));
  }

  @Test
  void shouldExposeProjectionTypeOnProjectedPlan() {
    QueryPlan<TestCustomer> plan =
        repository.query().select("name", "profile.city").selectInto(NameCityDto.class).plan();

    assertThat(plan.projectionType()).isEqualTo(NameCityDto.class);
  }

  @Test
  void shouldCountGroupsAfterFiltering() {
    long count =
        repository.query().where("status", Operators.IS_NOT_NULL, null).groupBy("status").count();

    assertThat(count).isEqualTo(2);
  }

  @Test
  void shouldCountGroupedQueryPlan() {
    QueryPlan<TestCustomer> plan =
        repository.query().where("status", Operators.IS_NOT_NULL, null).groupBy("status").plan();

    long count = repository.count(plan);

    assertThat(count).isEqualTo(2);
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
  @SuppressWarnings({"rawtypes", "unchecked"})
  void shouldFindAllWithProjectedQueryPlanMetadata() {
    QueryPlan<TestCustomer> plan =
        repository.query().select("name").selectInto(NameOnlyRecord.class).plan();

    List<?> results = repository.findAll((QueryPlan) plan);

    assertThat(results)
        .extracting(Object::toString)
        .containsExactly(
            "NameOnlyRecord[name=Borja]",
            "NameOnlyRecord[name=Lucia]",
            "NameOnlyRecord[name=John]",
            "NameOnlyRecord[name=Anna]");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void shouldFindAllPagedWithProjectedQueryPlanMetadata() {
    QueryPlan<TestCustomer> plan =
        repository
            .query()
            .sort(Sort.by("name"))
            .select("name")
            .selectInto(NameOnlyRecord.class)
            .plan();

    Page<?> page = repository.findAll((QueryPlan) plan, PageRequest.of(0, 2));

    assertThat(page.getContent())
        .extracting(Object::toString)
        .containsExactly("NameOnlyRecord[name=Anna]", "NameOnlyRecord[name=Borja]");
    assertThat(page.getTotalElements()).isEqualTo(4);
  }

  @Test
  void shouldFindOneWithQueryPlan() {
    QueryPlan<TestCustomer> plan =
        repository.query().where("name", Operators.EQUALS, "Borja").plan();

    Optional<TestCustomer> result = repository.findOne(plan);

    assertThat(result).isPresent();
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void shouldFindOneWithProjectedQueryPlanMetadata() {
    QueryPlan<TestCustomer> plan =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .select("name")
            .selectInto(NameOnlyRecord.class)
            .plan();

    Optional<?> result = repository.findOne((QueryPlan) plan);

    assertThat(result)
        .hasValueSatisfying(value -> assertThat(value).isEqualTo(new NameOnlyRecord("Borja")));
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

  // -- Allowed fields policy --

  @Test
  void shouldFilterWithAllowedFieldsPolicy() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name", "status"), Set.of("name"));

    List<TestCustomer> results =
        repository
            .query()
            .allowedFields(policy)
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .findAll();

    assertThat(results).hasSize(2);
  }

  @Test
  void shouldRejectDisallowedFilterFieldWithPolicy() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name"), Set.of("name"));

    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .allowedFields(policy)
                    .where("status", Operators.EQUALS, "ACTIVE")
                    .findAll())
        .isInstanceOf(DisallowedFieldException.class)
        .hasMessage("Field 'status' is not allowed for filtering");
  }

  @Test
  void shouldRejectDisallowedSortFieldWithPolicy() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name"), Set.of("name"));

    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .allowedFields(policy)
                    .where("name", Operators.EQUALS, "Borja")
                    .sort(Sort.by("status"))
                    .findAll())
        .isInstanceOf(DisallowedFieldException.class)
        .hasMessage("Field 'status' is not allowed for sorting");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableJpaRepositories(
      basePackageClasses = TestCustomerRepository.class,
      repositoryBaseClass = SpecificationRepositoryImpl.class)
  @EntityScan(basePackageClasses = TestCustomer.class)
  static class TestConfiguration {}

  private record NameOnlyRecord(String name) {}

  private record CustomerStatusSummary(String status, Long customerCount, Integer totalAge) {}

  public static final class NameCityDto {
    private final String name;
    private final String city;

    public NameCityDto(String name, String city) {
      this.name = name;
      this.city = city;
    }

    public String name() {
      return name;
    }

    public String city() {
      return city;
    }
  }
}
