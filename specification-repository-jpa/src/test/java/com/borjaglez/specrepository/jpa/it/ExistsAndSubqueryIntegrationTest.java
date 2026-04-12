package com.borjaglez.specrepository.jpa.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.SpecificationRepositoryImpl;

@DataJpaTest
@ContextConfiguration(classes = ExistsAndSubqueryIntegrationTest.TestConfiguration.class)
class ExistsAndSubqueryIntegrationTest {
  @Autowired private TestCustomerRepository customers;
  @Autowired private TestOrderRepository orders;

  @BeforeEach
  void setUp() {
    orders.deleteAll();
    customers.deleteAll();

    TestCustomer borja =
        new TestCustomer(
            "Borja", "ACTIVE", 25, LocalDate.of(2024, 1, 10), new TestProfile("Madrid"));
    borja.addOrder(new TestOrder(new BigDecimal("150.00"), "PAID", true, borja));
    borja.addOrder(new TestOrder(new BigDecimal("80.00"), "PAID", false, borja));

    TestCustomer lucia =
        new TestCustomer(
            "Lucia", "ACTIVE", 32, LocalDate.of(2024, 2, 15), new TestProfile("Barcelona"));
    lucia.addOrder(new TestOrder(new BigDecimal("20.00"), "CANCELLED", false, lucia));

    TestCustomer john =
        new TestCustomer(
            "John", "INACTIVE", 41, LocalDate.of(2024, 3, 20), new TestProfile("Madrid"));
    john.addOrder(new TestOrder(new BigDecimal("500.00"), "PAID", true, john));

    TestCustomer anna = new TestCustomer("Anna", "ACTIVE", 19, LocalDate.of(2024, 4, 5), null);

    customers.save(borja);
    customers.save(lucia);
    customers.save(john);
    customers.save(anna);
  }

  @Test
  void shouldFilterByExistsOnAssociation() {
    List<TestCustomer> results =
        customers
            .query()
            .<TestOrder>exists("orders", sub -> sub.where("total", Operators.GREATER_THAN, "100"))
            .findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "John");
  }

  @Test
  void shouldFilterByNotExistsOnAssociation() {
    List<TestCustomer> results =
        customers
            .query()
            .<TestOrder>notExists(
                "orders", sub -> sub.where("status", Operators.EQUALS, "CANCELLED"))
            .findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "John", "Anna");
  }

  @Test
  void shouldCombineSubqueryWithNormalPredicateInOrGroup() {
    List<TestCustomer> results =
        customers
            .query()
            .or(
                group ->
                    group
                        .where("status", Operators.EQUALS, "INACTIVE")
                        .<TestOrder>exists(
                            "orders", sub -> sub.where("vip", Operators.EQUALS, true)))
            .findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "John");
  }

  @Test
  void shouldFilterByExistsEntityBasedCorrelation() {
    List<TestCustomer> results =
        customers
            .query()
            .exists(
                TestOrder.class,
                sub ->
                    sub.correlate("id", "customer.id")
                        .where("status", Operators.EQUALS, "PAID")
                        .where("vip", Operators.EQUALS, true))
            .findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "John");
  }

  @Test
  void shouldFilterByNotExistsEntityBasedCorrelation() {
    List<TestCustomer> results =
        customers
            .query()
            .notExists(
                TestOrder.class,
                sub ->
                    sub.correlate("id", "customer.id")
                        .where("status", Operators.EQUALS, "CANCELLED"))
            .findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "John", "Anna");
  }

  @Test
  void shouldFilterByInSubquery() {
    List<TestCustomer> results =
        customers
            .query()
            .inSubquery(
                "id",
                TestOrder.class,
                "customer.id",
                sub -> sub.where("vip", Operators.EQUALS, true))
            .findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "John");
  }

  @Test
  void shouldFilterByNotInSubquery() {
    List<TestCustomer> results =
        customers
            .query()
            .notInSubquery(
                "id",
                TestOrder.class,
                "customer.id",
                sub -> sub.where("vip", Operators.EQUALS, true))
            .findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Lucia", "Anna");
  }

  @Test
  void shouldHandleNestedGroupInsideSubqueryBody() {
    List<TestCustomer> results =
        customers
            .query()
            .<TestOrder>exists(
                "orders",
                sub ->
                    sub.or(
                        group ->
                            group
                                .where("total", Operators.GREATER_THAN, "400")
                                .and(
                                    inner ->
                                        inner
                                            .where("status", Operators.EQUALS, "PAID")
                                            .where("vip", Operators.EQUALS, false))))
            .findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "John");
  }

  @Test
  void shouldKeepOuterRegistryIsolatedFromSubquery() {
    List<TestCustomer> results =
        customers
            .query()
            .where("profile.city", Operators.EQUALS, "Madrid")
            .<TestOrder>exists("orders", sub -> sub.where("status", Operators.EQUALS, "PAID"))
            .distinct()
            .findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "John");
  }

  @Test
  void shouldRejectDisallowedOuterFieldInCorrelate() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name", "status"), Set.of("name"));

    assertThatThrownBy(
            () ->
                customers
                    .query()
                    .allowedFields(policy)
                    .exists(
                        TestOrder.class,
                        sub ->
                            sub.correlate("id", "customer.id")
                                .where("status", Operators.EQUALS, "PAID"))
                    .findAll())
        .isInstanceOf(DisallowedFieldException.class)
        .hasMessageContaining("'id'");
  }

  @Test
  void shouldRejectDisallowedOuterFieldInInSubquery() {
    AllowedFieldsPolicy policy = AllowedFieldsPolicy.of(Set.of("name", "status"), Set.of("name"));

    assertThatThrownBy(
            () ->
                customers
                    .query()
                    .allowedFields(policy)
                    .inSubquery(
                        "id",
                        TestOrder.class,
                        "customer.id",
                        sub -> sub.where("vip", Operators.EQUALS, true))
                    .findAll())
        .isInstanceOf(DisallowedFieldException.class)
        .hasMessageContaining("'id'");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableJpaRepositories(
      basePackageClasses = TestCustomerRepository.class,
      repositoryBaseClass = SpecificationRepositoryImpl.class)
  @EntityScan(basePackageClasses = TestCustomer.class)
  static class TestConfiguration {}
}
