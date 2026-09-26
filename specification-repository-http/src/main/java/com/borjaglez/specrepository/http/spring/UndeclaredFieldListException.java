package com.borjaglez.specrepository.http.spring;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.borjaglez.specrepository.core.DisallowedFieldException;

/**
 * Thrown by {@link QueryPlanArgumentResolver} when a request filters or sorts through a {@link
 * FilterableQuery} parameter that declares no field list for that usage ({@link
 * FilterableQuery#filterableFields()} or {@link FilterableQuery#sortableFields()}) and does not set
 * {@link FilterableQuery#allowAllFields()}.
 *
 * <p>It is a {@link DisallowedFieldException}, so existing handlers for that type still apply.
 * Without such a handler, Spring MVC answers with HTTP 400.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UndeclaredFieldListException extends DisallowedFieldException {

  private final String attribute;

  public UndeclaredFieldListException(String field, String usage, String attribute) {
    super(field, usage);
    this.attribute = attribute;
  }

  /**
   * Returns the {@link FilterableQuery} attribute that is not declared: {@code filterableFields} or
   * {@code sortableFields}.
   */
  public String attribute() {
    return attribute;
  }

  @Override
  public String getMessage() {
    return super.getMessage()
        + ": @FilterableQuery declares no "
        + attribute
        + ". Declare "
        + attribute
        + ", or set allowAllFields = true to allow every field.";
  }
}
