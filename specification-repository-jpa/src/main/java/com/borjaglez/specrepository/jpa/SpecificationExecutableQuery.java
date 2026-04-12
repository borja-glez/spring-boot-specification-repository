package com.borjaglez.specrepository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.ConditionGroupBuilder;
import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.QueryPlanBuilder;
import com.borjaglez.specrepository.core.SubqueryBuilder;

public class SpecificationExecutableQuery<T> extends QueryPlanBuilder<T> {
  private final SpecificationRepository<T, ?> repository;

  public SpecificationExecutableQuery(
      Class<T> entityType, SpecificationRepository<T, ?> repository) {
    super(entityType);
    this.repository = repository;
  }

  @Override
  public SpecificationExecutableQuery<T> where(
      String field, FilterOperator operator, Object value) {
    super.where(field, operator, value);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> where(
      String field,
      FilterOperator operator,
      Object value,
      boolean ignoreCase,
      boolean includeNulls) {
    super.where(field, operator, value, ignoreCase, includeNulls);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> and(Consumer<ConditionGroupBuilder<T>> nested) {
    super.and(nested);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> or(Consumer<ConditionGroupBuilder<T>> nested) {
    super.or(nested);
    return this;
  }

  @Override
  public <S> SpecificationExecutableQuery<T> exists(
      String associationPath, Consumer<SubqueryBuilder<S>> body) {
    super.exists(associationPath, body);
    return this;
  }

  @Override
  public <S> SpecificationExecutableQuery<T> notExists(
      String associationPath, Consumer<SubqueryBuilder<S>> body) {
    super.notExists(associationPath, body);
    return this;
  }

  @Override
  public <S> SpecificationExecutableQuery<T> exists(
      Class<S> subEntity, Consumer<SubqueryBuilder<S>> body) {
    super.exists(subEntity, body);
    return this;
  }

  @Override
  public <S> SpecificationExecutableQuery<T> notExists(
      Class<S> subEntity, Consumer<SubqueryBuilder<S>> body) {
    super.notExists(subEntity, body);
    return this;
  }

  @Override
  public <S> SpecificationExecutableQuery<T> inSubquery(
      String outerField,
      Class<S> subEntity,
      String subSelectField,
      Consumer<SubqueryBuilder<S>> body) {
    super.inSubquery(outerField, subEntity, subSelectField, body);
    return this;
  }

  @Override
  public <S> SpecificationExecutableQuery<T> notInSubquery(
      String outerField,
      Class<S> subEntity,
      String subSelectField,
      Consumer<SubqueryBuilder<S>> body) {
    super.notInSubquery(outerField, subEntity, subSelectField, body);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> leftJoin(String... paths) {
    super.leftJoin(paths);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> innerJoin(String... paths) {
    super.innerJoin(paths);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> rightJoin(String... paths) {
    super.rightJoin(paths);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> leftFetch(String... paths) {
    super.leftFetch(paths);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> innerFetch(String... paths) {
    super.innerFetch(paths);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> rightFetch(String... paths) {
    super.rightFetch(paths);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> groupBy(String... fields) {
    super.groupBy(fields);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> select(String... fields) {
    super.select(fields);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> sum(String field) {
    super.sum(field);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> avg(String field) {
    super.avg(field);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> min(String field) {
    super.min(field);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> max(String field) {
    super.max(field);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> count(String field) {
    super.count(field);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> sort(Sort sort) {
    super.sort(sort);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> allowedFields(AllowedFieldsPolicy allowedFieldsPolicy) {
    super.allowedFields(allowedFieldsPolicy);
    return this;
  }

  @Override
  public SpecificationExecutableQuery<T> distinct() {
    super.distinct();
    return this;
  }

  @Override
  public <P> ProjectedSpecificationExecutableQuery<T, P> selectInto(Class<P> projectionType) {
    super.selectIntoInternal(projectionType);
    return new ProjectedSpecificationExecutableQuery<>(this, repository);
  }

  public List<T> findAll() {
    return repository.findAll(build());
  }

  public Page<T> findAll(Pageable pageable) {
    return repository.findAll(build(), pageable);
  }

  public Slice<T> findSlice(Pageable pageable) {
    return repository.findSlice(build(), pageable);
  }

  public Optional<T> findOne() {
    return repository.findOne(build());
  }

  public long count() {
    return repository.count(build());
  }

  public QueryPlan<T> plan() {
    return build();
  }
}
