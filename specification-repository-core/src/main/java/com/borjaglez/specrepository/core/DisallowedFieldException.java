package com.borjaglez.specrepository.core;

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
