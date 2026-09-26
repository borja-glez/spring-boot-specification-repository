package com.borjaglez.specrepository.http.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.http.HttpFilterParser;

/**
 * Marks a {@code QueryPlan<T>} controller parameter to be resolved from the request by {@link
 * QueryPlanArgumentResolver}.
 *
 * <p>Can also be used as a meta-annotation to declare a composed annotation that shares the same
 * entity and field lists across endpoints, directly or through several levels of composition.
 * Composed annotations may override attributes with {@code @AliasFor(annotation =
 * FilterableQuery.class)}. When a parameter carries {@code @FilterableQuery} directly, that
 * declaration wins over any meta-present one.
 *
 * <p>{@link #caseInsensitiveFields()} lets the server make matching on some fields case-insensitive
 * without any change to the client syntax.
 */
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FilterableQuery {
  Class<?> value();

  String[] filterableFields() default {};

  String[] sortableFields() default {};

  /**
   * Fields whose filter conditions are matched case-insensitively, in both {@code filter} and
   * {@code orFilter}. Applies to the {@code eq}, {@code neq}, {@code contains}, {@code
   * notcontains}, {@code startswith} and {@code endswith} operators; any other operator on these
   * fields keeps its default matching. On PostgreSQL the default operator handlers are also
   * accent-insensitive ({@code unaccent}), which requires the {@code unaccent} extension.
   *
   * @see HttpFilterParser#toQueryPlan(Class, java.util.Map, AllowedFieldsPolicy, java.util.Set)
   */
  String[] caseInsensitiveFields() default {};
}
