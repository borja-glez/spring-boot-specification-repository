package com.borjaglez.specrepository.jpa.it;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

/** Entity with an {@link EmbeddedId} and a collection to fetch. */
@Entity
public class TestEmbeddedIdCustomer {
  @EmbeddedId private Key id;

  private String name;

  @OneToMany(cascade = CascadeType.ALL)
  private List<TestProfile> addresses = new ArrayList<>();

  public TestEmbeddedIdCustomer() {}

  public TestEmbeddedIdCustomer(String region, Integer number, String name, String... cities) {
    this.id = new Key(region, number);
    this.name = name;
    for (String city : cities) {
      addresses.add(new TestProfile(city));
    }
  }

  public String getName() {
    return name;
  }

  public List<TestProfile> getAddresses() {
    return addresses;
  }

  @Embeddable
  public static class Key implements Serializable {
    private String region;
    private Integer number;

    public Key() {}

    public Key(String region, Integer number) {
      this.region = region;
      this.number = number;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(region, key.region)
          && Objects.equals(number, key.number);
    }

    @Override
    public int hashCode() {
      return Objects.hash(region, number);
    }
  }
}
