package com.borjaglez.specrepository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import com.borjaglez.specrepository.core.QueryPlan;

@NoRepositoryBean
public interface SpecificationRepository<T, ID>
    extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {
  SpecificationExecutableQuery<T> query();

  List<T> findAll(QueryPlan<T> plan);

  default <P> List<P> findAllProjected(QueryPlan<T> plan) {
    throw new UnsupportedOperationException(
        "Projected queries are not supported by this repository");
  }

  Page<T> findAll(QueryPlan<T> plan, Pageable pageable);

  default <P> Page<P> findAllProjected(QueryPlan<T> plan, Pageable pageable) {
    throw new UnsupportedOperationException(
        "Projected queries are not supported by this repository");
  }

  Optional<T> findOne(QueryPlan<T> plan);

  default <P> Optional<P> findOneProjected(QueryPlan<T> plan) {
    throw new UnsupportedOperationException(
        "Projected queries are not supported by this repository");
  }

  long count(QueryPlan<T> plan);
}
