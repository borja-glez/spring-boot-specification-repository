package com.borjaglez.specrepository.boot3;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class ExtensionTestProduct {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  private UUID externalId;

  public ExtensionTestProduct() {}

  public ExtensionTestProduct(String name, UUID externalId) {
    this.name = name;
    this.externalId = externalId;
  }

  public String getName() {
    return name;
  }

  public UUID getExternalId() {
    return externalId;
  }
}
