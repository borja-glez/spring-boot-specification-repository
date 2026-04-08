package com.borjaglez.specrepository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.borjaglez.specrepository.core.ProjectedQueryPlanBuilder;
import com.borjaglez.specrepository.core.QueryPlan;

public class ProjectedSpecificationExecutableQuery<T, P> extends ProjectedQueryPlanBuilder<T, P> {
  private final SpecificationRepository<T, ?> repository;

  ProjectedSpecificationExecutableQuery(
      SpecificationExecutableQuery<T> delegate, SpecificationRepository<T, ?> repository) {
    super(delegate);
    this.repository = repository;
  }

  public List<P> findAll() {
    return repository.findAllProjected(build());
  }

  public Page<P> findAll(Pageable pageable) {
    return repository.findAllProjected(build(), pageable);
  }

  public Optional<P> findOne() {
    return repository.findOneProjected(build());
  }

  public QueryPlan<T> plan() {
    return build();
  }
}
