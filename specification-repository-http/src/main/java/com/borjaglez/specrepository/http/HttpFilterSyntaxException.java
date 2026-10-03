package com.borjaglez.specrepository.http;

import static org.apiguardian.api.API.Status.STABLE;

import org.apiguardian.api.API;

@API(status = STABLE, since = "1.0.0")
public class HttpFilterSyntaxException extends IllegalArgumentException {
  private final String rawExpression;
  private final String reason;

  public HttpFilterSyntaxException(String rawExpression, String reason) {
    super("Invalid filter expression '" + rawExpression + "': " + reason);
    this.rawExpression = rawExpression;
    this.reason = reason;
  }

  public String rawExpression() {
    return rawExpression;
  }

  public String reason() {
    return reason;
  }
}
