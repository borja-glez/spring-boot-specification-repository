package com.borjaglez.specrepository.jpa.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.specrepository.core.AggregateFunction;
import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.InvalidFilterException;
import com.borjaglez.specrepository.core.InvalidFilterValueException;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;

/**
 * Database-neutral test bodies, run on H2 by {@link SpecificationRepositoryIntegrationTest} and on
 * PostgreSQL by {@link SpecificationRepositoryPostgresIntegrationTest}.
 *
 * <p>{@code @Transactional} is repeated here because Spring resolves the test transaction from the
 * class that declares the test method, so the one in {@code @DataJpaTest} on the subclasses would
 * not apply to the methods inherited from this class.
 */
@Transactional
abstract class AbstractSpecificationRepositoryIntegrationTest {
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
  void shouldTreatEqualsNullAsIsNull() {
    assertThat(repository.query().where("status", Operators.EQUALS, null).findAll())
        .extracting(TestCustomer::getName)
        .containsExactly("Anna");
    assertThat(repository.query().where("status", Operators.NOT_EQUALS, null).count()).isEqualTo(3);
  }

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

  // -- LIKE wildcards in the search term are literals --

  @Test
  void shouldMatchPercentAndUnderscoreInTheSearchTermLiterally() {
    saveCustomersWithLikeWildcards();

    assertThat(names(Operators.CONTAINS, "%")).containsExactlyInAnyOrder("100% cotton", "save 50%");
    assertThat(names(Operators.CONTAINS, "_")).containsExactly("pack_saver");
    assertThat(names(Operators.STARTS_WITH, "_")).isEmpty();
    assertThat(names(Operators.ENDS_WITH, "%")).containsExactly("save 50%");
    assertThat(names(Operators.NOT_CONTAINS, "%"))
        .containsExactlyInAnyOrder("Borja", "Lucia", "John", "Anna", "pack_saver", "C:\\data");
  }

  @Test
  void shouldMatchTheEscapeCharacterInTheSearchTermLiterally() {
    saveCustomersWithLikeWildcards();

    assertThat(names(Operators.CONTAINS, "\\")).containsExactly("C:\\data");
    assertThat(names(Operators.CONTAINS, ":\\d")).containsExactly("C:\\data");
  }

  private void saveCustomersWithLikeWildcards() {
    repository.save(new TestCustomer("100% cotton", "ACTIVE", null));
    repository.save(new TestCustomer("save 50%", "ACTIVE", null));
    repository.save(new TestCustomer("pack_saver", "ACTIVE", null));
    repository.save(new TestCustomer("C:\\data", "ACTIVE", null));
  }

  private List<String> names(FilterOperator operator, String term) {
    return repository.query().where("name", operator, term).findAll().stream()
        .map(TestCustomer::getName)
        .toList();
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

  @org.springframework.beans.factory.annotation.Autowired
  private jakarta.persistence.EntityManager fetchEntityManager;

  @Test
  void shouldStillFetchAnAssociationThatIsAlsoJoined() {
    fetchEntityManager.flush();
    fetchEntityManager.clear();

    List<TestCustomer> found =
        repository
            .query()
            .leftJoin("orders")
            .leftFetch("orders")
            .where("name", Operators.EQUALS, "Borja")
            .findAll();

    assertThat(found)
        .singleElement()
        .satisfies(c -> assertThat(org.hibernate.Hibernate.isInitialized(c.getOrders())).isTrue());
  }

  @Test
  void shouldReturnAndCountEachCustomerOnceWhenSeveralOfTheirOrdersMatch() {
    TestCustomer borja =
        repository.query().where("name", Operators.EQUALS, "Borja").findOne().orElseThrow();
    borja.getOrders().add(new TestOrder(new java.math.BigDecimal("10.00"), "PAID", false, borja));
    borja.getOrders().add(new TestOrder(new java.math.BigDecimal("20.00"), "PAID", false, borja));
    repository.save(borja);

    var plan =
        com.borjaglez.specrepository.core.SpecificationQueryBuilder.forEntity(TestCustomer.class)
            .where("orders.status", Operators.EQUALS, "PAID")
            .build();

    assertThat(repository.findAll(plan)).extracting(TestCustomer::getName).containsExactly("Borja");
    assertThat(repository.count(plan)).isEqualTo(1);
    assertThat(repository.findAll(plan, org.springframework.data.domain.PageRequest.of(0, 10)))
        .extracting(TestCustomer::getName)
        .containsExactly("Borja");
  }

  @org.springframework.beans.factory.annotation.Autowired private TestOrderRepository orders;

  @Test
  void shouldKeepTheRowsOfAProjectionOverACollection() {
    TestCustomer borja =
        repository.query().where("name", Operators.EQUALS, "Borja").findOne().orElseThrow();
    borja.getOrders().add(new TestOrder(new java.math.BigDecimal("10.00"), "PAID", false, borja));
    borja.getOrders().add(new TestOrder(new java.math.BigDecimal("20.00"), "PAID", false, borja));
    repository.save(borja);

    List<GroupedRow> names =
        repository
            .query()
            .select("name")
            .where("orders.status", Operators.EQUALS, "PAID")
            .findRows();

    assertThat(names).hasSize(2);
  }

  @Test
  void shouldReturnEachRootOnceWhenACollectionIsReachedThroughAnotherAssociation() {
    TestCustomer borja =
        repository.query().where("name", Operators.EQUALS, "Borja").findOne().orElseThrow();
    TestOrder first = new TestOrder(new java.math.BigDecimal("10.00"), "PAID", false, borja);
    borja.getOrders().add(first);
    borja.getOrders().add(new TestOrder(new java.math.BigDecimal("20.00"), "PAID", false, borja));
    repository.save(borja);

    var plan =
        com.borjaglez.specrepository.core.SpecificationQueryBuilder.forEntity(TestOrder.class)
            .where("customer.orders.status", Operators.EQUALS, "PAID")
            .where("total", Operators.EQUALS, new java.math.BigDecimal("10.00"))
            .build();

    assertThat(orders.findAll(plan)).hasSize(1);
    assertThat(orders.count(plan)).isEqualTo(1);
  }

  @Test
  void shouldKeepAnInnerJoinWhenTheSamePathIsFetchedWithALeftJoin() {
    List<TestCustomer> found = repository.query().innerJoin("orders").leftFetch("orders").findAll();

    // Nobody has orders in the fixtures: the inner join still filters every customer out.
    assertThat(found).isEmpty();
  }

  @Test
  void shouldStillSortByAnAssociationWhenAFilterCrossesACollection() {
    TestCustomer borja =
        repository.query().where("name", Operators.EQUALS, "Borja").findOne().orElseThrow();
    borja.getOrders().add(new TestOrder(new java.math.BigDecimal("10.00"), "PAID", false, borja));
    repository.save(borja);

    var plan =
        com.borjaglez.specrepository.core.SpecificationQueryBuilder.forEntity(TestCustomer.class)
            .where("orders.status", Operators.EQUALS, "PAID")
            .sort(org.springframework.data.domain.Sort.by("profile.city"))
            .build();

    assertThat(repository.findAll(plan)).extracting(TestCustomer::getName).containsExactly("Borja");
    assertThat(
            repository.findAll(
                plan,
                org.springframework.data.domain.PageRequest.of(
                    0, 10, org.springframework.data.domain.Sort.by("profile.city"))))
        .extracting(TestCustomer::getName)
        .containsExactly("Borja");
  }

  // -- count with innerFetch --

  private void giveOrders(String name, int count) {
    TestCustomer customer =
        repository.query().where("name", Operators.EQUALS, name).findOne().orElseThrow();
    for (int i = 0; i < count; i++) {
      customer
          .getOrders()
          .add(
              new TestOrder(
                  new BigDecimal("10.00").add(BigDecimal.valueOf(i)), "PAID", false, customer));
    }
    repository.save(customer);
    fetchEntityManager.flush();
    fetchEntityManager.clear();
  }

  @Test
  void shouldCountOnlyTheRootsKeptByAnInnerFetchOfACollection() {
    giveOrders("Borja", 2);
    giveOrders("Lucia", 1);

    var plan = repository.query().innerFetch("orders");

    assertThat(plan.findAll())
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "Lucia");
    assertThat(plan.count()).isEqualTo(2);
    Page<TestCustomer> page = plan.findAll(PageRequest.of(0, 1, Sort.by("name")));
    assertThat(page.getContent()).extracting(TestCustomer::getName).containsExactly("Borja");
    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getTotalPages()).isEqualTo(2);
  }

  @Test
  void shouldCountOnlyTheRootsKeptByAnInnerFetchOfASingleAssociation() {
    var plan = repository.query().innerFetch("profile");

    assertThat(plan.findAll()).hasSize(3);
    assertThat(plan.count()).isEqualTo(3);
    Page<TestCustomer> page = plan.findAll(PageRequest.of(0, 2, Sort.by("name")));
    assertThat(page.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Borja", "John");
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void shouldNotJoinTwiceWhenAnInnerFetchedPathIsAlsoFilteredOn() {
    giveOrders("Borja", 2);
    giveOrders("Lucia", 1);

    var plan =
        repository
            .query()
            .innerJoin("orders")
            .innerFetch("orders")
            .where("orders.total", Operators.EQUALS, new BigDecimal("11.00"));

    assertThat(plan.findAll()).extracting(TestCustomer::getName).containsExactly("Borja");
    assertThat(plan.count()).isEqualTo(1);
    assertThat(plan.findAll(PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
  }

  @Test
  void shouldKeepCountingEveryRootWithALeftFetch() {
    giveOrders("Borja", 2);

    var plan = repository.query().leftFetch("orders").leftFetch("profile");

    assertThat(plan.findAll()).hasSize(4);
    assertThat(plan.count()).isEqualTo(4);
    assertThat(plan.findAll(PageRequest.of(0, 3)).getTotalElements()).isEqualTo(4);
  }

  // -- findOne --

  @Test
  void shouldFindOneResult() {
    Optional<TestCustomer> result =
        repository.query().where("name", Operators.EQUALS, "Borja").findOne();

    assertThat(result).isPresent().get().extracting(TestCustomer::getName).isEqualTo("Borja");
  }

  @org.springframework.beans.factory.annotation.Autowired
  private jakarta.persistence.EntityManager entityManager;

  @Test
  void shouldReadOnlyTheFirstRowForFindOne() {
    entityManager.flush();
    entityManager.clear();
    org.hibernate.stat.Statistics statistics =
        entityManager
            .getEntityManagerFactory()
            .unwrap(org.hibernate.SessionFactory.class)
            .getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    try {
      Optional<TestCustomer> first =
          repository
              .query()
              .where("status", Operators.EQUALS, "ACTIVE")
              .sort(org.springframework.data.domain.Sort.by("name"))
              .findOne();

      assertThat(first).get().extracting(TestCustomer::getName).isEqualTo("Borja");
      assertThat(statistics.getEntityStatistics(TestCustomer.class.getName()).getLoadCount())
          .isEqualTo(1);
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  @Test
  void shouldFindOneWithAFetchedCollection() {
    Optional<TestCustomer> result =
        repository.query().leftFetch("orders").where("name", Operators.EQUALS, "Borja").findOne();

    assertThat(result).get().extracting(TestCustomer::getName).isEqualTo("Borja");
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

  // -- Slice pagination (no count query) --

  @Test
  void shouldSliceResultsWithNextPage() {
    Slice<TestCustomer> slice =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .sort(Sort.by("name"))
            .findSlice(PageRequest.of(0, 2));

    assertThat(slice.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Borja", "John");
    assertThat(slice.hasNext()).isTrue();
    assertThat(slice.getNumberOfElements()).isEqualTo(2);
  }

  @Test
  void shouldSliceLastPage() {
    Slice<TestCustomer> slice =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .sort(Sort.by("name"))
            .findSlice(PageRequest.of(1, 2));

    assertThat(slice.getContent()).extracting(TestCustomer::getName).containsExactly("Lucia");
    assertThat(slice.hasNext()).isFalse();
  }

  @Test
  void shouldSliceEmptyResults() {
    Slice<TestCustomer> slice =
        repository
            .query()
            .where("status", Operators.EQUALS, "NOPE")
            .findSlice(PageRequest.of(0, 5));

    assertThat(slice.getContent()).isEmpty();
    assertThat(slice.hasNext()).isFalse();
  }

  @Test
  void shouldSliceUsingPageableSortOverridingPlanSort() {
    Slice<TestCustomer> slice =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by(Sort.Direction.ASC, "name"))
            .findSlice(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "name")));

    assertThat(slice.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Lucia", "Borja");
    assertThat(slice.hasNext()).isFalse();
  }

  @Test
  void shouldProjectSlicedResultsIntoDto() {
    Slice<NameOnlyRecord> slice =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .sort(Sort.by("name"))
            .select("name")
            .selectInto(NameOnlyRecord.class)
            .findSlice(PageRequest.of(0, 2));

    assertThat(slice.getContent())
        .containsExactly(new NameOnlyRecord("Borja"), new NameOnlyRecord("John"));
    assertThat(slice.hasNext()).isTrue();
  }

  @Test
  void shouldSliceGroupedAggregateResults() {
    Slice<StatusCount> slice =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .selectInto(StatusCount.class)
            .findSlice(PageRequest.of(0, 1));

    assertThat(slice.getContent()).containsExactly(new StatusCount("ACTIVE", 2L));
    assertThat(slice.hasNext()).isTrue();
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
    List<GroupedRow> results =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .select("name")
            .findRows();

    assertThat(results).extracting(row -> row.get("name")).containsExactly("Borja", "Lucia");
  }

  @Test
  void shouldProjectMultipleSelectedFields() {
    List<GroupedRow> results =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .select("name", "profile.city")
            .findRows();

    assertThat(results).hasSize(2);
    assertThat(results.get(0).columns()).containsExactly("name", "profile.city");
    assertThat(results.get(0).values()).containsExactly("Borja", "Madrid");
    assertThat(results.get(1).values()).containsExactly("Lucia", "Barcelona");
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
  void shouldProjectPagedResultsUsingPageableSort() {
    Page<NameOnlyRecord> page =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .select("name")
            .selectInto(NameOnlyRecord.class)
            .findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "name")));

    assertThat(page.getContent())
        .containsExactly(new NameOnlyRecord("Lucia"), new NameOnlyRecord("Borja"));
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void shouldProjectPagedResultsWithoutAnySort() {
    Page<NameOnlyRecord> page =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .select("name")
            .selectInto(NameOnlyRecord.class)
            .findAll(PageRequest.of(0, 10));

    assertThat(page.getContent()).hasSize(3);
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void shouldReturnSumAggregate() {
    Optional<GroupedRow> result =
        repository.query().where("status", Operators.IS_NOT_NULL, null).sum("age").findRow();

    assertThat(result).hasValueSatisfying(row -> assertThat(row.get("SUM_age")).isEqualTo(98));
  }

  @Test
  void shouldReturnAverageAggregate() {
    Optional<GroupedRow> result = repository.query().avg("age").findRow();

    assertThat(result)
        .hasValueSatisfying(
            row -> assertThat((Double) row.get(0)).isEqualTo((25D + 32D + 41D + 19D) / 4D));
  }

  @Test
  void shouldReturnMinimumComparableAggregate() {
    Optional<GroupedRow> result = repository.query().min("createdAt").findRow();

    assertThat(result)
        .hasValueSatisfying(row -> assertThat(row.get(0)).isEqualTo(LocalDate.of(2024, 1, 10)));
  }

  @Test
  void shouldReturnMinimumNumericAggregate() {
    Optional<GroupedRow> result = repository.query().min("age").findRow();

    assertThat(result).hasValueSatisfying(row -> assertThat(row.get(0)).isEqualTo(19));
  }

  @Test
  void shouldReturnMaximumAggregate() {
    Optional<GroupedRow> result = repository.query().max("age").findRow();

    assertThat(result).hasValueSatisfying(row -> assertThat(row.get(0)).isEqualTo(41));
  }

  @Test
  void shouldReturnMaximumComparableAggregate() {
    Optional<GroupedRow> result = repository.query().max("createdAt").findRow();

    assertThat(result)
        .hasValueSatisfying(row -> assertThat(row.get(0)).isEqualTo(LocalDate.of(2024, 4, 5)));
  }

  @Test
  void shouldReturnFieldLevelCountAggregate() {
    Optional<GroupedRow> result = repository.query().count("status").findRow();

    assertThat(result).hasValueSatisfying(row -> assertThat(row.get(0)).isEqualTo(3L));
  }

  @Test
  void shouldReturnGroupedAggregates() {
    List<GroupedRow> results =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .sum("age")
            .findRows();

    assertThat(results).hasSize(2);
    assertThat(results.get(0).columns()).containsExactly("status", "COUNT_id", "SUM_age");
    assertThat(results.get(0).values()).containsExactly("ACTIVE", 2L, 57);
    assertThat(results.get(1).values()).containsExactly("INACTIVE", 1L, 41);
  }

  @Test
  void shouldPageGroupedAggregateResults() {
    Page<StatusCount> page =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .selectInto(StatusCount.class)
            .findAll(PageRequest.of(0, 1));

    assertThat(page.getContent()).containsExactly(new StatusCount("ACTIVE", 2L));
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void shouldPageNonGroupedAggregateResultsAsSingleRow() {
    Page<TotalAge> page =
        repository.query().sum("age").selectInto(TotalAge.class).findAll(PageRequest.of(0, 10));

    assertThat(page.getContent())
        .singleElement()
        .satisfies(value -> assertThat(value.total().intValue()).isEqualTo(117));
    assertThat(page.getTotalElements()).isEqualTo(1);
  }

  @Test
  void shouldRejectNonNumericSumField() {
    assertThatThrownBy(() -> repository.query().sum("status").findRows())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SUM requires a numeric field: status");
  }

  @Test
  void shouldRejectNonComparableMinimumField() {
    assertThatThrownBy(() -> repository.query().min("profile").findRows())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MIN requires a comparable field");
  }

  @Test
  void shouldRejectNonComparableMaximumField() {
    assertThatThrownBy(() -> repository.query().max("profile").findRows())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MAX requires a comparable field");
  }

  @Test
  void shouldProjectFindOneResult() {
    Optional<GroupedRow> result =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .select("name")
            .findRow();

    assertThat(result).hasValueSatisfying(row -> assertThat(row.get(0)).isEqualTo("Borja"));
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

  // -- Selections without selectInto are read as rows, never as entities --

  private static final String SELECTION_READ_AS_ENTITY =
      "The query selects fields or aggregates, so it returns rows, not entities:"
          + " read it with findRows() or findRow(), or map it with selectInto(...)";

  @Test
  void shouldRejectReadingSelectedFieldsAsEntities() {
    var query = repository.query().select("name");

    assertThatThrownBy(query::findAll)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(SELECTION_READ_AS_ENTITY);
    assertThatThrownBy(() -> query.findAll(PageRequest.of(0, 2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(SELECTION_READ_AS_ENTITY);
    assertThatThrownBy(() -> query.findSlice(PageRequest.of(0, 2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(SELECTION_READ_AS_ENTITY);
    assertThatThrownBy(query::findOne)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(SELECTION_READ_AS_ENTITY);
  }

  @Test
  void shouldRejectReadingAPlanWithSelectionsAsEntities() {
    // Through the repository proxy, Spring translates the IllegalStateException.
    QueryPlan<TestCustomer> plan = repository.query().sum("age").plan();

    assertThatThrownBy(() -> repository.findAll(plan))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessage(SELECTION_READ_AS_ENTITY);
    assertThatThrownBy(() -> repository.findAll(plan, PageRequest.of(0, 2)))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessage(SELECTION_READ_AS_ENTITY);
    assertThatThrownBy(() -> repository.findSlice(plan, PageRequest.of(0, 2)))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessage(SELECTION_READ_AS_ENTITY);
    assertThatThrownBy(() -> repository.findOne(plan))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessage(SELECTION_READ_AS_ENTITY);
  }

  @Test
  void shouldReadASingleScalarAggregateAsOneRow() {
    Optional<GroupedRow> row =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sumAs("total", "age")
            .findRow();

    assertThat(row).hasValueSatisfying(value -> assertThat(value.get("total")).isEqualTo(57));
  }

  @Test
  void shouldReadOnlyTheFirstRow() {
    Optional<GroupedRow> row =
        repository.query().sort(Sort.by("name")).select("name", "status").findRow();

    assertThat(row)
        .hasValueSatisfying(value -> assertThat(value.values()).containsExactly("Anna", null));
  }

  @Test
  void shouldReadNoRowWhenNothingMatches() {
    Optional<GroupedRow> row =
        repository.query().where("name", Operators.EQUALS, "Nobody").select("name").findRow();

    assertThat(row).isEmpty();
  }

  @Test
  void shouldFindRowsWithQueryPlanThroughProxy() {
    QueryPlan<TestCustomer> plan =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .sort(Sort.by("name"))
            .select("name")
            .plan();

    assertThat(repository.findRows(plan))
        .extracting(row -> row.get("name"))
        .containsExactly("Borja", "Lucia");
    assertThat(repository.findRow(plan))
        .hasValueSatisfying(row -> assertThat(row.get("name")).isEqualTo("Borja"));
  }

  @Test
  void shouldRequireSelectionsToReadRows() {
    assertThatThrownBy(() -> repository.query().findRows())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("findRows requires at least one select() or aggregate selection");
    assertThatThrownBy(() -> repository.query().findRow())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("findRow requires at least one select() or aggregate selection");
  }

  // -- Plan-based projected and grouped methods through the repository proxy --
  // Spring Data invokes default interface methods itself instead of delegating to the repository
  // base class, so these methods must reach SpecificationRepositoryImpl through the proxy.

  private QueryPlan<TestCustomer> activeNamesPlan() {
    return repository
        .query()
        .where("status", Operators.EQUALS, "ACTIVE")
        .sort(Sort.by("name"))
        .select("name")
        .selectInto(NameOnlyRecord.class)
        .plan();
  }

  @Test
  void shouldFindAllProjectedWithQueryPlanThroughProxy() {
    List<NameOnlyRecord> results = repository.findAllProjected(activeNamesPlan());

    assertThat(results).containsExactly(new NameOnlyRecord("Borja"), new NameOnlyRecord("Lucia"));
  }

  @Test
  void shouldFindProjectedPageWithQueryPlanThroughProxy() {
    Page<NameOnlyRecord> page =
        repository.findAllProjected(activeNamesPlan(), PageRequest.of(0, 1));

    assertThat(page.getContent()).containsExactly(new NameOnlyRecord("Borja"));
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void shouldFindProjectedSliceWithQueryPlanThroughProxy() {
    Slice<NameOnlyRecord> slice =
        repository.findSliceProjected(activeNamesPlan(), PageRequest.of(0, 1));

    assertThat(slice.getContent()).containsExactly(new NameOnlyRecord("Borja"));
    assertThat(slice.hasNext()).isTrue();
  }

  @Test
  void shouldFindOneProjectedWithQueryPlanThroughProxy() {
    Optional<NameOnlyRecord> result = repository.findOneProjected(activeNamesPlan());

    assertThat(result).hasValue(new NameOnlyRecord("Borja"));
  }

  @Test
  void shouldFindAllGroupedWithQueryPlanThroughProxy() {
    QueryPlan<TestCustomer> plan =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .countAs("customers", "id")
            .plan();

    List<GroupedRow> rows = repository.findAllGrouped(plan);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).get("status")).isEqualTo("ACTIVE");
    assertThat(rows.get(0).get("customers")).isEqualTo(2L);
  }

  // -- Element collections of basic values --

  @Test
  void shouldFilterByElementOfBasicCollection() {
    repository.save(new TestCustomer("Tagged", "ACTIVE", null).tagged("vip", "newsletter"));
    repository.save(new TestCustomer("Other", "ACTIVE", null).tagged("newsletter"));

    List<TestCustomer> vips = repository.query().where("tags", Operators.EQUALS, "vip").findAll();

    assertThat(vips).extracting(TestCustomer::getName).containsExactly("Tagged");
  }

  @Test
  void shouldFilterByAnyElementOfBasicCollection() {
    repository.save(new TestCustomer("Tagged", "ACTIVE", null).tagged("vip", "newsletter"));
    repository.save(new TestCustomer("Other", "ACTIVE", null).tagged("newsletter"));

    long count = repository.query().where("tags", Operators.IN, List.of("vip", "beta")).count();

    assertThat(count).isEqualTo(1);
  }

  @Test
  void shouldCheckEmptinessOfBasicCollection() {
    repository.save(new TestCustomer("Tagged", "ACTIVE", null).tagged("vip"));

    long untagged = repository.query().where("tags", Operators.IS_EMPTY, null).count();
    long tagged = repository.query().where("tags", Operators.IS_NOT_EMPTY, null).count();

    assertThat(untagged).isEqualTo(4);
    assertThat(tagged).isEqualTo(1);
  }

  @Test
  void shouldStillTreatAnEntityCollectionAsAWholeWhenItIsTheLastSegment() {
    TestCustomer withOrder = new TestCustomer("Buyer", "ACTIVE", null);
    withOrder.addOrder(new TestOrder(BigDecimal.TEN, "PAID", false, withOrder));
    repository.save(withOrder);

    long withoutOrders = repository.query().where("orders", Operators.IS_EMPTY, null).count();

    assertThat(withoutOrders).isEqualTo(4);
  }

  // -- Fetching element collections of basic values --

  @Test
  void shouldLeftFetchABasicCollection() {
    repository.save(new TestCustomer("Tagged", "ACTIVE", null).tagged("vip", "newsletter"));
    fetchEntityManager.flush();
    fetchEntityManager.clear();

    List<TestCustomer> found = repository.query().leftFetch("tags").sort(Sort.by("name")).findAll();

    assertThat(found)
        .extracting(TestCustomer::getName)
        .containsExactly("Anna", "Borja", "John", "Lucia", "Tagged");
    assertThat(found)
        .allSatisfy(c -> assertThat(org.hibernate.Hibernate.isInitialized(c.getTags())).isTrue());
    assertThat(found.get(4).getTags()).containsExactlyInAnyOrder("vip", "newsletter");
    assertThat(found.get(0).getTags()).isEmpty();
  }

  @Test
  void shouldInnerFetchABasicCollectionDroppingRootsWithoutElements() {
    repository.save(new TestCustomer("Tagged", "ACTIVE", null).tagged("vip", "newsletter"));
    repository.save(new TestCustomer("Other", "ACTIVE", null).tagged("beta"));
    fetchEntityManager.flush();
    fetchEntityManager.clear();

    List<TestCustomer> found =
        repository.query().innerFetch("tags").sort(Sort.by("name")).findAll();

    assertThat(found).extracting(TestCustomer::getName).containsExactly("Other", "Tagged");
    assertThat(found)
        .allSatisfy(c -> assertThat(org.hibernate.Hibernate.isInitialized(c.getTags())).isTrue());
    assertThat(found.get(1).getTags()).containsExactlyInAnyOrder("vip", "newsletter");
  }

  @Test
  void shouldRejectAFetchPathThatContinuesAfterABasicCollection() {
    assertThatThrownBy(() -> repository.query().leftFetch("tags.value").findAll())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tags.value");
  }

  // -- EXISTS / NOT EXISTS over element collections of basic values --

  @Test
  void shouldFilterByExistsOnBasicCollection() {
    repository.save(new TestCustomer("Tagged", "ACTIVE", null).tagged("vip", "newsletter"));
    repository.save(new TestCustomer("Other", "ACTIVE", null).tagged("newsletter"));

    List<TestCustomer> vips =
        repository
            .query()
            .<String>exists("tags", sub -> sub.where("value", Operators.EQUALS, "vip"))
            .findAll();

    assertThat(vips).extracting(TestCustomer::getName).containsExactly("Tagged");
  }

  @Test
  void shouldFilterByNotExistsOnBasicCollection() {
    repository.save(new TestCustomer("Tagged", "ACTIVE", null).tagged("vip", "newsletter"));
    repository.save(new TestCustomer("Other", "ACTIVE", null).tagged("newsletter"));

    List<TestCustomer> results =
        repository
            .query()
            .<String>notExists("tags", sub -> sub.where("value", Operators.EQUALS, "vip"))
            .findAll();
    // With the shared join, NOT_EQUALS means "has some tag other than vip".
    List<TestCustomer> someOtherTag =
        repository.query().where("tags", Operators.NOT_EQUALS, "vip").distinct().findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "Lucia", "John", "Anna", "Other");
    assertThat(someOtherTag)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Tagged", "Other");
  }

  @Test
  void shouldRequireEveryTagWithOneExistsPerElement() {
    repository.save(new TestCustomer("Both", "ACTIVE", null).tagged("vip", "beta"));
    repository.save(new TestCustomer("VipOnly", "ACTIVE", null).tagged("vip"));

    // A shared join matches nothing here; one subquery per element keeps the conditions apart.
    long sharedJoin =
        repository
            .query()
            .where("tags", Operators.EQUALS, "vip")
            .where("tags", Operators.EQUALS, "beta")
            .count();
    List<TestCustomer> both =
        repository
            .query()
            .<String>exists("tags", sub -> sub.where("value", Operators.EQUALS, "vip"))
            .<String>exists("tags", sub -> sub.where("value", Operators.EQUALS, "beta"))
            .findAll();

    assertThat(sharedJoin).isZero();
    assertThat(both).extracting(TestCustomer::getName).containsExactly("Both");
  }

  @Test
  void shouldFilterByExistsOnBasicCollectionWithNestedGroupAndNoBody() {
    repository.save(new TestCustomer("Tagged", "ACTIVE", null).tagged("vip"));
    repository.save(new TestCustomer("Beta", "ACTIVE", null).tagged("beta"));
    repository.save(new TestCustomer("Other", "ACTIVE", null).tagged("newsletter"));

    List<TestCustomer> matching =
        repository
            .query()
            .<String>exists(
                "tags",
                sub ->
                    sub.or(
                        group ->
                            group
                                .where("value", Operators.EQUALS, "vip")
                                .where("value", Operators.STARTS_WITH, "bet")))
            .findAll();
    long tagged = repository.query().<String>exists("tags", sub -> {}).count();

    assertThat(matching)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Tagged", "Beta");
    assertThat(tagged).isEqualTo(3);
  }

  @Test
  void shouldRejectAnAttributeOfABasicCollectionElementInsideExists() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .<String>exists("tags", sub -> sub.where("name", Operators.EQUALS, "vip"))
                    .findAll())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("value");
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
  void shouldFindSliceWithQueryPlan() {
    QueryPlan<TestCustomer> plan =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .sort(Sort.by("name"))
            .plan();

    Slice<TestCustomer> slice = repository.findSlice(plan, PageRequest.of(0, 2));

    assertThat(slice.getContent()).hasSize(2);
    assertThat(slice.hasNext()).isTrue();
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void shouldFindSliceWithProjectedQueryPlanMetadata() {
    QueryPlan<TestCustomer> plan =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .sort(Sort.by("name"))
            .select("name")
            .selectInto(NameOnlyRecord.class)
            .plan();

    Slice<?> slice = repository.findSlice((QueryPlan) plan, PageRequest.of(0, 2));

    assertThat(slice.getContent())
        .extracting(Object::toString)
        .containsExactly("NameOnlyRecord[name=Borja]", "NameOnlyRecord[name=John]");
    assertThat(slice.hasNext()).isTrue();
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

  // -- Unknown operators and fields --

  @Test
  void shouldRejectAnUnknownOperatorAsAnInvalidFilter() {
    assertThatThrownBy(
            () -> repository.query().where("name", Operators.custom("like"), "x").findAll())
        .isInstanceOfSatisfying(
            InvalidFilterException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("name");
              assertThat(ex.reason()).isEqualTo("unknown operator 'like'");
            });
  }

  @Test
  void shouldRejectAnUnknownOperatorInsideAnOrGroupAsAnInvalidFilter() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .where("status", Operators.EQUALS, "ACTIVE")
                    .or(group -> group.where("name", Operators.custom("like"), "x"))
                    .count())
        .isInstanceOfSatisfying(
            InvalidFilterException.class, ex -> assertThat(ex.field()).isEqualTo("name"));
  }

  @Test
  void shouldRejectAnUnknownOperatorInsideASubqueryBodyAsAnInvalidFilter() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .<String>exists(
                        "tags", sub -> sub.where("value", Operators.custom("like"), "x"))
                    .findAll())
        .isInstanceOfSatisfying(
            InvalidFilterException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("value");
              assertThat(ex.reason()).isEqualTo("unknown operator 'like'");
            });
  }

  @Test
  void shouldRejectAnUnknownFieldAsAnInvalidFilter() {
    assertThatThrownBy(
            () -> repository.query().where("doesNotExist", Operators.EQUALS, "x").findAll())
        .isInstanceOfSatisfying(
            InvalidFilterException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("doesNotExist");
              assertThat(ex.reason()).isEqualTo("unknown field");
              assertThat(ex).hasCauseInstanceOf(IllegalArgumentException.class);
            });
  }

  @Test
  void shouldRejectAnUnknownNestedFieldAsAnInvalidFilter() {
    assertThatThrownBy(
            () -> repository.query().where("profile.nope", Operators.EQUALS, "x").count())
        .isInstanceOfSatisfying(
            InvalidFilterException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("profile.nope");
              assertThat(ex.reason()).isEqualTo("unknown field");
            });
  }

  @Test
  void shouldRejectAnUnknownJoinPathAsAnInvalidFilter() {
    assertThatThrownBy(() -> repository.query().leftJoin("nope.city").findAll())
        .isInstanceOfSatisfying(
            InvalidFilterException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("nope.city");
              assertThat(ex.reason()).isEqualTo("unknown field 'nope'");
            });
  }

  @Test
  void shouldExposeAnInvalidFilterAsTheCauseThroughTheRepositoryProxy() {
    QueryPlan<TestCustomer> plan =
        repository.query().where("name", Operators.custom("like"), "x").plan();

    assertThatThrownBy(() -> repository.findAll(plan))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .cause()
        .isInstanceOfSatisfying(
            InvalidFilterException.class, ex -> assertThat(ex.field()).isEqualTo("name"));
  }

  // -- Unconvertible values --

  @Test
  void shouldRejectANonNumericValueOnANumericFieldAsAnInvalidFilterValue() {
    assertThatThrownBy(
            () -> repository.query().where("age", Operators.GREATER_THAN_OR_EQUAL, "abc").findAll())
        .isInstanceOfSatisfying(
            InvalidFilterValueException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("age");
              assertThat(ex.value()).isEqualTo("abc");
              assertThat(ex.targetType()).isEqualTo(Integer.class);
              assertThat(ex.reason()).isEqualTo("cannot convert 'abc' to Integer");
              assertThat(ex).hasCauseInstanceOf(ConversionFailedException.class);
              assertThat(ex).hasRootCauseInstanceOf(NumberFormatException.class);
            });
  }

  @Test
  void shouldRejectAnUnparsableDateAsAnInvalidFilterValue() {
    assertThatThrownBy(
            () ->
                repository.query().where("createdAt", Operators.GREATER_THAN, "yesterday").count())
        .isInstanceOfSatisfying(
            InvalidFilterValueException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("createdAt");
              assertThat(ex.value()).isEqualTo("yesterday");
              assertThat(ex.targetType()).isEqualTo(LocalDate.class);
              assertThat(ex).hasCauseInstanceOf(DateTimeParseException.class);
            });
  }

  @Test
  void shouldReportTheOffendingElementOfAnInList() {
    assertThatThrownBy(
            () -> repository.query().where("age", Operators.IN, List.of("25", "x", "41")).findAll())
        .isInstanceOfSatisfying(
            InvalidFilterValueException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("age");
              assertThat(ex.value()).isEqualTo("x");
            });
  }

  @Test
  void shouldReportTheOffendingBoundOfABetween() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .where("createdAt", Operators.BETWEEN, List.of("2024-01-01", "soon"))
                    .findAll())
        .isInstanceOfSatisfying(
            InvalidFilterValueException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("createdAt");
              assertThat(ex.value()).isEqualTo("soon");
            });
  }

  @Test
  void shouldRejectAnUnconvertibleValueInsideAnOrGroup() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .where("status", Operators.EQUALS, "ACTIVE")
                    .or(group -> group.where("age", Operators.EQUALS, "old"))
                    .count())
        .isInstanceOfSatisfying(
            InvalidFilterValueException.class, ex -> assertThat(ex.field()).isEqualTo("age"));
  }

  @Test
  void shouldRejectAnUnconvertibleValueInsideASubqueryBody() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .<TestOrder>exists(
                        "orders", sub -> sub.where("total", Operators.GREATER_THAN, "lots"))
                    .findAll())
        .isInstanceOfSatisfying(
            InvalidFilterValueException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("total");
              assertThat(ex.targetType()).isEqualTo(BigDecimal.class);
            });
  }

  @Test
  void shouldRejectAnUnconvertibleHavingValueWithTheHavingField() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .groupBy("status")
                    .select("status")
                    .avg("age")
                    .having(AggregateFunction.AVG, "age", Operators.GREATER_THAN, "abc")
                    .findAll())
        .isInstanceOfSatisfying(
            InvalidFilterValueException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("age");
              assertThat(ex.value()).isEqualTo("abc");
              assertThat(ex.targetType()).isEqualTo(Double.class);
            });
  }

  @Test
  void shouldRejectAnUnconvertibleHavingBetweenBound() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .groupBy("status")
                    .select("status")
                    .count("id")
                    .having(AggregateFunction.COUNT, "id", Operators.BETWEEN, List.of("1", "many"))
                    .findAll())
        .isInstanceOfSatisfying(
            InvalidFilterValueException.class,
            ex -> {
              assertThat(ex.field()).isEqualTo("id");
              assertThat(ex.value()).isEqualTo("many");
              assertThat(ex.targetType()).isEqualTo(Long.class);
            });
  }

  @Test
  void shouldExposeAnInvalidFilterValueAsTheCauseThroughTheRepositoryProxy() {
    QueryPlan<TestCustomer> plan = repository.query().where("age", Operators.EQUALS, "abc").plan();

    assertThatThrownBy(() -> repository.findAll(plan))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .cause()
        .isInstanceOfSatisfying(
            InvalidFilterValueException.class, ex -> assertThat(ex.field()).isEqualTo("age"));
  }

  // -- HAVING / multiple aggregates / grouped rows --

  @Test
  void shouldFilterGroupedResultsWithHavingGreaterThan() {
    List<GroupedRow> results =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .having(AggregateFunction.COUNT, "id", Operators.GREATER_THAN, 1L)
            .findRows();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).values()).containsExactly("ACTIVE", 2L);
  }

  @Test
  void shouldFilterWithMultipleHavingPredicatesAndedTogether() {
    List<GroupedRow> results =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .sum("age")
            .count("id")
            .having(AggregateFunction.SUM, "age", Operators.GREATER_THAN_OR_EQUAL, 41)
            .having(AggregateFunction.COUNT, "id", Operators.LESS_THAN_OR_EQUAL, 1L)
            .findRows();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).values()).containsExactly("INACTIVE", 41, 1L);
  }

  @Test
  void shouldSupportEveryHavingComparator() {
    assertHavingMatch(Operators.EQUALS, 2L, "ACTIVE");
    assertHavingMatch(Operators.NOT_EQUALS, 2L, "INACTIVE");
    assertHavingMatch(Operators.LESS_THAN, 2L, "INACTIVE");
    assertHavingMatch(Operators.LESS_THAN_OR_EQUAL, 1L, "INACTIVE");
    assertHavingMatch(Operators.IS_NOT_NULL, null, "ACTIVE", "INACTIVE");
  }

  @Test
  void shouldFilterWithBetweenHavingClause() {
    List<GroupedRow> results =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .having(AggregateFunction.COUNT, "id", Operators.BETWEEN, java.util.List.of(1L, 1L))
            .findRows();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).values()).containsExactly("INACTIVE", 1L);
  }

  @Test
  void shouldFilterWithHavingAgainstAvgAggregate() {
    // AVG returns Double; the test passes an Integer threshold to verify that the HAVING
    // value is converted against the aggregate result type, not the underlying field type.
    List<GroupedRow> results =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .avg("age")
            .having(AggregateFunction.AVG, "age", Operators.GREATER_THAN, 30)
            .findRows();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).get(0)).isEqualTo("INACTIVE");
  }

  @Test
  void shouldFilterWithHavingAgainstCountAggregateUsingIntegerValue() {
    // COUNT returns Long; passing an int verifies the value is converted to Long via the
    // aggregate result type rather than the underlying id column type.
    List<GroupedRow> results =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .having(AggregateFunction.COUNT, "id", Operators.GREATER_THAN_OR_EQUAL, 2)
            .findRows();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).get(0)).isEqualTo("ACTIVE");
  }

  @Test
  void shouldExecuteIsNullHavingClause() {
    // COUNT can never be NULL, so this query is expected to return zero rows; the test
    // exercises the IS_NULL branch of the HAVING translation, not a real filtering use case.
    List<GroupedRow> results =
        repository
            .query()
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .having(AggregateFunction.COUNT, "id", Operators.IS_NULL, null)
            .findRows();

    assertThat(results).isEmpty();
  }

  @Test
  void shouldFilterWithBetweenHavingUsingNonListIterable() {
    java.util.LinkedHashSet<Long> bounds = new java.util.LinkedHashSet<>();
    bounds.add(1L);
    bounds.add(2L);
    Iterable<Long> iterable = () -> bounds.iterator();
    List<GroupedRow> results =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .having(AggregateFunction.COUNT, "id", Operators.BETWEEN, iterable)
            .findRows();

    assertThat(results).hasSize(2);
  }

  @Test
  void shouldRejectBetweenHavingWithWrongValueShape() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .groupBy("status")
                    .count("id")
                    .having(AggregateFunction.COUNT, "id", Operators.BETWEEN, "not a list")
                    .findRows())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BETWEEN having requires exactly 2 values");
  }

  @Test
  void shouldRejectBetweenHavingWithWrongArity() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .groupBy("status")
                    .count("id")
                    .having(
                        AggregateFunction.COUNT,
                        "id",
                        Operators.BETWEEN,
                        java.util.List.of(1L, 2L, 3L))
                    .findRows())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BETWEEN having requires exactly 2 values");
  }

  @Test
  void shouldRejectUnsupportedHavingOperator() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .groupBy("status")
                    .count("id")
                    .having(AggregateFunction.COUNT, "id", Operators.CONTAINS, "x")
                    .findRows())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported operator for having clause: contains");
  }

  @Test
  void shouldRejectHavingWithCustomOperator() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .groupBy("status")
                    .count("id")
                    .having(AggregateFunction.COUNT, "id", FilterOperator.of("foobar"), 1L)
                    .findRows())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported operator for having clause: foobar");
  }

  @Test
  void shouldAcceptAllowedHavingFieldUnderPolicy() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("status", "id"), Set.of("status"));

    List<GroupedRow> results =
        repository
            .query()
            .allowedFields(policy)
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .having(AggregateFunction.COUNT, "id", Operators.GREATER_THAN_OR_EQUAL, 1L)
            .findRows();

    assertThat(results).hasSize(2);
  }

  @Test
  void shouldValidateHavingFieldAgainstAllowedFieldsPolicy() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("status"), Set.of("status"));

    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .allowedFields(policy)
                    .groupBy("status")
                    .count("id")
                    .having(AggregateFunction.COUNT, "id", Operators.GREATER_THAN, 1L)
                    .findRows())
        .isInstanceOf(DisallowedFieldException.class)
        .hasMessage("Field 'id' is not allowed for filtering");
  }

  @Test
  void shouldSupportMultipleAggregatesInOneQuery() {
    List<GroupedRow> rows =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .sumAs("totalAge", "age")
            .avgAs("averageAge", "age")
            .countAs("customers", "id")
            .minAs("youngest", "age")
            .maxAs("oldest", "age")
            .findAllGrouped();

    assertThat(rows).hasSize(2);
    GroupedRow active = rows.get(0);
    assertThat(active.get("status")).isEqualTo("ACTIVE");
    assertThat(active.get("totalAge")).isEqualTo(57);
    assertThat(((Number) active.get("averageAge")).doubleValue()).isEqualTo(28.5d);
    assertThat(active.get("customers")).isEqualTo(2L);
    assertThat(active.get("youngest")).isEqualTo(25);
    assertThat(active.get("oldest")).isEqualTo(32);
  }

  @Test
  void shouldReturnSingleColumnGroupedRow() {
    List<GroupedRow> rows =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .countAs("customers", "id")
            .findAllGrouped();

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).columns()).containsExactly("customers");
    assertThat(rows.get(0).get("customers")).isEqualTo(2L);
    assertThat(rows.get(1).get(0)).isEqualTo(1L);
  }

  @Test
  void shouldFallbackToDerivedColumnNameWhenAliasMissing() {
    List<GroupedRow> rows =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .findAllGrouped();

    assertThat(rows.get(0).columns()).containsExactly("status", "COUNT_id");
    assertThat(rows.get(0).get("COUNT_id")).isEqualTo(2L);
  }

  @Test
  void shouldGroupByAssociationPathUsingTheSameJoinAsTheFilter() {
    List<GroupedRow> rows =
        repository
            .query()
            .where("profile.city", Operators.IS_NOT_NULL, null)
            .groupBy("profile.city")
            .sort(Sort.by("profile.city"))
            .select("profile.city")
            .countAs("customers", "id")
            .findAllGrouped();

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).get("profile.city")).isEqualTo("Barcelona");
    assertThat(rows.get(0).get("customers")).isEqualTo(1L);
    assertThat(rows.get(1).get("profile.city")).isEqualTo("Madrid");
    assertThat(rows.get(1).get("customers")).isEqualTo(2L);
  }

  @Test
  void shouldGroupByAssociationPathWithoutFilters() {
    List<GroupedRow> rows =
        repository
            .query()
            .groupBy("profile.city")
            .select("profile.city")
            .countAs("customers", "id")
            .findAllGrouped();

    assertThat(rows).extracting(row -> row.get("customers")).containsExactlyInAnyOrder(1L, 2L, 1L);
  }

  @Test
  void shouldCountDistinctRootsWhenFilteringThroughAToManyPath() {
    TestCustomer buyer =
        repository.findAll().stream()
            .filter(c -> c.getName().equals("Borja"))
            .findFirst()
            .orElseThrow();
    buyer.addOrder(new TestOrder(BigDecimal.TEN, "PAID", false, buyer));
    buyer.addOrder(new TestOrder(BigDecimal.ONE, "PAID", false, buyer));
    repository.save(buyer);

    List<GroupedRow> rows =
        repository
            .query()
            .where("orders.status", Operators.EQUALS, "PAID")
            .groupBy("status")
            .select("status")
            .countAs("joinedRows", "id")
            .countDistinctAs("customers", "id")
            .findAllGrouped();

    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("joinedRows")).isEqualTo(2L);
              assertThat(row.get("customers")).isEqualTo(1L);
            });
  }

  @Test
  void shouldNameCountDistinctColumnsAfterFunctionAndField() {
    List<GroupedRow> rows =
        repository
            .query()
            .where("status", Operators.EQUALS, "ACTIVE")
            .groupBy("status")
            .select("status")
            .countDistinct("id")
            .findAllGrouped();

    assertThat(rows.getFirst().get("COUNT_DISTINCT_id")).isEqualTo(2L);
  }

  @Test
  void findAllGroupedShouldRequireSelections() {
    assertThatThrownBy(() -> repository.query().findAllGrouped())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("findAllGrouped requires");
  }

  @Test
  void havingWithoutGroupByShouldFailFastInBuilder() {
    assertThatThrownBy(
            () ->
                repository
                    .query()
                    .sum("age")
                    .having(AggregateFunction.SUM, "age", Operators.GREATER_THAN, 1)
                    .findRows())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("having requires at least one groupBy field");
  }

  private void assertHavingMatch(
      FilterOperator operator, Object value, String... expectedStatuses) {
    List<GroupedRow> rows =
        repository
            .query()
            .where("status", Operators.IS_NOT_NULL, null)
            .groupBy("status")
            .sort(Sort.by("status"))
            .select("status")
            .count("id")
            .having(AggregateFunction.COUNT, "id", operator, value)
            .findRows();

    assertThat(rows)
        .extracting(row -> row.get("status"))
        .containsExactly((Object[]) expectedStatuses);
  }

  // -- Grouping by a to-many association path --

  @Test
  void shouldGroupByToManyAssociationPath() {
    TestCustomer carla = repository.save(new TestCustomer("Carla", "ACTIVE", null));
    TestCustomer diego = repository.save(new TestCustomer("Diego", "ACTIVE", null));
    orders.save(new TestOrder(new BigDecimal("10.00"), "PAID", false, carla));
    orders.save(new TestOrder(new BigDecimal("20.00"), "PENDING", false, carla));
    orders.save(new TestOrder(new BigDecimal("30.00"), "PAID", true, diego));

    // PostgreSQL rejects the query when the selection and the GROUP BY use different joins.
    List<GroupedRow> rows =
        repository
            .query()
            .where("orders.status", Operators.IS_NOT_NULL, null)
            .groupBy("orders.status")
            .sort(Sort.by("orders.status"))
            .select("orders.status")
            .countAs("customers", "id")
            .findAllGrouped();

    assertThat(rows).extracting(row -> row.get("orders.status")).containsExactly("PAID", "PENDING");
    assertThat(rows).extracting(row -> row.get("customers")).containsExactly(2L, 1L);
  }

  // -- ignoreCase also ignores accents (needs the PostgreSQL unaccent extension) --

  @Test
  void ignoreCaseShouldAlsoIgnoreAccentsInTheSearchTerm() {
    repository.save(new TestCustomer("Café Ramón", "ACTIVE", null));
    repository.save(new TestCustomer("CAFE MOLIDO", "ACTIVE", null));
    repository.save(new TestCustomer("Té verde", "ACTIVE", null));

    assertThat(repository.query().where("name", Operators.CONTAINS, "café", true, false).count())
        .isEqualTo(2);
    assertThat(repository.query().where("name", Operators.EQUALS, "te verde", true, false).count())
        .isEqualTo(1);
  }

  @Test
  void ignoreCaseShouldMatchLikeWildcardsInTheSearchTermLiterally() {
    repository.save(new TestCustomer("100% Algodón", "ACTIVE", null));
    repository.save(new TestCustomer("Algodón puro", "ACTIVE", null));
    repository.save(new TestCustomer("C:\\algodón", "ACTIVE", null));

    assertThat(repository.query().where("name", Operators.CONTAINS, "%", true, false).findAll())
        .extracting(TestCustomer::getName)
        .containsExactly("100% Algodón");
    assertThat(
            repository.query().where("name", Operators.CONTAINS, "0% algodon", true, false).count())
        .isEqualTo(1);
    assertThat(repository.query().where("name", Operators.CONTAINS, "\\", true, false).findAll())
        .extracting(TestCustomer::getName)
        .containsExactly("C:\\algodón");
  }

  // -- Pagination with a collection fetch on entities with a composite id --

  @org.springframework.beans.factory.annotation.Autowired
  private TestIdClassCustomerRepository idClassCustomers;

  @org.springframework.beans.factory.annotation.Autowired
  private TestEmbeddedIdCustomerRepository embeddedIdCustomers;

  @Test
  void shouldPageAnIdClassEntityThatFetchesACollectionInOneQuery() {
    idClassCustomers.save(new TestIdClassCustomer("north", 1, "Carla", "Vigo"));
    idClassCustomers.save(new TestIdClassCustomer("north", 2, "Anna", "Madrid", "Toledo"));
    idClassCustomers.save(new TestIdClassCustomer("south", 1, "Borja"));
    entityManager.flush();
    entityManager.clear();

    Page<TestIdClassCustomer> page =
        idClassCustomers
            .query()
            .leftFetch("addresses")
            .sort(Sort.by("name"))
            .findAll(PageRequest.of(0, 2));

    assertThat(page.getContent())
        .extracting(TestIdClassCustomer::getName)
        .containsExactly("Anna", "Borja");
    assertThat(page.getContent().getFirst().getAddresses())
        .extracting(TestProfile::getCity)
        .containsExactlyInAnyOrder("Madrid", "Toledo");
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void shouldPageAnEmbeddedIdEntityThatFetchesACollectionInOneQuery() {
    embeddedIdCustomers.save(new TestEmbeddedIdCustomer("north", 1, "Carla", "Vigo"));
    embeddedIdCustomers.save(new TestEmbeddedIdCustomer("north", 2, "Anna", "Madrid", "Toledo"));
    embeddedIdCustomers.save(new TestEmbeddedIdCustomer("south", 1, "Borja"));
    entityManager.flush();
    entityManager.clear();

    Slice<TestEmbeddedIdCustomer> slice =
        embeddedIdCustomers
            .query()
            .leftFetch("addresses")
            .sort(Sort.by("name"))
            .findSlice(PageRequest.of(1, 2));

    assertThat(slice.getContent())
        .extracting(TestEmbeddedIdCustomer::getName)
        .containsExactly("Carla");
    assertThat(slice.getContent().getFirst().getAddresses())
        .extracting(TestProfile::getCity)
        .containsExactly("Vigo");
    assertThat(slice.hasNext()).isFalse();
  }

  private record NameOnlyRecord(String name) {}

  private record StatusCount(String status, Long customers) {}

  private record TotalAge(Number total) {}

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
