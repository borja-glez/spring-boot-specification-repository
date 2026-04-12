package com.borjaglez.specrepository.http;

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
