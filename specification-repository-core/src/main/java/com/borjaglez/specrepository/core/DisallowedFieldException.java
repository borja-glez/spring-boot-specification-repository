package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.STABLE;

import org.apiguardian.api.API;

@API(status = STABLE, since = "1.0.0")
public class DisallowedFieldException extends IllegalArgumentException {
  private final String field;
  private final String usage;

  public DisallowedFieldException(String field, String usage) {
    super("Field '" + field + "' is not allowed for " + usage);
    this.field = field;
    this.usage = usage;
  }

  public String field() {
    return field;
  }

  public String usage() {
    return usage;
  }
}
