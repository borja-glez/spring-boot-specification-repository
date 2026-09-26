package com.borjaglez.specrepository.jpa.support;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

import com.borjaglez.specrepository.core.JoinMode;

public class AssociationRegistry {
  private final Map<String, Join<?, ?>> joins = new LinkedHashMap<>();
  private final Map<String, Fetch<?, ?>> fetches = new LinkedHashMap<>();

  public Join<?, ?> getOrCreateJoin(
      String path, From<?, ?> from, String attributeName, JoinMode mode) {
    Join<?, ?> existing = joins.get(path);
    if (existing != null) {
      return existing;
    }
    JoinType joinType = toJoinType(mode);
    Fetch<?, ?> existingFetch = fetches.get(path);
    // Reuse the fetch only when it joins the same way: an inner join must keep filtering out the
    // roots without the association even if the plan also fetches it with a left join.
    if (existingFetch instanceof Join<?, ?> fetchAsJoin && fetchAsJoin.getJoinType() == joinType) {
      joins.put(path, fetchAsJoin);
      return fetchAsJoin;
    }
    Join<?, ?> join = findJoin(from, attributeName, joinType);
    if (join == null) {
      join = from.join(attributeName, joinType);
    }
    joins.put(path, join);
    return join;
  }

  /**
   * Finds a join that another registry already created on the same {@code From}. Filters,
   * projections, grouping and sorting are resolved with different registries over the same root;
   * without this they would each join the association again, which duplicates rows and makes
   * databases such as PostgreSQL reject grouped queries.
   */
  private static Join<?, ?> findJoin(From<?, ?> from, String attributeName, JoinType joinType) {
    for (Join<?, ?> candidate : from.getJoins()) {
      if (candidate.getJoinType() == joinType
          && attributeName.equals(candidate.getAttribute().getName())) {
        return candidate;
      }
    }
    return null;
  }

  public Fetch<?, ?> getOrCreateFetch(
      String path, From<?, ?> from, String attributeName, JoinMode mode) {
    Fetch<?, ?> existing = fetches.get(path);
    if (existing != null) {
      return existing;
    }
    Join<?, ?> existingJoin = joins.get(path);
    if (existingJoin instanceof Fetch<?, ?> joinAsFetch) {
      fetches.put(path, joinAsFetch);
      return joinAsFetch;
    }
    Fetch<?, ?> fetch = from.fetch(attributeName, toJoinType(mode));
    fetches.put(path, fetch);
    return fetch;
  }

  static JoinType toJoinType(JoinMode mode) {
    return switch (mode) {
      case LEFT -> JoinType.LEFT;
      case INNER -> JoinType.INNER;
      case RIGHT -> JoinType.RIGHT;
    };
  }
}
