package com.borjaglez.specrepository.jpa.support;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

import com.borjaglez.specrepository.core.InvalidFilterException;
import com.borjaglez.specrepository.core.JoinMode;

public class PathResolver {

  public Path<?> resolve(
      Root<?> root, AssociationRegistry registry, String path, JoinMode joinMode) {
    return resolve(root, root.getModel(), registry, path, joinMode);
  }

  public Path<?> resolve(
      From<?, ?> from,
      ManagedType<?> fromType,
      AssociationRegistry registry,
      String path,
      JoinMode joinMode) {
    return resolve(from, fromType, registry, path, joinMode, true);
  }

  /**
   * Resolves a dotted path.
   *
   * @param joinBasicCollections whether a collection of basic values at the end of the path is
   *     joined, so conditions compare its elements. Pass {@code false} for operators that need the
   *     collection itself, such as {@code isempty}.
   */
  public Path<?> resolve(
      From<?, ?> from,
      ManagedType<?> fromType,
      AssociationRegistry registry,
      String path,
      JoinMode joinMode,
      boolean joinBasicCollections) {
    String[] segments = path.split("\\.");
    Path<?> currentPath = from;
    From<?, ?> currentFrom = from;
    ManagedType<?> currentType = fromType;
    StringBuilder associationPath = new StringBuilder();

    for (int index = 0; index < segments.length; index++) {
      String segment = segments[index];
      Attribute<?, ?> attribute = attribute(currentType, path, segments, index);
      boolean last = index == segments.length - 1;

      // Associations are joined to keep navigating. A collection of basic values (for example an
      // @ElementCollection of strings) is joined too when it is the last segment, so conditions
      // compare its elements instead of the whole collection.
      if (isAssociation(attribute)
          && (!last || (joinBasicCollections && isBasicCollection(attribute)))) {
        if (!associationPath.isEmpty()) {
          associationPath.append('.');
        }
        associationPath.append(segment);
        currentFrom =
            registry.getOrCreateJoin(associationPath.toString(), currentFrom, segment, joinMode);
        currentPath = currentFrom;
        if (last) {
          break;
        }
        currentType = managedType(attribute);
        continue;
      }

      currentPath = currentPath.get(segment);
      if (!last && isEmbeddable(attribute)) {
        currentType = managedType(attribute);
      }
    }

    return currentPath;
  }

  public void join(Root<?> root, AssociationRegistry registry, String path, JoinMode joinMode) {
    join(root, root.getModel(), registry, path, joinMode);
  }

  public void join(
      From<?, ?> from,
      ManagedType<?> fromType,
      AssociationRegistry registry,
      String path,
      JoinMode joinMode) {
    String[] segments = path.split("\\.");
    From<?, ?> currentFrom = from;
    ManagedType<?> currentType = fromType;
    StringBuilder associationPath = new StringBuilder();

    for (int index = 0; index < segments.length; index++) {
      String segment = segments[index];
      Attribute<?, ?> attribute = attribute(currentType, path, segments, index);
      if (!associationPath.isEmpty()) {
        associationPath.append('.');
      }
      associationPath.append(segment);
      currentFrom =
          registry.getOrCreateJoin(associationPath.toString(), currentFrom, segment, joinMode);
      currentType = managedType(attribute);
    }
  }

  public void fetch(Root<?> root, AssociationRegistry registry, String path, JoinMode joinMode) {
    String[] segments = path.split("\\.");
    From<?, ?> currentFrom = root;
    ManagedType<?> currentType = root.getModel();
    StringBuilder associationPath = new StringBuilder();

    for (int index = 0; index < segments.length; index++) {
      String segment = segments[index];
      Attribute<?, ?> attribute = attribute(currentType, path, segments, index);
      boolean last = index == segments.length - 1;
      // A collection of basic values (for example an @ElementCollection of strings) is fetched as
      // a whole: its elements have no attributes, so it can only end the path.
      if (!last && isBasicCollection(attribute)) {
        throw new IllegalArgumentException(
            "Cannot fetch '"
                + path
                + "': '"
                + segment
                + "' is a collection of basic values and must be the last segment");
      }
      if (!associationPath.isEmpty()) {
        associationPath.append('.');
      }
      associationPath.append(segment);
      Fetch<?, ?> fetch =
          registry.getOrCreateFetch(associationPath.toString(), currentFrom, segment, joinMode);
      if (!last) {
        currentFrom = (From<?, ?>) fetch;
        currentType = managedType(attribute);
      }
    }
  }

  /**
   * Resolves the type reached by an association path, or {@code null} when the path ends in a
   * collection of basic values (for example an {@code @ElementCollection} of strings), whose
   * elements have no attributes to navigate.
   */
  ManagedType<?> resolveAssociationTarget(ManagedType<?> fromType, String associationPath) {
    String[] segments = associationPath.split("\\.");
    ManagedType<?> currentType = fromType;
    for (int index = 0; index < segments.length; index++) {
      Attribute<?, ?> attribute = attribute(currentType, associationPath, segments, index);
      if (isBasicCollection(attribute)) {
        return null;
      }
      currentType = managedType(attribute);
    }
    return currentType;
  }

  /**
   * Looks up one segment of {@code path}. The JPA provider rejects an unknown name with a generic
   * {@link IllegalArgumentException}; it is reported as an {@link InvalidFilterException} on the
   * full path.
   */
  private Attribute<?, ?> attribute(
      ManagedType<?> type, String path, String[] segments, int index) {
    String segment = segments[index];
    try {
      return type.getAttribute(segment);
    } catch (IllegalArgumentException ex) {
      String reason =
          index == segments.length - 1 ? "unknown field" : "unknown field '" + segment + "'";
      throw new InvalidFilterException(path, reason, ex);
    }
  }

  private boolean isAssociation(Attribute<?, ?> attribute) {
    return attribute.isAssociation() || attribute instanceof PluralAttribute<?, ?, ?>;
  }

  private boolean isBasicCollection(Attribute<?, ?> attribute) {
    return attribute instanceof PluralAttribute<?, ?, ?> plural
        && plural.getElementType().getPersistenceType() == Type.PersistenceType.BASIC;
  }

  private boolean isEmbeddable(Attribute<?, ?> attribute) {
    return attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED;
  }

  private ManagedType<?> managedType(Attribute<?, ?> attribute) {
    if (attribute instanceof SingularAttribute<?, ?> singularAttribute) {
      return (ManagedType<?>) singularAttribute.getType();
    }
    if (attribute instanceof PluralAttribute<?, ?, ?> pluralAttribute) {
      return (ManagedType<?>) pluralAttribute.getElementType();
    }
    throw new IllegalStateException(
        "Unsupported managed type for attribute " + attribute.getName());
  }
}
