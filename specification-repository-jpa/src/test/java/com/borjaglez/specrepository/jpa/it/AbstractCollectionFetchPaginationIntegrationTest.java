package com.borjaglez.specrepository.jpa.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

/**
 * Paginated plans that fetch a collection, run with {@code
 * hibernate.query.fail_on_pagination_over_collection_fetch}: Hibernate throws instead of paginating
 * in memory, so every test here proves that the page is cut by the database. Run on H2 by {@link
 * CollectionFetchPaginationIntegrationTest} and on PostgreSQL by {@link
 * CollectionFetchPaginationPostgresIntegrationTest}.
 */
@Transactional
@TestPropertySource(
    properties =
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true")
abstract class AbstractCollectionFetchPaginationIntegrationTest {

  @Autowired private TestCustomerRepository customers;
  @Autowired private TestOrderRepository orders;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    orders.deleteAll();
    customers.deleteAll();
    customer("Anna", "Madrid", "10 PAID", "20 PENDING");
    customer("Borja", "Barcelona", "5 PAID");
    customer("Carla", null);
    customer("David", "Madrid", "7 PAID", "8 PAID", "9 PENDING");
    customer("Elena", "Valencia", "30 PENDING");
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void pagesCustomersWithTheirOrdersInTheDatabase() {
    Page<TestCustomer> first =
        customers.query().leftFetch("orders").sort(Sort.by("name")).findAll(PageRequest.of(0, 2));
    Page<TestCustomer> last =
        customers.query().leftFetch("orders").sort(Sort.by("name")).findAll(PageRequest.of(2, 2));

    assertThat(first.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Anna", "Borja");
    assertThat(first.getTotalElements()).isEqualTo(5);
    assertThat(first.getTotalPages()).isEqualTo(3);
    assertThat(first.getContent())
        .allSatisfy(c -> assertThat(Hibernate.isInitialized(c.getOrders())).isTrue())
        .extracting(c -> c.getOrders().size())
        .containsExactly(2, 1);
    assertThat(last.getContent()).extracting(TestCustomer::getName).containsExactly("Elena");
    assertThat(last.hasNext()).isFalse();
  }

  @Test
  void usesThePageableSortOverThePlanSort() {
    Page<TestCustomer> page =
        customers
            .query()
            .leftFetch("orders")
            .sort(Sort.by("name"))
            .findAll(PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "name")));

    assertThat(page.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Carla", "Borja");
    assertThat(page.getTotalElements()).isEqualTo(5);
  }

  @Test
  void slicesCustomersWithTheirOrdersInTheDatabase() {
    Slice<TestCustomer> first =
        customers.query().leftFetch("orders").sort(Sort.by("name")).findSlice(PageRequest.of(0, 2));
    Slice<TestCustomer> last =
        customers.query().leftFetch("orders").sort(Sort.by("name")).findSlice(PageRequest.of(2, 2));

    assertThat(first.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Anna", "Borja");
    assertThat(first.hasNext()).isTrue();
    assertThat(first.getContent())
        .allSatisfy(c -> assertThat(Hibernate.isInitialized(c.getOrders())).isTrue());
    assertThat(last.getContent()).extracting(TestCustomer::getName).containsExactly("Elena");
    assertThat(last.hasNext()).isFalse();
  }

  @Test
  void findsOneCustomerWithItsOrdersReadingASingleRoot() {
    Statistics statistics = statistics();
    try {
      Optional<TestCustomer> found =
          customers
              .query()
              .leftFetch("orders")
              .sort(Sort.by(Sort.Direction.DESC, "name"))
              .findOne();

      assertThat(found)
          .get()
          .satisfies(c -> assertThat(Hibernate.isInitialized(c.getOrders())).isTrue())
          .extracting(TestCustomer::getName)
          .isEqualTo("Elena");
      assertThat(statistics.getEntityStatistics(TestCustomer.class.getName()).getLoadCount())
          .isEqualTo(1);
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  @Test
  void findsNoCustomerWhenNothingMatches() {
    assertThat(
            customers
                .query()
                .leftFetch("orders")
                .where("name", Operators.EQUALS, "Nobody")
                .findOne())
        .isEmpty();
  }

  @Test
  void sortsByAnAssociationWhileFilteringOnTheFetchedCollection() {
    QueryPlan<TestCustomer> plan =
        SpecificationQueryBuilder.forEntity(TestCustomer.class)
            .leftFetch("orders")
            .where("orders.status", Operators.EQUALS, "PAID")
            .sort(Sort.by("profile.city", "name"))
            .build();

    Page<TestCustomer> first = customers.findAll(plan, PageRequest.of(0, 2));
    Slice<TestCustomer> second = customers.findSlice(plan, PageRequest.of(1, 2));
    Page<TestCustomer> sortedByPageable =
        customers.findAll(
            plan, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "profile.city", "name")));

    assertThat(first.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Borja", "Anna");
    assertThat(first.getTotalElements()).isEqualTo(3);
    assertThat(first.getContent())
        .allSatisfy(c -> assertThat(Hibernate.isInitialized(c.getOrders())).isTrue());
    assertThat(second.getContent()).extracting(TestCustomer::getName).containsExactly("David");
    assertThat(second.hasNext()).isFalse();
    assertThat(sortedByPageable.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("David", "Anna");
  }

  @Test
  void pagesADistinctPlanSortedByAnAssociation() {
    Page<TestCustomer> page =
        customers
            .query()
            .leftFetch("orders")
            .distinct()
            .where("profile.city", Operators.IS_NOT_NULL, null)
            .sort(Sort.by("profile.city", "name"))
            .findAll(PageRequest.of(0, 2));

    assertThat(page.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Borja", "Anna");
    assertThat(page.getTotalElements()).isEqualTo(4);
  }

  @Test
  void sortsIgnoringCaseByAnAssociation() {
    Page<TestCustomer> page =
        customers
            .query()
            .leftFetch("orders")
            .where("orders.status", Operators.EQUALS, "PENDING")
            .findAll(
                PageRequest.of(
                    0,
                    2,
                    Sort.by(Sort.Order.desc("profile.city").ignoreCase(), Sort.Order.asc("name"))));

    assertThat(page.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("Elena", "Anna");
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void returnsTheSameContentAsTheUnpagedQuery() {
    Supplier<QueryPlan<TestCustomer>> plan =
        () ->
            SpecificationQueryBuilder.forEntity(TestCustomer.class)
                .leftFetch("orders")
                .leftFetch("profile")
                .where("orders.total", Operators.GREATER_THAN, new BigDecimal("6"))
                .sort(Sort.by(Sort.Direction.DESC, "name"))
                .build();
    List<String> all = customers.findAll(plan.get()).stream().map(TestCustomer::getName).toList();

    List<String> paged =
        java.util.stream.IntStream.range(0, 3)
            .mapToObj(page -> customers.findAll(plan.get(), PageRequest.of(page, 1)))
            .flatMap(page -> page.getContent().stream())
            .map(TestCustomer::getName)
            .toList();

    assertThat(all).containsExactly("Elena", "David", "Anna");
    assertThat(paged).isEqualTo(all);
  }

  @Test
  void keepsTheRootsFilteredOutByAnInnerFetch() {
    Page<TestCustomer> page =
        customers.query().innerFetch("orders").sort(Sort.by("name")).findAll(PageRequest.of(1, 2));

    assertThat(page.getContent())
        .extracting(TestCustomer::getName)
        .containsExactly("David", "Elena");
  }

  @Test
  void pagesByANestedCollectionFetch() {
    Slice<TestOrder> slice =
        orders
            .query()
            .leftFetch("customer.orders")
            .where("customer.orders.status", Operators.EQUALS, "PENDING")
            .sort(Sort.by("total"))
            .findSlice(PageRequest.of(0, 2));

    assertThat(slice.getContent())
        .extracting(TestOrder::getTotal)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("7"), new BigDecimal("8"));
    assertThat(slice.hasNext()).isTrue();
    assertThat(slice.getContent())
        .allSatisfy(o -> assertThat(Hibernate.isInitialized(o.getCustomer().getOrders())).isTrue());
  }

  @Test
  void returnsAnEmptyPageWithoutLoadingEntities() {
    Statistics statistics = statistics();
    try {
      Slice<TestCustomer> empty =
          customers
              .query()
              .leftFetch("orders")
              .where("name", Operators.EQUALS, "Nobody")
              .findSlice(PageRequest.of(0, 2));
      Page<TestCustomer> beyondTheEnd =
          customers.query().leftFetch("orders").findAll(PageRequest.of(5, 2));

      assertThat(empty.getContent()).isEmpty();
      assertThat(empty.hasNext()).isFalse();
      assertThat(beyondTheEnd.getContent()).isEmpty();
      assertThat(beyondTheEnd.getTotalElements()).isEqualTo(5);
      assertThat(statistics.getEntityLoadCount()).isZero();
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  @Test
  void pagesACollectionFetchWithOneQueryForTheIdsAndOneForTheEntities() {
    Statistics statistics = statistics();
    try {
      customers.query().leftFetch("orders").leftFetch("profile").findSlice(PageRequest.of(0, 2));

      assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  @Test
  void keepsASingleQueryWhenOnlyAToOneAssociationIsFetched() {
    Statistics statistics = statistics();
    try {
      Slice<TestCustomer> slice =
          customers
              .query()
              .leftFetch("profile")
              .sort(Sort.by("name"))
              .findSlice(PageRequest.of(0, 2));
      Optional<TestCustomer> one = customers.query().leftFetch("profile").findOne();

      assertThat(slice.getContent())
          .extracting(TestCustomer::getName)
          .containsExactly("Anna", "Borja");
      assertThat(one).isPresent();
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  @Test
  void keepsASingleQueryWithoutPagination() {
    Statistics statistics = statistics();
    try {
      List<TestCustomer> all = customers.query().leftFetch("orders").leftFetch("profile").findAll();

      assertThat(all).hasSize(5);
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  /**
   * Statistics counted from here on. The profile is an eager to-one association: tests that count
   * statements fetch it, so loading it does not add one statement per customer.
   */
  private Statistics statistics() {
    entityManager.clear();
    Statistics statistics =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    return statistics;
  }

  /** Saves a customer with its orders, each written as {@code "<total> <status>"}. */
  private void customer(String name, String city, String... placed) {
    TestCustomer customer =
        new TestCustomer(
            name,
            "ACTIVE",
            30,
            LocalDate.of(2024, 1, 1),
            city == null ? null : new TestProfile(city));
    for (String order : placed) {
      String[] parts = order.split(" ");
      customer.addOrder(new TestOrder(new BigDecimal(parts[0]), parts[1], false, customer));
    }
    customers.save(customer);
  }
}
