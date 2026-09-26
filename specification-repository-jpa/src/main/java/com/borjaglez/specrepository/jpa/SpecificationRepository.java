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

  /**
   * The selected fields and aggregates of the plan, one {@link GroupedRow} per result row. A plan
   * with selections and no projection type is read with this method or {@link #findRow(QueryPlan)}:
   * the entity methods ({@code findAll}, {@code findSlice}, {@code findOne}) reject it.
   *
   * @throws IllegalStateException if the plan has no selections
   */
  List<GroupedRow> findRows(QueryPlan<T> plan);

  /**
   * The first row of {@link #findRows(QueryPlan)}, reading only that row. A single aggregate
   * without {@code groupBy} returns exactly one row, holding its value.
   *
   * @throws IllegalStateException if the plan has no selections
   */
  Optional<GroupedRow> findRow(QueryPlan<T> plan);

  /** Same as {@link #findRows(QueryPlan)}, named for {@code groupBy} reports. */
  List<GroupedRow> findAllGrouped(QueryPlan<T> plan);
}
