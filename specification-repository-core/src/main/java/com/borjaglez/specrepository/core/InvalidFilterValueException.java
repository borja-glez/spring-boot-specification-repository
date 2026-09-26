package com.borjaglez.specrepository.core;

/**
 * Thrown when a filter value cannot be converted to the Java type of its field, for example {@code
 * "abc"} on an {@code Integer} field, an unknown enum constant, or an unparsable date. {@link
 * #field()} is the condition's field, {@link #value()} the value (or list element) that failed,
 * {@link #targetType()} the type it was converted to, and the conversion error is the cause.
 *
 * <p>Through a Spring Data repository proxy it arrives as the cause of an {@code
 * InvalidDataAccessApiUsageException}.
 */
public class InvalidFilterValueException extends InvalidFilterException {
  private final transient Object value;
  private final Class<?> targetType;

  public InvalidFilterValueException(
      String field, Object value, Class<?> targetType, Throwable cause) {
    super(field, "cannot convert '" + value + "' to " + targetType.getSimpleName(), cause);
    this.value = value;
    this.targetType = targetType;
  }

  public Object value() {
    return value;
  }

  public Class<?> targetType() {
    return targetType;
  }
}
