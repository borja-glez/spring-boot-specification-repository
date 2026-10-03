package com.borjaglez.specrepository.http;

import static org.apiguardian.api.API.Status.STABLE;

import org.apiguardian.api.API;

@API(status = STABLE, since = "1.0.0")
public class HttpUnknownOperatorException extends IllegalArgumentException {
  private final String operator;

  public HttpUnknownOperatorException(String operator) {
    super("Unknown filter operator '" + operator + "'");
    this.operator = operator;
  }

  public String operator() {
    return operator;
  }
}
