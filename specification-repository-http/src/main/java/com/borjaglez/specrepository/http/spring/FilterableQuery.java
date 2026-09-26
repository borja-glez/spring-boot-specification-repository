package com.borjaglez.specrepository.http.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code QueryPlan<T>} controller parameter to be resolved from the request by {@link
 * QueryPlanArgumentResolver}.
 *
 * <p>Can also be used as a meta-annotation to declare a composed annotation that shares the same
 * entity and field lists across endpoints, directly or through several levels of composition.
 * Composed annotations may override attributes with {@code @AliasFor(annotation =
 * FilterableQuery.class)}. When a parameter carries {@code @FilterableQuery} directly, that
 * declaration wins over any meta-present one.
 */
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FilterableQuery {
  Class<?> value();

  String[] filterableFields() default {};

  String[] sortableFields() default {};
}
