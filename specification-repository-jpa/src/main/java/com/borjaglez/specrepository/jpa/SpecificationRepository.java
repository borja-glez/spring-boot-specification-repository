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

  <P> List<P> findAllProjected(QueryPlan<T> plan);

  Page<T> findAll(QueryPlan<T> plan, Pageable pageable);

  <P> Page<P> findAllProjected(QueryPlan<T> plan, Pageable pageable);

  Optional<T> findOne(QueryPlan<T> plan);

  <P> Optional<P> findOneProjected(QueryPlan<T> plan);

  long count(QueryPlan<T> plan);
}
