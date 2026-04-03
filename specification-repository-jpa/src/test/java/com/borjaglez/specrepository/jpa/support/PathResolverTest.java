package com.borjaglez.specrepository.jpa.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.specrepository.core.JoinMode;

class PathResolverTest {

  private PathResolver pathResolver;
  private Root<?> root;
  private AssociationRegistry registry;
  private EntityType<?> entityType;

  @BeforeEach
  void setUp() {
    pathResolver = new PathResolver();
    root = mock(Root.class);
    registry = mock(AssociationRegistry.class);
    entityType = mock(EntityType.class);
    doReturn(entityType).when(root).getModel();
  }

  @Test
  void shouldResolveSimplePath() {
    Path<?> namePath = mock(Path.class);
    SingularAttribute<?, ?> attribute = mock(SingularAttribute.class);
    doReturn(attribute).when(entityType).getAttribute("name");
    doReturn(false).when(attribute).isAssociation();
    doReturn(Attribute.PersistentAttributeType.BASIC).when(attribute).getPersistentAttributeType();
    doReturn(namePath).when(root).get("name");

    Path<?> result = pathResolver.resolve(root, registry, "name", JoinMode.LEFT);

    assertThat(result).isSameAs(namePath);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldResolveNestedAssociationPath() {
    SingularAttribute<?, ?> profileAttr = mock(SingularAttribute.class);
    doReturn(true).when(profileAttr).isAssociation();
    ManagedType<?> profileType = mock(ManagedType.class);
    doReturn(profileType).when(profileAttr).getType();

    SingularAttribute<?, ?> cityAttr = mock(SingularAttribute.class);
    doReturn(false).when(cityAttr).isAssociation();
    doReturn(Attribute.PersistentAttributeType.BASIC).when(cityAttr).getPersistentAttributeType();

    doReturn(profileAttr).when(entityType).getAttribute("profile");
    doReturn(cityAttr).when(profileType).getAttribute("city");

    Join<?, ?> profileJoin = mock(Join.class);
    doReturn(profileJoin)
        .when(registry)
        .getOrCreateJoin(eq("profile"), eq(root), eq("profile"), eq(JoinMode.LEFT));

    Path<?> cityPath = mock(Path.class);
    doReturn(cityPath).when(profileJoin).get("city");

    Path<?> result = pathResolver.resolve(root, registry, "profile.city", JoinMode.LEFT);

    assertThat(result).isSameAs(cityPath);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldResolveEmbeddablePath() {
    SingularAttribute<?, ?> addressAttr = mock(SingularAttribute.class);
    doReturn(false).when(addressAttr).isAssociation();
    doReturn(Attribute.PersistentAttributeType.EMBEDDED)
        .when(addressAttr)
        .getPersistentAttributeType();
    ManagedType<?> addressType = mock(ManagedType.class);
    doReturn(addressType).when(addressAttr).getType();

    SingularAttribute<?, ?> streetAttr = mock(SingularAttribute.class);
    doReturn(false).when(streetAttr).isAssociation();
    doReturn(Attribute.PersistentAttributeType.BASIC).when(streetAttr).getPersistentAttributeType();

    doReturn(addressAttr).when(entityType).getAttribute("address");
    doReturn(streetAttr).when(addressType).getAttribute("street");

    Path<?> addressPath = mock(Path.class);
    Path<?> streetPath = mock(Path.class);
    doReturn(addressPath).when(root).get("address");
    doReturn(streetPath).when(addressPath).get("street");

    Path<?> result = pathResolver.resolve(root, registry, "address.street", JoinMode.LEFT);

    assertThat(result).isSameAs(streetPath);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldResolvePluralAttributeAsAssociation() {
    PluralAttribute<?, ?, ?> ordersAttr = mock(PluralAttribute.class);
    doReturn(false).when(ordersAttr).isAssociation();
    ManagedType<?> orderType = mock(ManagedType.class);
    doReturn(orderType).when(ordersAttr).getElementType();

    SingularAttribute<?, ?> totalAttr = mock(SingularAttribute.class);
    doReturn(false).when(totalAttr).isAssociation();
    doReturn(Attribute.PersistentAttributeType.BASIC).when(totalAttr).getPersistentAttributeType();

    doReturn(ordersAttr).when(entityType).getAttribute("orders");
    doReturn(totalAttr).when(orderType).getAttribute("total");

    Join<?, ?> ordersJoin = mock(Join.class);
    doReturn(ordersJoin)
        .when(registry)
        .getOrCreateJoin(eq("orders"), eq(root), eq("orders"), eq(JoinMode.LEFT));

    Path<?> totalPath = mock(Path.class);
    doReturn(totalPath).when(ordersJoin).get("total");

    Path<?> result = pathResolver.resolve(root, registry, "orders.total", JoinMode.LEFT);

    assertThat(result).isSameAs(totalPath);
  }

  @SuppressWarnings("unchecked")
  @Test
  void fetchShouldCreateFetchForSingleSegment() {
    SingularAttribute<?, ?> profileAttr = mock(SingularAttribute.class);
    doReturn(true).when(profileAttr).isAssociation();
    ManagedType<?> profileType = mock(ManagedType.class);
    doReturn(profileType).when(profileAttr).getType();
    doReturn(profileAttr).when(entityType).getAttribute("profile");

    FetchFrom profileFetch = mock(FetchFrom.class);
    doReturn(profileFetch)
        .when(registry)
        .getOrCreateFetch(eq("profile"), eq(root), eq("profile"), eq(JoinMode.LEFT));

    pathResolver.fetch(root, registry, "profile", JoinMode.LEFT);

    verify(registry).getOrCreateFetch("profile", root, "profile", JoinMode.LEFT);
  }

  @SuppressWarnings("unchecked")
  @Test
  void fetchShouldChainFetchesForNestedPath() {
    SingularAttribute<?, ?> profileAttr = mock(SingularAttribute.class);
    doReturn(true).when(profileAttr).isAssociation();
    ManagedType<?> profileType = mock(ManagedType.class);
    doReturn(profileType).when(profileAttr).getType();
    doReturn(profileAttr).when(entityType).getAttribute("profile");

    SingularAttribute<?, ?> addressAttr = mock(SingularAttribute.class);
    doReturn(true).when(addressAttr).isAssociation();
    ManagedType<?> addressType = mock(ManagedType.class);
    doReturn(addressType).when(addressAttr).getType();
    doReturn(addressAttr).when(profileType).getAttribute("address");

    // Use a mock that implements both Fetch and From
    FetchFrom profileFetchFrom = mock(FetchFrom.class);
    doReturn(profileFetchFrom)
        .when(registry)
        .getOrCreateFetch(eq("profile"), eq(root), eq("profile"), eq(JoinMode.INNER));

    FetchFrom addressFetch = mock(FetchFrom.class);
    doReturn(addressFetch)
        .when(registry)
        .getOrCreateFetch(
            eq("profile.address"), eq(profileFetchFrom), eq("address"), eq(JoinMode.INNER));

    pathResolver.fetch(root, registry, "profile.address", JoinMode.INNER);

    verify(registry).getOrCreateFetch("profile", root, "profile", JoinMode.INNER);
    verify(registry)
        .getOrCreateFetch("profile.address", profileFetchFrom, "address", JoinMode.INNER);
  }

  @Test
  void shouldResolvePathWithNonEmbeddableNonAssociationMiddleSegment() {
    // Tests a.b where a is neither association nor embeddable (BASIC) and is not the last segment
    // This covers the isEmbeddable returning false branch at line 42
    SingularAttribute<?, ?> aAttr = mock(SingularAttribute.class);
    doReturn(false).when(aAttr).isAssociation();
    doReturn(Attribute.PersistentAttributeType.BASIC).when(aAttr).getPersistentAttributeType();
    doReturn(aAttr).when(entityType).getAttribute("a");

    Path<?> aPath = mock(Path.class);
    Path<?> bPath = mock(Path.class);
    doReturn(aPath).when(root).get("a");
    doReturn(bPath).when(aPath).get("b");

    // For the second segment, it is the last, so isEmbeddable check is skipped
    SingularAttribute<?, ?> bAttr = mock(SingularAttribute.class);
    doReturn(false).when(bAttr).isAssociation();
    doReturn(Attribute.PersistentAttributeType.BASIC).when(bAttr).getPersistentAttributeType();
    // Need to make the entityType return bAttr for "b" too - but wait,
    // if "a" is BASIC and not embeddable, currentType won't change, so getAttribute("b")
    // is still called on the original entityType
    doReturn(bAttr).when(entityType).getAttribute("b");

    Path<?> result = pathResolver.resolve(root, registry, "a.b", JoinMode.LEFT);

    assertThat(result).isSameAs(bPath);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldResolveMultiLevelAssociationPath() {
    // Tests profile.address.city where profile and address are both associations
    SingularAttribute<?, ?> profileAttr = mock(SingularAttribute.class);
    doReturn(true).when(profileAttr).isAssociation();
    ManagedType<?> profileType = mock(ManagedType.class);
    doReturn(profileType).when(profileAttr).getType();
    doReturn(profileAttr).when(entityType).getAttribute("profile");

    SingularAttribute<?, ?> addressAttr = mock(SingularAttribute.class);
    doReturn(true).when(addressAttr).isAssociation();
    ManagedType<?> addressType = mock(ManagedType.class);
    doReturn(addressType).when(addressAttr).getType();
    doReturn(addressAttr).when(profileType).getAttribute("address");

    SingularAttribute<?, ?> cityAttr = mock(SingularAttribute.class);
    doReturn(false).when(cityAttr).isAssociation();
    doReturn(Attribute.PersistentAttributeType.BASIC).when(cityAttr).getPersistentAttributeType();
    doReturn(cityAttr).when(addressType).getAttribute("city");

    Join<?, ?> profileJoin = mock(Join.class);
    doReturn(profileJoin)
        .when(registry)
        .getOrCreateJoin(eq("profile"), eq(root), eq("profile"), eq(JoinMode.LEFT));

    Join<?, ?> addressJoin = mock(Join.class);
    doReturn(addressJoin)
        .when(registry)
        .getOrCreateJoin(eq("profile.address"), eq(profileJoin), eq("address"), eq(JoinMode.LEFT));

    Path<?> cityPath = mock(Path.class);
    doReturn(cityPath).when(addressJoin).get("city");

    Path<?> result = pathResolver.resolve(root, registry, "profile.address.city", JoinMode.LEFT);

    assertThat(result).isSameAs(cityPath);
    verify(registry).getOrCreateJoin("profile", root, "profile", JoinMode.LEFT);
    verify(registry).getOrCreateJoin("profile.address", profileJoin, "address", JoinMode.LEFT);
  }

  @Test
  void managedTypeShouldThrowForUnsupportedAttribute() {
    Attribute<?, ?> attribute = mock(Attribute.class);
    doReturn(true).when(attribute).isAssociation();
    doReturn("weird").when(attribute).getName();
    doReturn(attribute).when(entityType).getAttribute("weird");

    Join<?, ?> weirdJoin = mock(Join.class);
    doReturn(weirdJoin)
        .when(registry)
        .getOrCreateJoin(eq("weird"), eq(root), eq("weird"), eq(JoinMode.LEFT));

    assertThatThrownBy(() -> pathResolver.resolve(root, registry, "weird.field", JoinMode.LEFT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("weird");
  }

  @SuppressWarnings("unchecked")
  @Test
  void joinShouldCreateJoinForSingleSegment() {
    SingularAttribute<?, ?> profileAttr = mock(SingularAttribute.class);
    doReturn(true).when(profileAttr).isAssociation();
    ManagedType<?> profileType = mock(ManagedType.class);
    doReturn(profileType).when(profileAttr).getType();
    doReturn(profileAttr).when(entityType).getAttribute("profile");

    Join<?, ?> profileJoin = mock(Join.class);
    doReturn(profileJoin)
        .when(registry)
        .getOrCreateJoin(eq("profile"), eq(root), eq("profile"), eq(JoinMode.LEFT));

    pathResolver.join(root, registry, "profile", JoinMode.LEFT);

    verify(registry).getOrCreateJoin("profile", root, "profile", JoinMode.LEFT);
  }

  @SuppressWarnings("unchecked")
  @Test
  void joinShouldCreateJoinsForAllSegments() {
    SingularAttribute<?, ?> profileAttr = mock(SingularAttribute.class);
    doReturn(true).when(profileAttr).isAssociation();
    ManagedType<?> profileType = mock(ManagedType.class);
    doReturn(profileType).when(profileAttr).getType();
    doReturn(profileAttr).when(entityType).getAttribute("profile");

    SingularAttribute<?, ?> addressAttr = mock(SingularAttribute.class);
    doReturn(true).when(addressAttr).isAssociation();
    ManagedType<?> addressType = mock(ManagedType.class);
    doReturn(addressType).when(addressAttr).getType();
    doReturn(addressAttr).when(profileType).getAttribute("address");

    Join<?, ?> profileJoin = mock(Join.class);
    doReturn(profileJoin)
        .when(registry)
        .getOrCreateJoin(eq("profile"), eq(root), eq("profile"), eq(JoinMode.INNER));

    Join<?, ?> addressJoin = mock(Join.class);
    doReturn(addressJoin)
        .when(registry)
        .getOrCreateJoin(eq("profile.address"), eq(profileJoin), eq("address"), eq(JoinMode.INNER));

    pathResolver.join(root, registry, "profile.address", JoinMode.INNER);

    verify(registry).getOrCreateJoin("profile", root, "profile", JoinMode.INNER);
    verify(registry).getOrCreateJoin("profile.address", profileJoin, "address", JoinMode.INNER);
  }

  /** Helper interface that combines Fetch and From for mocking nested fetches. */
  interface FetchFrom extends Fetch<Object, Object>, From<Object, Object> {}
}
