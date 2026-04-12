package com.borjaglez.specrepository.jpa.it;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class TestOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private BigDecimal total;

  private String status;

  private boolean vip;

  @ManyToOne private TestCustomer customer;

  public TestOrder() {}

  public TestOrder(BigDecimal total, String status, boolean vip, TestCustomer customer) {
    this.total = total;
    this.status = status;
    this.vip = vip;
    this.customer = customer;
  }

  public Long getId() {
    return id;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public String getStatus() {
    return status;
  }

  public boolean isVip() {
    return vip;
  }

  public TestCustomer getCustomer() {
    return customer;
  }
}
