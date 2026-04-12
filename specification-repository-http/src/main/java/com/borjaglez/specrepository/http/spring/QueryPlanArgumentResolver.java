package com.borjaglez.specrepository.http.spring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.HttpFilterParser;

public class QueryPlanArgumentResolver implements HandlerMethodArgumentResolver {

  private final HttpFilterParser parser;

  public QueryPlanArgumentResolver(HttpFilterParser parser) {
    this.parser = parser;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(FilterableQuery.class)
        && QueryPlan.class.isAssignableFrom(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    FilterableQuery annotation = parameter.getParameterAnnotation(FilterableQuery.class);
    Map<String, List<String>> params = extractParams(webRequest);
    AllowedFieldsPolicy policy = buildPolicy(annotation);
    return parser.toQueryPlan(annotation.value(), params, policy);
  }

  private Map<String, List<String>> extractParams(NativeWebRequest webRequest) {
    return webRequest.getParameterMap().entrySet().stream()
        .collect(
            Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(Arrays.asList(e.getValue()))));
  }

  private AllowedFieldsPolicy buildPolicy(FilterableQuery annotation) {
    String[] filterable = annotation.filterableFields();
    String[] sortable = annotation.sortableFields();
    if (filterable.length == 0 && sortable.length == 0) {
      return AllowedFieldsPolicy.allowAll();
    }
    return AllowedFieldsPolicy.of(Set.of(filterable), Set.of(sortable));
  }
}
