package com.borjaglez.specrepository.jpa.support;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

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
    String[] segments = path.split("\\.");
    Path<?> currentPath = from;
    From<?, ?> currentFrom = from;
    ManagedType<?> currentType = fromType;
    StringBuilder associationPath = new StringBuilder();

    for (int index = 0; index < segments.length; index++) {
      String segment = segments[index];
      Attribute<?, ?> attribute = currentType.getAttribute(segment);
      boolean last = index == segments.length - 1;

      if (!last && isAssociation(attribute)) {
        if (!associationPath.isEmpty()) {
          associationPath.append('.');
        }
        associationPath.append(segment);
        currentFrom =
            registry.getOrCreateJoin(associationPath.toString(), currentFrom, segment, joinMode);
        currentPath = currentFrom;
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

    for (String segment : segments) {
      Attribute<?, ?> attribute = currentType.getAttribute(segment);
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

    for (String segment : segments) {
      Attribute<?, ?> attribute = currentType.getAttribute(segment);
      if (!associationPath.isEmpty()) {
        associationPath.append('.');
      }
      associationPath.append(segment);
      Fetch<?, ?> fetch =
          registry.getOrCreateFetch(associationPath.toString(), currentFrom, segment, joinMode);
      currentFrom = (From<?, ?>) fetch;
      currentType = managedType(attribute);
    }
  }

  ManagedType<?> resolveAssociationTarget(ManagedType<?> fromType, String associationPath) {
    String[] segments = associationPath.split("\\.");
    ManagedType<?> currentType = fromType;
    for (String segment : segments) {
      Attribute<?, ?> attribute = currentType.getAttribute(segment);
      currentType = managedType(attribute);
    }
    return currentType;
  }

  private boolean isAssociation(Attribute<?, ?> attribute) {
    return attribute.isAssociation() || attribute instanceof PluralAttribute<?, ?, ?>;
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
