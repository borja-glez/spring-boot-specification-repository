package com.borjaglez.specrepository.jpa.it;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class TestProfile {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String city;

  public TestProfile() {}

  public TestProfile(String city) {
    this.city = city;
  }

  public String getCity() {
    return city;
  }
}
