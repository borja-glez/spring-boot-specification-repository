package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.HttpFilterParser;

class QueryPlanArgumentResolverTest {

  private final HttpFilterParser parser = new HttpFilterParser();
  private final QueryPlanArgumentResolver resolver = new QueryPlanArgumentResolver(parser);

  static class TestEntity {}

  @SuppressWarnings("unused")
  void annotatedMethod(
      @FilterableQuery(
              value = TestEntity.class,
              filterableFields = {"name"},
              sortableFields = {"name"})
          QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void defaultAnnotatedMethod(@FilterableQuery(TestEntity.class) QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void sortableOnlyMethod(
      @FilterableQuery(
              value = TestEntity.class,
              sortableFields = {"name"})
          QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void nonAnnotatedMethod(QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void wrongTypeMethod(@FilterableQuery(TestEntity.class) String notAQueryPlan) {}

  @Test
  void shouldSupportAnnotatedQueryPlanParameter() throws Exception {
    MethodParameter param = getParam("annotatedMethod", QueryPlan.class);
    assertThat(resolver.supportsParameter(param)).isTrue();
  }

  @Test
  void shouldNotSupportNonAnnotatedParameter() throws Exception {
    MethodParameter param = getParam("nonAnnotatedMethod", QueryPlan.class);
    assertThat(resolver.supportsParameter(param)).isFalse();
  }

  @Test
  void shouldNotSupportWrongType() throws Exception {
    MethodParameter param = getParam("wrongTypeMethod", String.class);
    assertThat(resolver.supportsParameter(param)).isFalse();
  }

  @Test
  void shouldResolveQueryPlanFromRequestParams() throws Exception {
    MethodParameter param = getParam("annotatedMethod", QueryPlan.class);
    NativeWebRequest webRequest = mock(NativeWebRequest.class);
    when(webRequest.getParameterMap())
        .thenReturn(
            Map.of("filter", new String[] {"name:eq:John"}, "sort", new String[] {"name,asc"}));

    @SuppressWarnings("unchecked")
    QueryPlan<TestEntity> plan =
        (QueryPlan<TestEntity>) resolver.resolveArgument(param, null, webRequest, null);

    assertThat(plan).isNotNull();
    assertThat(plan.entityType()).isEqualTo(TestEntity.class);
    assertThat(plan.rootCondition().conditions()).hasSize(1);
    var cond = (PredicateCondition) plan.rootCondition().conditions().get(0);
    assertThat(cond.field()).isEqualTo("name");
    assertThat(cond.operator()).isEqualTo(Operators.EQUALS);
  }

  @Test
  void shouldApplyFieldRestrictions() throws Exception {
    MethodParameter param = getParam("annotatedMethod", QueryPlan.class);
    NativeWebRequest webRequest = mock(NativeWebRequest.class);
    when(webRequest.getParameterMap()).thenReturn(Map.of("filter", new String[] {"name:eq:John"}));

    @SuppressWarnings("unchecked")
    QueryPlan<TestEntity> plan =
        (QueryPlan<TestEntity>) resolver.resolveArgument(param, null, webRequest, null);

    assertThat(plan.allowedFieldsPolicy()).isNotSameAs(AllowedFieldsPolicy.allowAll());
  }

  @Test
  void shouldUseAllowAllWhenNoFieldsSpecified() throws Exception {
    MethodParameter param = getParam("defaultAnnotatedMethod", QueryPlan.class);
    NativeWebRequest webRequest = mock(NativeWebRequest.class);
    when(webRequest.getParameterMap()).thenReturn(Map.of());

    @SuppressWarnings("unchecked")
    QueryPlan<TestEntity> plan =
        (QueryPlan<TestEntity>) resolver.resolveArgument(param, null, webRequest, null);

    assertThat(plan.allowedFieldsPolicy()).isSameAs(AllowedFieldsPolicy.allowAll());
  }

  @Test
  void shouldApplyPolicyWhenOnlySortableFieldsSpecified() throws Exception {
    MethodParameter param = getParam("sortableOnlyMethod", QueryPlan.class);
    NativeWebRequest webRequest = mock(NativeWebRequest.class);
    when(webRequest.getParameterMap()).thenReturn(Map.of());

    @SuppressWarnings("unchecked")
    QueryPlan<TestEntity> plan =
        (QueryPlan<TestEntity>) resolver.resolveArgument(param, null, webRequest, null);

    assertThat(plan.allowedFieldsPolicy()).isNotSameAs(AllowedFieldsPolicy.allowAll());
  }

  @Test
  void shouldResolveEmptyParams() throws Exception {
    MethodParameter param = getParam("defaultAnnotatedMethod", QueryPlan.class);
    NativeWebRequest webRequest = mock(NativeWebRequest.class);
    when(webRequest.getParameterMap()).thenReturn(Map.of());

    @SuppressWarnings("unchecked")
    QueryPlan<TestEntity> plan =
        (QueryPlan<TestEntity>) resolver.resolveArgument(param, null, webRequest, null);

    assertThat(plan.rootCondition().conditions()).isEmpty();
  }

  private MethodParameter getParam(String methodName, Class<?> paramType) throws Exception {
    Method method = getClass().getDeclaredMethod(methodName, paramType);
    MethodParameter param = new MethodParameter(method, 0);
    param.initParameterNameDiscovery(null);
    return param;
  }
}
