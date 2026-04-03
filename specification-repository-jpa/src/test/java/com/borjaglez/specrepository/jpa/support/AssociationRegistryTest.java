package com.borjaglez.specrepository.jpa.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

import org.junit.jupiter.api.Test;

import com.borjaglez.specrepository.core.JoinMode;

class AssociationRegistryTest {

  @SuppressWarnings("unchecked")
  private final From<?, ?> from = mock(From.class);

  @SuppressWarnings("unchecked")
  private final Join<?, ?> join = mock(Join.class);

  @SuppressWarnings("unchecked")
  private final Fetch<?, ?> fetch = mock(Fetch.class);

  @Test
  void getOrCreateJoinShouldCreateJoinOnFirstCall() {
    doReturn(join).when(from).join("profile", JoinType.LEFT);
    AssociationRegistry registry = new AssociationRegistry();

    Join<?, ?> result = registry.getOrCreateJoin("profile", from, "profile", JoinMode.LEFT);

    assertThat(result).isSameAs(join);
    verify(from).join("profile", JoinType.LEFT);
  }

  @Test
  void getOrCreateJoinShouldReturnCachedJoinOnSecondCall() {
    doReturn(join).when(from).join("profile", JoinType.LEFT);
    AssociationRegistry registry = new AssociationRegistry();

    Join<?, ?> first = registry.getOrCreateJoin("profile", from, "profile", JoinMode.LEFT);
    Join<?, ?> second = registry.getOrCreateJoin("profile", from, "profile", JoinMode.LEFT);

    assertThat(first).isSameAs(second);
    verify(from, times(1)).join("profile", JoinType.LEFT);
  }

  @Test
  void getOrCreateJoinShouldUseInnerJoinType() {
    doReturn(join).when(from).join("profile", JoinType.INNER);
    AssociationRegistry registry = new AssociationRegistry();

    registry.getOrCreateJoin("profile", from, "profile", JoinMode.INNER);

    verify(from).join("profile", JoinType.INNER);
  }

  @Test
  void getOrCreateJoinShouldUseRightJoinType() {
    doReturn(join).when(from).join("profile", JoinType.RIGHT);
    AssociationRegistry registry = new AssociationRegistry();

    registry.getOrCreateJoin("profile", from, "profile", JoinMode.RIGHT);

    verify(from).join("profile", JoinType.RIGHT);
  }

  @Test
  void getOrCreateFetchShouldCreateFetchOnFirstCall() {
    doReturn(fetch).when(from).fetch("profile", JoinType.LEFT);
    AssociationRegistry registry = new AssociationRegistry();

    Fetch<?, ?> result = registry.getOrCreateFetch("profile", from, "profile", JoinMode.LEFT);

    assertThat(result).isSameAs(fetch);
    verify(from).fetch("profile", JoinType.LEFT);
  }

  @Test
  void getOrCreateFetchShouldReturnCachedFetchOnSecondCall() {
    doReturn(fetch).when(from).fetch("profile", JoinType.LEFT);
    AssociationRegistry registry = new AssociationRegistry();

    Fetch<?, ?> first = registry.getOrCreateFetch("profile", from, "profile", JoinMode.LEFT);
    Fetch<?, ?> second = registry.getOrCreateFetch("profile", from, "profile", JoinMode.LEFT);

    assertThat(first).isSameAs(second);
    verify(from, times(1)).fetch("profile", JoinType.LEFT);
  }

  @Test
  void getOrCreateFetchShouldUseInnerJoinType() {
    doReturn(fetch).when(from).fetch("profile", JoinType.INNER);
    AssociationRegistry registry = new AssociationRegistry();

    registry.getOrCreateFetch("profile", from, "profile", JoinMode.INNER);

    verify(from).fetch("profile", JoinType.INNER);
  }

  @Test
  void getOrCreateFetchShouldUseRightJoinType() {
    doReturn(fetch).when(from).fetch("profile", JoinType.RIGHT);
    AssociationRegistry registry = new AssociationRegistry();

    registry.getOrCreateFetch("profile", from, "profile", JoinMode.RIGHT);

    verify(from).fetch("profile", JoinType.RIGHT);
  }

  @Test
  void getOrCreateJoinShouldReuseFetchWhenFetchImplementsJoin() {
    @SuppressWarnings("unchecked")
    FetchJoin fetchJoin = mock(FetchJoin.class);
    doReturn(fetchJoin).when(from).fetch("profile", JoinType.LEFT);
    AssociationRegistry registry = new AssociationRegistry();

    // First create a fetch for the path
    registry.getOrCreateFetch("profile", from, "profile", JoinMode.LEFT);

    // Now request a join for the same path — should reuse the fetch since it implements Join
    Join<?, ?> result = registry.getOrCreateJoin("profile", from, "profile", JoinMode.LEFT);

    assertThat(result).isSameAs(fetchJoin);
    verify(from, times(0)).join("profile", JoinType.LEFT);
  }

  @Test
  void getOrCreateJoinShouldNotReuseFetchWhenFetchDoesNotImplementJoin() {
    doReturn(fetch).when(from).fetch("profile", JoinType.LEFT);
    doReturn(join).when(from).join("profile", JoinType.LEFT);
    AssociationRegistry registry = new AssociationRegistry();

    // First create a fetch for the path (plain Fetch, not a Join)
    registry.getOrCreateFetch("profile", from, "profile", JoinMode.LEFT);

    // Now request a join — should NOT reuse the fetch, should create a new join
    Join<?, ?> result = registry.getOrCreateJoin("profile", from, "profile", JoinMode.LEFT);

    assertThat(result).isSameAs(join);
    verify(from).join("profile", JoinType.LEFT);
  }

  @Test
  void getOrCreateFetchShouldReuseJoinWhenJoinImplementsFetch() {
    @SuppressWarnings("unchecked")
    FetchJoin fetchJoin = mock(FetchJoin.class);
    doReturn(fetchJoin).when(from).join("profile", JoinType.LEFT);
    AssociationRegistry registry = new AssociationRegistry();

    // First create a join for the path
    registry.getOrCreateJoin("profile", from, "profile", JoinMode.LEFT);

    // Now request a fetch for the same path — should reuse the join since it implements Fetch
    Fetch<?, ?> result = registry.getOrCreateFetch("profile", from, "profile", JoinMode.LEFT);

    assertThat(result).isSameAs(fetchJoin);
    verify(from, times(0)).fetch("profile", JoinType.LEFT);
  }

  @Test
  void getOrCreateFetchShouldNotReuseJoinWhenJoinDoesNotImplementFetch() {
    doReturn(join).when(from).join("profile", JoinType.LEFT);
    doReturn(fetch).when(from).fetch("profile", JoinType.LEFT);
    AssociationRegistry registry = new AssociationRegistry();

    // First create a join for the path (plain Join, not a Fetch)
    registry.getOrCreateJoin("profile", from, "profile", JoinMode.LEFT);

    // Now request a fetch — should NOT reuse the join, should create a new fetch
    Fetch<?, ?> result = registry.getOrCreateFetch("profile", from, "profile", JoinMode.LEFT);

    assertThat(result).isSameAs(fetch);
    verify(from).fetch("profile", JoinType.LEFT);
  }

  /** Helper interface that combines Fetch and Join for mocking cross-references. */
  interface FetchJoin extends Fetch<Object, Object>, Join<Object, Object> {}
}
