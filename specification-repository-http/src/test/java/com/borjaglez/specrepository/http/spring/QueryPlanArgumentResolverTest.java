package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AliasFor;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.HttpFilterParser;

class QueryPlanArgumentResolverTest {

  private final HttpFilterParser parser = new HttpFilterParser();
  private final QueryPlanArgumentResolver resolver = new QueryPlanArgumentResolver(parser);

  static class TestEntity {}

  static class OtherEntity {}

  @Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @FilterableQuery(
      value = TestEntity.class,
      filterableFields = {"name", "status"},
      sortableFields = {"name"})
  @interface TestEntitySearch {}

  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  @TestEntitySearch
  @interface NestedTestEntitySearch {}

  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  @FilterableQuery(
      value = TestEntity.class,
      filterableFields = {"name"},
      sortableFields = {"name"})
  @interface EntitySearch {
    @AliasFor(annotation = FilterableQuery.class, attribute = "value")
    Class<?> value();
  }

  interface SearchApi {
    void search(@TestEntitySearch QueryPlan<TestEntity> query);
  }

  static class SearchController implements SearchApi {
    @Override
    public void search(QueryPlan<TestEntity> query) {}
  }

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

  @SuppressWarnings("unused")
  void composedMethod(@TestEntitySearch QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void nestedComposedMethod(@NestedTestEntitySearch QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void aliasOverrideMethod(@EntitySearch(OtherEntity.class) QueryPlan<OtherEntity> query) {}

  @SuppressWarnings("unused")
  void directAndComposedMethod(
      @FilterableQuery(
              value = OtherEntity.class,
              filterableFields = {"code"},
              sortableFields = {"code"})
          @TestEntitySearch
          QueryPlan<OtherEntity> query) {}

  @SuppressWarnings("unused")
  void composedWrongTypeMethod(@TestEntitySearch String notAQueryPlan) {}

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
  void shouldResolveUpperCaseOperatorIntoLowerCaseOperator() throws Exception {
    MethodParameter param = getParam("annotatedMethod", QueryPlan.class);
    NativeWebRequest webRequest = mock(NativeWebRequest.class);
    when(webRequest.getParameterMap()).thenReturn(Map.of("filter", new String[] {"name:EQ:x"}));

    @SuppressWarnings("unchecked")
    QueryPlan<TestEntity> plan =
        (QueryPlan<TestEntity>) resolver.resolveArgument(param, null, webRequest, null);

    var cond = (PredicateCondition) plan.rootCondition().conditions().get(0);
    assertThat(cond.operator()).isEqualTo(Operators.EQUALS);
    assertThat(cond.value()).isEqualTo("x");
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

  @Test
  void shouldSupportQueryPlanParameterAnnotatedWithComposedAnnotation() throws Exception {
    MethodParameter param = getParam("composedMethod", QueryPlan.class);
    assertThat(resolver.supportsParameter(param)).isTrue();
  }

  @Test
  void shouldNotSupportNonQueryPlanParameterAnnotatedWithComposedAnnotation() throws Exception {
    MethodParameter param = getParam("composedWrongTypeMethod", String.class);
    assertThat(resolver.supportsParameter(param)).isFalse();
  }

  @Test
  void shouldResolveWithComposedAnnotationFieldLists() throws Exception {
    QueryPlan<?> plan = resolve(getParam("composedMethod", QueryPlan.class));

    assertThat(plan.entityType()).isEqualTo(TestEntity.class);
    assertTestEntitySearchPolicy(plan.allowedFieldsPolicy());
  }

  @Test
  void shouldResolveWithTwoLevelComposedAnnotation() throws Exception {
    MethodParameter param = getParam("nestedComposedMethod", QueryPlan.class);
    assertThat(resolver.supportsParameter(param)).isTrue();

    QueryPlan<?> plan = resolve(param);

    assertThat(plan.entityType()).isEqualTo(TestEntity.class);
    assertTestEntitySearchPolicy(plan.allowedFieldsPolicy());
  }

  @Test
  void shouldResolveComposedAnnotationWithAliasForOverride() throws Exception {
    MethodParameter param = getParam("aliasOverrideMethod", QueryPlan.class);
    assertThat(resolver.supportsParameter(param)).isTrue();

    QueryPlan<?> plan = resolve(param);

    assertThat(plan.entityType()).isEqualTo(OtherEntity.class);
    plan.allowedFieldsPolicy().validateFilter("name");
    assertThatThrownBy(() -> plan.allowedFieldsPolicy().validateFilter("status"))
        .isInstanceOf(DisallowedFieldException.class);
  }

  @Test
  void shouldPreferDirectAnnotationOverComposedAnnotation() throws Exception {
    QueryPlan<?> plan = resolve(getParam("directAndComposedMethod", QueryPlan.class));

    assertThat(plan.entityType()).isEqualTo(OtherEntity.class);
    plan.allowedFieldsPolicy().validateFilter("code");
    plan.allowedFieldsPolicy().validateSort("code");
    assertThatThrownBy(() -> plan.allowedFieldsPolicy().validateFilter("name"))
        .isInstanceOf(DisallowedFieldException.class);
  }

  @Test
  void shouldResolveComposedAnnotationDeclaredOnInterfaceMethod() throws Exception {
    Method method = SearchController.class.getMethod("search", QueryPlan.class);
    MethodParameter param =
        new HandlerMethod(new SearchController(), method).getMethodParameters()[0];
    assertThat(resolver.supportsParameter(param)).isTrue();

    QueryPlan<?> plan = resolve(param);

    assertThat(plan.entityType()).isEqualTo(TestEntity.class);
    assertTestEntitySearchPolicy(plan.allowedFieldsPolicy());
  }

  private QueryPlan<?> resolve(MethodParameter param) {
    NativeWebRequest webRequest = mock(NativeWebRequest.class);
    when(webRequest.getParameterMap()).thenReturn(Map.of());
    return (QueryPlan<?>) resolver.resolveArgument(param, null, webRequest, null);
  }

  /** Asserts the policy of {@link TestEntitySearch}: filter {name, status}, sort {name}. */
  private void assertTestEntitySearchPolicy(AllowedFieldsPolicy policy) {
    assertThat(policy.isAllowAll()).isFalse();
    policy.validateFilter("name");
    policy.validateFilter("status");
    policy.validateSort("name");
    assertThatThrownBy(() -> policy.validateSort("status"))
        .isInstanceOf(DisallowedFieldException.class);
    assertThatThrownBy(() -> policy.validateFilter("price"))
        .isInstanceOf(DisallowedFieldException.class);
  }

  private MethodParameter getParam(String methodName, Class<?> paramType) throws Exception {
    Method method = getClass().getDeclaredMethod(methodName, paramType);
    MethodParameter param = new MethodParameter(method, 0);
    param.initParameterNameDiscovery(null);
    return param;
  }
}
