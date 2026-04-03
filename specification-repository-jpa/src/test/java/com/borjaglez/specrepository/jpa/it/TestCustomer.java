package com.borjaglez.specrepository.jpa.it;

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

  @ManyToOne(cascade = CascadeType.ALL)
  private TestProfile profile;

  public TestCustomer() {}

  public TestCustomer(String name, String status, TestProfile profile) {
    this.name = name;
    this.status = status;
    this.profile = profile;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }
}
