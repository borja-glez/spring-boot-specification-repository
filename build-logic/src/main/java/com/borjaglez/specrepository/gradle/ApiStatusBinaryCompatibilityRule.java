package com.borjaglez.specrepository.gradle;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import japicmp.model.JApiBehavior;
import japicmp.model.JApiClass;
import japicmp.model.JApiCompatibility;
import japicmp.model.JApiCompatibilityChange;
import japicmp.model.JApiConstructor;
import japicmp.model.JApiField;
import japicmp.model.JApiImplementedInterface;
import japicmp.model.JApiMethod;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.MemberValue;
import me.champeau.gradle.japicmp.report.AbstractContextAwareViolationRule;
import me.champeau.gradle.japicmp.report.Violation;

/**
 * japicmp rich-report rule that fails on binary-incompatible changes, except for elements whose
 * {@code @org.apiguardian.api.API} status is {@code INTERNAL} or {@code EXPERIMENTAL}.
 *
 * <p>japicmp's own annotation exclusion ({@code annotationExcludes}) only matches the annotation
 * type name, not its attribute values, so it cannot tell {@code @API(status = STABLE)} from
 * {@code @API(status = INTERNAL)}. This rule reads the {@code status} value from the bytecode
 * instead.
 *
 * <p>An element's effective status is its own {@code @API} status or, when it has none, the status
 * of its (enclosing) class. A binary-incompatible change is accepted when the baseline marks the
 * element {@code INTERNAL}/{@code EXPERIMENTAL}, or when the baseline predates {@code @API} (no
 * status at all) and the current version marks it {@code INTERNAL}/{@code EXPERIMENTAL}. Demoting a
 * {@code STABLE} or {@code MAINTAINED} element to {@code INTERNAL} is still reported.
 *
 * <p>The rule is instantiated by japicmp in a Gradle worker process, so it must stay a plain Java
 * class with a public no-argument constructor.
 */
public class ApiStatusBinaryCompatibilityRule extends AbstractContextAwareViolationRule {

  private static final String API_ANNOTATION = "org.apiguardian.api.API";

  private static final Set<String> EXCLUDED_STATUSES = Set.of("INTERNAL", "EXPERIMENTAL");

  private static final String CLASS_DECISIONS_KEY =
      ApiStatusBinaryCompatibilityRule.class.getName() + ".classDecisions";

  @Override
  public Violation maybeViolation(JApiCompatibility member) {
    String excludedStatus = excludedStatus(member);
    if (!isBinaryIncompatible(member)) {
      return null;
    }
    if (excludedStatus != null) {
      return Violation.accept(
          member, "Is not binary compatible, but is @API(status = " + excludedStatus + ")");
    }
    return Violation.notBinaryCompatible(member);
  }

  private static boolean isBinaryIncompatible(JApiCompatibility member) {
    if (member instanceof JApiClass jApiClass) {
      // JApiClass.isBinaryCompatible() also aggregates its members, which are checked one by one.
      return hasBinaryIncompatibleChange(jApiClass)
          || !jApiClass.getSuperclass().isBinaryCompatible();
    }
    if (member instanceof JApiImplementedInterface) {
      return hasBinaryIncompatibleChange(member);
    }
    return !member.isBinaryCompatible();
  }

  private static boolean hasBinaryIncompatibleChange(JApiCompatibility member) {
    for (JApiCompatibilityChange change : member.getCompatibilityChanges()) {
      if (!change.isBinaryCompatible()) {
        return true;
      }
    }
    return false;
  }

  private String excludedStatus(JApiCompatibility member) {
    if (member instanceof JApiClass jApiClass) {
      String status =
          decide(
              classStatus(jApiClass.getOldClass().orElse(null)),
              classStatus(jApiClass.getNewClass().orElse(null)));
      classDecisions().put(jApiClass.getFullyQualifiedName(), Optional.ofNullable(status));
      return status;
    }
    if (member instanceof JApiMethod method) {
      return decide(
          behaviorStatus(method.getOldMethod(), method, true),
          behaviorStatus(method.getNewMethod(), method, false));
    }
    if (member instanceof JApiConstructor constructor) {
      return decide(
          behaviorStatus(constructor.getOldConstructor(), constructor, true),
          behaviorStatus(constructor.getNewConstructor(), constructor, false));
    }
    if (member instanceof JApiField field) {
      return decide(
          fieldStatus(field.getOldFieldOptional(), field.getjApiClass().getOldClass()),
          fieldStatus(field.getNewFieldOptional(), field.getjApiClass().getNewClass()));
    }
    // Implemented interfaces and other class-level elements follow the enclosing class.
    Optional<String> classDecision = classDecisions().get(getContext().getClassName());
    return classDecision == null ? null : classDecision.orElse(null);
  }

  private Map<String, Optional<String>> classDecisions() {
    Map<String, Optional<String>> decisions = getContext().getUserData(CLASS_DECISIONS_KEY);
    if (decisions == null) {
      decisions = new HashMap<>();
      getContext().putUserData(CLASS_DECISIONS_KEY, decisions);
    }
    return decisions;
  }

  private static String decide(String baselineStatus, String currentStatus) {
    if (baselineStatus != null) {
      return EXCLUDED_STATUSES.contains(baselineStatus) ? baselineStatus : null;
    }
    return currentStatus != null && EXCLUDED_STATUSES.contains(currentStatus)
        ? currentStatus
        : null;
  }

  private static String behaviorStatus(
      Optional<? extends CtBehavior> behavior, JApiBehavior member, boolean baseline) {
    if (behavior.isPresent()) {
      String own =
          status(
              (AnnotationsAttribute)
                  behavior.get().getMethodInfo2().getAttribute(AnnotationsAttribute.visibleTag));
      return own != null ? own : classStatus(behavior.get().getDeclaringClass());
    }
    JApiClass owner = member.getjApiClass();
    return classStatus((baseline ? owner.getOldClass() : owner.getNewClass()).orElse(null));
  }

  private static String fieldStatus(Optional<CtField> field, Optional<CtClass> owner) {
    if (field.isPresent()) {
      String own =
          status(
              (AnnotationsAttribute)
                  field.get().getFieldInfo2().getAttribute(AnnotationsAttribute.visibleTag));
      return own != null ? own : classStatus(field.get().getDeclaringClass());
    }
    return classStatus(owner.orElse(null));
  }

  private static String classStatus(CtClass ctClass) {
    CtClass current = ctClass;
    while (current != null) {
      String own =
          status(
              (AnnotationsAttribute)
                  current.getClassFile2().getAttribute(AnnotationsAttribute.visibleTag));
      if (own != null) {
        return own;
      }
      try {
        current = current.getDeclaringClass();
      } catch (NotFoundException e) {
        return null;
      }
    }
    return null;
  }

  private static String status(AnnotationsAttribute attribute) {
    if (attribute == null) {
      return null;
    }
    Annotation annotation = attribute.getAnnotation(API_ANNOTATION);
    if (annotation == null) {
      return null;
    }
    MemberValue value = annotation.getMemberValue("status");
    return value instanceof EnumMemberValue enumValue ? enumValue.getValue() : null;
  }
}
