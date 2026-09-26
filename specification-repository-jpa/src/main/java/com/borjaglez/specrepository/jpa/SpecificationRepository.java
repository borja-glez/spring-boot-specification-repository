package com.borjaglez.specrepository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.QueryPlan;

/**
 * Repository with the specification-repository query DSL.
 *
 * <p>Every method is abstract on purpose: Spring Data invokes {@code default} methods of repository
 * interfaces itself instead of delegating them to the repository base class, so a default body here
 * would shadow {@link SpecificationRepositoryImpl} for every repository proxy.
 */
@NoRepositoryBean
public interface SpecificationRepository<T, ID>
    extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {
  SpecificationExecutableQuery<T> query();

  List<T> findAll(QueryPlan<T> plan);

  <P> List<P> findAllProjected(QueryPlan<T> plan);

  Page<T> findAll(QueryPlan<T> plan, Pageable pageable);

  <P> Page<P> findAllProjected(QueryPlan<T> plan, Pageable pageable);

  Slice<T> findSlice(QueryPlan<T> plan, Pageable pageable);

  <P> Slice<P> findSliceProjected(QueryPlan<T> plan, Pageable pageable);

  Optional<T> findOne(QueryPlan<T> plan);

  <P> Optional<P> findOneProjected(QueryPlan<T> plan);

  long count(QueryPlan<T> plan);

  List<GroupedRow> findAllGrouped(QueryPlan<T> plan);
}
