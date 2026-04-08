package com.borjaglez.specrepository.jpa.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;

import com.borjaglez.specrepository.jpa.spi.OperatorHandler;
import com.borjaglez.specrepository.jpa.spi.ValueConverter;

public final class SpecificationRepositoryConfiguration {
  private final List<OperatorHandler> operatorHandlers;
  private final List<ValueConverter> valueConverters;
  private final PathResolver pathResolver;
  private final QueryPlanSpecificationFactory specificationFactory;

  private SpecificationRepositoryConfiguration(
      List<OperatorHandler> operatorHandlers,
      List<ValueConverter> valueConverters,
      PathResolver pathResolver,
      QueryPlanSpecificationFactory specificationFactory) {
    this.operatorHandlers = List.copyOf(operatorHandlers);
    this.valueConverters = List.copyOf(valueConverters);
    this.pathResolver = pathResolver;
    this.specificationFactory = specificationFactory;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static SpecificationRepositoryConfiguration defaultConfiguration() {
    return builder().addDefaultValueConverters().addDefaultOperatorHandlers().build();
  }

  public List<OperatorHandler> operatorHandlers() {
    return operatorHandlers;
  }

  public List<ValueConverter> valueConverters() {
    return valueConverters;
  }

  public PathResolver pathResolver() {
    return pathResolver;
  }

  public QueryPlanSpecificationFactory specificationFactory() {
    return specificationFactory;
  }

  public static final class Builder {
    private final List<OperatorHandler> operatorHandlers = new ArrayList<>();
    private final List<ValueConverter> valueConverters = new ArrayList<>();
    private ConversionService conversionService = new DefaultFormattingConversionService();
    private PathResolver pathResolver = new PathResolver();
    private boolean pathResolverConfigured;
    private QueryPlanSpecificationFactory specificationFactory;

    public Builder addDefaultOperatorHandlers() {
      operatorHandlers.addAll(DefaultOperatorHandlers.defaults());
      return this;
    }

    public Builder addOperatorHandler(OperatorHandler operatorHandler) {
      operatorHandlers.add(
          Objects.requireNonNull(operatorHandler, "operatorHandler must not be null"));
      return this;
    }

    public Builder operatorHandlers(Collection<OperatorHandler> operatorHandlers) {
      clearOperatorHandlers();
      operatorHandlers.forEach(this::addOperatorHandler);
      return this;
    }

    public Builder clearOperatorHandlers() {
      operatorHandlers.clear();
      return this;
    }

    public Builder addDefaultValueConverters() {
      valueConverters.addAll(DefaultValueConverters.defaults());
      return this;
    }

    public Builder addValueConverter(ValueConverter valueConverter) {
      valueConverters.add(
          Objects.requireNonNull(valueConverter, "valueConverter must not be null"));
      return this;
    }

    public Builder valueConverters(Collection<ValueConverter> valueConverters) {
      clearValueConverters();
      valueConverters.forEach(this::addValueConverter);
      return this;
    }

    public Builder clearValueConverters() {
      valueConverters.clear();
      return this;
    }

    public Builder conversionService(ConversionService conversionService) {
      this.conversionService =
          Objects.requireNonNull(conversionService, "conversionService must not be null");
      return this;
    }

    public Builder pathResolver(PathResolver pathResolver) {
      this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
      this.pathResolverConfigured = true;
      return this;
    }

    public Builder specificationFactory(QueryPlanSpecificationFactory specificationFactory) {
      this.specificationFactory =
          Objects.requireNonNull(specificationFactory, "specificationFactory must not be null");
      return this;
    }

    public SpecificationRepositoryConfiguration build() {
      PathResolver configuredPathResolver = pathResolver;
      QueryPlanSpecificationFactory configuredSpecificationFactory = specificationFactory;
      if (configuredSpecificationFactory == null) {
        configuredSpecificationFactory =
            new QueryPlanSpecificationFactory(
                new OperatorRegistry(operatorHandlers),
                new ValueConversionService(conversionService, valueConverters),
                configuredPathResolver);
      } else {
        if (pathResolverConfigured
            && configuredPathResolver != configuredSpecificationFactory.pathResolver()) {
          throw new IllegalArgumentException(
              "pathResolver must match specificationFactory.pathResolver");
        }
        configuredPathResolver = configuredSpecificationFactory.pathResolver();
      }
      return new SpecificationRepositoryConfiguration(
          operatorHandlers,
          valueConverters,
          configuredPathResolver,
          configuredSpecificationFactory);
    }
  }
}
