package com.borjaglez.specrepository.jpa.support;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

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

  public OperatorHandler get(FilterOperator operator) {
    OperatorHandler handler = handlers.get(operator);
    if (handler == null) {
      throw new IllegalStateException("No operator handler registered for " + operator.value());
    }
    return handler;
  }
}
