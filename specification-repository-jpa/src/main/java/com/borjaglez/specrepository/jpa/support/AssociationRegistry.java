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
    Fetch<?, ?> existingFetch = fetches.get(path);
    if (existingFetch instanceof Join<?, ?> fetchAsJoin) {
      joins.put(path, fetchAsJoin);
      return fetchAsJoin;
    }
    Join<?, ?> join = from.join(attributeName, toJoinType(mode));
    joins.put(path, join);
    return join;
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
