package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.STABLE;

import org.apiguardian.api.API;

/**
 * Thrown when a filter cannot be applied to the entity: for example it names a field the entity
 * does not have, or an operator with no registered handler. It is a client error, so applications
 * can map it to a 400 response.
 *
 * <p>Through a Spring Data repository proxy it arrives as the cause of an {@code
 * InvalidDataAccessApiUsageException}.
 */
@API(status = STABLE, since = "1.0.0")
public class InvalidFilterException extends IllegalArgumentException {
  private final String field;
  private final String reason;

  public InvalidFilterException(String field, String reason) {
    this(field, reason, null);
  }

  public InvalidFilterException(String field, String reason, Throwable cause) {
    super("Invalid filter on field '" + field + "': " + reason, cause);
    this.field = field;
    this.reason = reason;
  }

  public String field() {
    return field;
  }

  public String reason() {
    return reason;
  }
}
