package com.borjaglez.specrepository.jpa.it;

import java.time.LocalDate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

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
}
