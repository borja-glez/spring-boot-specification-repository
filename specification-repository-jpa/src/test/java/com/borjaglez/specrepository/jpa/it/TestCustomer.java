package com.borjaglez.specrepository.jpa.it;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity
public class TestCustomer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  private String status;

  private Integer age;

  private LocalDate createdAt;

  @ManyToOne(cascade = CascadeType.ALL)
  private TestProfile profile;

  @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
  private List<TestOrder> orders = new ArrayList<>();

  public TestCustomer() {}

  public TestCustomer(String name, String status, TestProfile profile) {
    this(name, status, 0, LocalDate.now(), profile);
  }

  public TestCustomer(
      String name, String status, Integer age, LocalDate createdAt, TestProfile profile) {
    this.name = name;
    this.status = status;
    this.age = age;
    this.createdAt = createdAt;
    this.profile = profile;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public Integer getAge() {
    return age;
  }

  public LocalDate getCreatedAt() {
    return createdAt;
  }

  public Long getId() {
    return id;
  }

  public List<TestOrder> getOrders() {
    return orders;
  }

  public void addOrder(TestOrder order) {
    orders.add(order);
  }
}
