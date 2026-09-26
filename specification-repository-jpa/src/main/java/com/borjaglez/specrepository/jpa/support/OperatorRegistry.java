package com.borjaglez.specrepository.jpa.support;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;

public class OperatorRegistry {
  private final Map<FilterOperator, OperatorHandler> handlers = new LinkedHashMap<>();

  public OperatorRegistry(Collection<OperatorHandler> handlers) {
    handlers.forEach(this::register);
  }

  public void register(OperatorHandler handler) {
    handlers.put(handler.operator(), handler);
  }

  /** Returns the handler registered for {@code operator}, or empty when there is none. */
  public Optional<OperatorHandler> find(FilterOperator operator) {
    return Optional.ofNullable(handlers.get(operator));
  }

  /**
   * Returns the handler registered for {@code operator}.
   *
   * @throws IllegalStateException when no handler is registered for it
   */
  public OperatorHandler get(FilterOperator operator) {
    return find(operator)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No operator handler registered for " + operator.value()));
  }
}
