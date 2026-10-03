package com.borjaglez.specrepository.http.spring;

import static org.apiguardian.api.API.Status.INTERNAL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apiguardian.api.API;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.HttpFilterParser;

@API(status = INTERNAL, since = "1.0.0")
public class QueryPlanArgumentResolver implements HandlerMethodArgumentResolver {

  private static final String SORTING = "sorting";

  private final HttpFilterParser parser;

  public QueryPlanArgumentResolver(HttpFilterParser parser) {
    this.parser = parser;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return findAnnotation(parameter) != null
        && QueryPlan.class.isAssignableFrom(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    FilterableQuery annotation = findAnnotation(parameter);
    Map<String, List<String>> params = extractParams(webRequest);
    AllowedFieldsPolicy policy = buildPolicy(annotation);
    Set<String> caseInsensitiveFields =
        Arrays.stream(annotation.caseInsensitiveFields()).collect(Collectors.toSet());
    QueryPlan<?> plan =
        parser.toQueryPlan(annotation.value(), params, policy, caseInsensitiveFields);
    // Checked here so a disallowed client field fails before the handler runs, without the
    // wrapping of the repository proxy. The plan keeps the policy for later client input.
    validate(annotation, policy, plan);
    return plan;
  }

  /**
   * Validates the client input of {@code plan}. A field rejected because its usage has no declared
   * list is reported with an {@link UndeclaredFieldListException} that names the missing attribute.
   */
  private void validate(FilterableQuery annotation, AllowedFieldsPolicy policy, QueryPlan<?> plan) {
    try {
      policy.validate(plan);
    } catch (DisallowedFieldException ex) {
      boolean sorting = SORTING.equals(ex.usage());
      String[] declared = sorting ? annotation.sortableFields() : annotation.filterableFields();
      if (declared.length == 0) {
        throw new UndeclaredFieldListException(
            ex.field(), ex.usage(), sorting ? "sortableFields" : "filterableFields");
      }
      throw ex;
    }
  }

  /**
   * Finds {@link FilterableQuery} directly on the parameter or meta-present on a composed
   * annotation, merging {@code @AliasFor} overrides. Starts from {@link
   * MethodParameter#getParameterAnnotations()} so that annotations a handler method inherits from
   * an interface are honoured as well. A direct declaration wins over meta-present ones.
   */
  private FilterableQuery findAnnotation(MethodParameter parameter) {
    return MergedAnnotations.from(parameter, parameter.getParameterAnnotations())
        .get(FilterableQuery.class)
        .synthesize(MergedAnnotation::isPresent)
        .orElse(null);
  }

  private Map<String, List<String>> extractParams(NativeWebRequest webRequest) {
    return webRequest.getParameterMap().entrySet().stream()
        .collect(
            Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(Arrays.asList(e.getValue()))));
  }

  private AllowedFieldsPolicy buildPolicy(FilterableQuery annotation) {
    String[] filterable = annotation.filterableFields();
    String[] sortable = annotation.sortableFields();
    if (annotation.allowAllFields()) {
      if (filterable.length > 0 || sortable.length > 0) {
        throw new IllegalStateException(
            "@FilterableQuery allowAllFields = true cannot be combined with filterableFields"
                + " or sortableFields");
      }
      return AllowedFieldsPolicy.allowAll();
    }
    Set<String> filterableFields = Arrays.stream(filterable).collect(Collectors.toSet());
    Set<String> sortableFields = Arrays.stream(sortable).collect(Collectors.toSet());
    return AllowedFieldsPolicy.of(filterableFields, sortableFields);
  }
}
