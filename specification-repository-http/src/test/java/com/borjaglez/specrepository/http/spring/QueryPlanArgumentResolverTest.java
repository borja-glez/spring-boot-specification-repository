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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AliasFor;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.GroupCondition;
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
  void filterableOnlyMethod(
      @FilterableQuery(
              value = TestEntity.class,
              filterableFields = {"name"})
          QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void allowAllFieldsMethod(
      @FilterableQuery(value = TestEntity.class, allowAllFields = true)
          QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void allowAllFieldsWithFilterableFieldsMethod(
      @FilterableQuery(
              value = TestEntity.class,
              allowAllFields = true,
              filterableFields = {"name"})
          QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void allowAllFieldsWithSortableFieldsMethod(
      @FilterableQuery(
              value = TestEntity.class,
              allowAllFields = true,
              sortableFields = {"name"})
          QueryPlan<TestEntity> query) {}

  @Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @FilterableQuery(value = TestEntity.class, allowAllFields = true)
  @interface OpenTestEntitySearch {}

  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  @OpenTestEntitySearch
  @interface NestedOpenTestEntitySearch {}

  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  @FilterableQuery(TestEntity.class)
  @interface ToggleableSearch {
    @AliasFor(annotation = FilterableQuery.class, attribute = "allowAllFields")
    boolean value() default false;
  }

  @SuppressWarnings("unused")
  void composedAllowAllFieldsMethod(@OpenTestEntitySearch QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void nestedAllowAllFieldsMethod(@NestedOpenTestEntitySearch QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void aliasAllowAllFieldsMethod(@ToggleableSearch(true) QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void aliasDefaultAllowAllFieldsMethod(@ToggleableSearch QueryPlan<TestEntity> query) {}

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

  @Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @FilterableQuery(
      value = TestEntity.class,
      filterableFields = {"name", "code"},
      caseInsensitiveFields = {"name"})
  @interface CaseInsensitiveSearch {}

  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  @CaseInsensitiveSearch
  @interface NestedCaseInsensitiveSearch {}

  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  @FilterableQuery(value = TestEntity.class, allowAllFields = true)
  @interface OverridableCaseInsensitiveSearch {
    @AliasFor(annotation = FilterableQuery.class, attribute = "caseInsensitiveFields")
    String[] value();
  }

  @SuppressWarnings("unused")
  void caseInsensitiveMethod(
      @FilterableQuery(
              value = TestEntity.class,
              allowAllFields = true,
              caseInsensitiveFields = {"name"})
          QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void composedCaseInsensitiveMethod(@CaseInsensitiveSearch QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void nestedCaseInsensitiveMethod(@NestedCaseInsensitiveSearch QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void aliasCaseInsensitiveMethod(
      @OverridableCaseInsensitiveSearch("code") QueryPlan<TestEntity> query) {}

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
  void shouldRejectADisallowedFilterDuringArgumentResolution() throws Exception {
    MethodParameter param = getParam("annotatedMethod", QueryPlan.class);
    Map<String, String[]> params = Map.of("filter", new String[] {"status:eq:ACTIVE"});

    assertThatThrownBy(() -> resolve(param, params))
        .isInstanceOfSatisfying(
            DisallowedFieldException.class,
            exception -> {
              assertThat(exception.field()).isEqualTo("status");
              assertThat(exception.usage()).isEqualTo("filtering");
            });
  }

  @Test
  void shouldRejectADisallowedOrFilterDuringArgumentResolution() throws Exception {
    MethodParameter param = getParam("annotatedMethod", QueryPlan.class);
    Map<String, String[]> params =
        Map.of("orFilter", new String[] {"name:eq:John;status:eq:ACTIVE"});

    assertThatThrownBy(() -> resolve(param, params))
        .isInstanceOf(DisallowedFieldException.class)
        .hasMessage("Field 'status' is not allowed for filtering");
  }

  @Test
  void shouldRejectADisallowedSortDuringArgumentResolution() throws Exception {
    MethodParameter param = getParam("annotatedMethod", QueryPlan.class);
    Map<String, String[]> params = Map.of("sort", new String[] {"status,desc"});

    assertThatThrownBy(() -> resolve(param, params))
        .isInstanceOfSatisfying(
            DisallowedFieldException.class,
            exception -> {
              assertThat(exception.field()).isEqualTo("status");
              assertThat(exception.usage()).isEqualTo("sorting");
            });
  }

  @Test
  void shouldKeepThePolicyOnAResolvedPlanWithAllowedInput() throws Exception {
    QueryPlan<?> plan =
        resolve(
            getParam("annotatedMethod", QueryPlan.class),
            Map.of("filter", new String[] {"name:eq:John"}, "sort", new String[] {"name,asc"}));

    assertThat(plan.rootCondition().conditions()).hasSize(1);
    assertThat(plan.serverCondition().conditions()).isEmpty();
    assertThatThrownBy(() -> plan.allowedFieldsPolicy().validateSort("status"))
        .isInstanceOf(DisallowedFieldException.class);
  }

  @Test
  void shouldDenyFilteringWhenNoFieldListsAreDeclared() throws Exception {
    MethodParameter param = getParam("defaultAnnotatedMethod", QueryPlan.class);
    Map<String, String[]> params = Map.of("filter", new String[] {"name:eq:John"});

    assertThatThrownBy(() -> resolve(param, params))
        .isInstanceOfSatisfying(
            UndeclaredFieldListException.class,
            exception -> {
              assertThat(exception).isInstanceOf(DisallowedFieldException.class);
              assertThat(exception.field()).isEqualTo("name");
              assertThat(exception.usage()).isEqualTo("filtering");
              assertThat(exception.attribute()).isEqualTo("filterableFields");
            })
        .hasMessage(
            "Field 'name' is not allowed for filtering: @FilterableQuery declares no"
                + " filterableFields. Declare filterableFields, or set allowAllFields = true to"
                + " allow every field.");
  }

  @Test
  void shouldDenyOrFiltersWhenNoFieldListsAreDeclared() throws Exception {
    MethodParameter param = getParam("defaultAnnotatedMethod", QueryPlan.class);
    Map<String, String[]> params = Map.of("orFilter", new String[] {"name:eq:a;status:eq:b"});

    assertThatThrownBy(() -> resolve(param, params))
        .isInstanceOf(UndeclaredFieldListException.class)
        .hasMessageContaining("declares no filterableFields");
  }

  @Test
  void shouldDenySortingWhenNoFieldListsAreDeclared() throws Exception {
    MethodParameter param = getParam("defaultAnnotatedMethod", QueryPlan.class);
    Map<String, String[]> params = Map.of("sort", new String[] {"name,asc"});

    assertThatThrownBy(() -> resolve(param, params))
        .isInstanceOfSatisfying(
            UndeclaredFieldListException.class,
            exception -> {
              assertThat(exception.field()).isEqualTo("name");
              assertThat(exception.usage()).isEqualTo("sorting");
              assertThat(exception.attribute()).isEqualTo("sortableFields");
            })
        .hasMessage(
            "Field 'name' is not allowed for sorting: @FilterableQuery declares no"
                + " sortableFields. Declare sortableFields, or set allowAllFields = true to"
                + " allow every field.");
  }

  @Test
  void shouldResolveAPlanThatDeniesEveryFieldWhenNoFieldListsAreDeclared() throws Exception {
    QueryPlan<?> plan = resolve(getParam("defaultAnnotatedMethod", QueryPlan.class));

    AllowedFieldsPolicy policy = plan.allowedFieldsPolicy();
    assertThat(policy.isAllowAll()).isFalse();
    assertThatThrownBy(() -> policy.validateFilter("name"))
        .isInstanceOf(DisallowedFieldException.class);
    assertThatThrownBy(() -> policy.validateSort("name"))
        .isInstanceOf(DisallowedFieldException.class);
  }

  @Test
  void shouldAllowEveryFieldWhenAllowAllFieldsIsSet() throws Exception {
    QueryPlan<?> plan =
        resolve(
            getParam("allowAllFieldsMethod", QueryPlan.class),
            Map.of(
                "filter", new String[] {"anything:eq:x"}, "sort", new String[] {"whatever,desc"}));

    assertThat(plan.rootCondition().conditions()).hasSize(1);
    assertThat(plan.allowedFieldsPolicy()).isSameAs(AllowedFieldsPolicy.allowAll());
  }

  @Test
  void shouldAllowEveryFieldWhenAllowAllFieldsIsSetThroughComposedAnnotations() throws Exception {
    for (String method :
        List.of(
            "composedAllowAllFieldsMethod",
            "nestedAllowAllFieldsMethod",
            "aliasAllowAllFieldsMethod")) {
      MethodParameter param = getParam(method, QueryPlan.class);
      assertThat(resolver.supportsParameter(param)).isTrue();

      QueryPlan<?> plan = resolve(param, Map.of("filter", new String[] {"anything:eq:x"}));

      assertThat(plan.entityType()).isEqualTo(TestEntity.class);
      assertThat(plan.allowedFieldsPolicy()).isSameAs(AllowedFieldsPolicy.allowAll());
    }
  }

  @Test
  void shouldDenyByDefaultThroughAComposedAnnotationWithoutAllowAllFields() throws Exception {
    MethodParameter param = getParam("aliasDefaultAllowAllFieldsMethod", QueryPlan.class);

    assertThatThrownBy(() -> resolve(param, Map.of("filter", new String[] {"name:eq:x"})))
        .isInstanceOf(UndeclaredFieldListException.class);
  }

  @Test
  void shouldRejectAllowAllFieldsCombinedWithFieldLists() throws Exception {
    for (String method :
        List.of(
            "allowAllFieldsWithFilterableFieldsMethod", "allowAllFieldsWithSortableFieldsMethod")) {
      MethodParameter param = getParam(method, QueryPlan.class);

      assertThatThrownBy(() -> resolve(param))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(
              "@FilterableQuery allowAllFields = true cannot be combined with filterableFields"
                  + " or sortableFields");
    }
  }

  @Test
  void shouldKeepDenyingSortingWhenOnlyFilterableFieldsAreDeclared() throws Exception {
    MethodParameter param = getParam("filterableOnlyMethod", QueryPlan.class);

    QueryPlan<?> plan = resolve(param, Map.of("filter", new String[] {"name:eq:x"}));
    assertThat(plan.rootCondition().conditions()).hasSize(1);

    assertThatThrownBy(() -> resolve(param, Map.of("sort", new String[] {"name,asc"})))
        .isInstanceOf(UndeclaredFieldListException.class)
        .hasMessageContaining("declares no sortableFields");
  }

  @Test
  void shouldKeepDenyingFilteringWhenOnlySortableFieldsAreDeclared() throws Exception {
    MethodParameter param = getParam("sortableOnlyMethod", QueryPlan.class);

    QueryPlan<?> plan = resolve(param, Map.of("sort", new String[] {"name,asc"}));
    assertThat(plan.sort().isSorted()).isTrue();

    assertThatThrownBy(() -> resolve(param, Map.of("filter", new String[] {"name:eq:x"})))
        .isInstanceOf(UndeclaredFieldListException.class)
        .hasMessageContaining("declares no filterableFields");
  }

  @Test
  void shouldKeepThePlainExceptionForAFieldMissingFromADeclaredList() throws Exception {
    MethodParameter param = getParam("annotatedMethod", QueryPlan.class);

    assertThatThrownBy(() -> resolve(param, Map.of("sort", new String[] {"status,asc"})))
        .isExactlyInstanceOf(DisallowedFieldException.class)
        .hasMessage("Field 'status' is not allowed for sorting");
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

  @Test
  void shouldIgnoreCaseOnlyForDeclaredCaseInsensitiveFields() throws Exception {
    QueryPlan<?> plan =
        resolve(
            getParam("caseInsensitiveMethod", QueryPlan.class),
            Map.of(
                "filter",
                new String[] {"name:contains:cafe", "code:contains:cafe", "name:gt:a"},
                "orFilter",
                new String[] {"name:eq:cafe;code:eq:cafe"}));

    var conditions = plan.rootCondition().conditions();
    assertThat(((PredicateCondition) conditions.get(0)).ignoreCase()).isTrue();
    assertThat(((PredicateCondition) conditions.get(1)).ignoreCase()).isFalse();
    assertThat(((PredicateCondition) conditions.get(2)).ignoreCase()).isFalse();
    var orGroup = (GroupCondition) conditions.get(3);
    assertThat(orGroup.conditions())
        .map(c -> ((PredicateCondition) c).ignoreCase())
        .containsExactly(true, false);
    assertThat(plan.allowedFieldsPolicy()).isSameAs(AllowedFieldsPolicy.allowAll());
  }

  @Test
  void shouldKeepCaseSensitiveMatchingByDefault() throws Exception {
    QueryPlan<?> plan =
        resolve(
            getParam("allowAllFieldsMethod", QueryPlan.class),
            Map.of("filter", new String[] {"name:contains:cafe"}));

    assertThat(((PredicateCondition) plan.rootCondition().conditions().get(0)).ignoreCase())
        .isFalse();
  }

  @Test
  void shouldHonourCaseInsensitiveFieldsFromComposedAnnotations() throws Exception {
    for (String method : List.of("composedCaseInsensitiveMethod", "nestedCaseInsensitiveMethod")) {
      QueryPlan<?> plan =
          resolve(
              getParam(method, QueryPlan.class),
              Map.of("filter", new String[] {"name:contains:cafe", "code:contains:cafe"}));

      assertThat(plan.rootCondition().conditions())
          .map(c -> ((PredicateCondition) c).ignoreCase())
          .containsExactly(true, false);
      plan.allowedFieldsPolicy().validateFilter("code");
      assertThatThrownBy(() -> plan.allowedFieldsPolicy().validateFilter("status"))
          .isInstanceOf(DisallowedFieldException.class);
    }
  }

  @Test
  void shouldHonourCaseInsensitiveFieldsOverriddenWithAliasFor() throws Exception {
    QueryPlan<?> plan =
        resolve(
            getParam("aliasCaseInsensitiveMethod", QueryPlan.class),
            Map.of("filter", new String[] {"name:contains:cafe", "code:contains:cafe"}));

    assertThat(plan.rootCondition().conditions())
        .map(c -> ((PredicateCondition) c).ignoreCase())
        .containsExactly(false, true);
  }

  private QueryPlan<?> resolve(MethodParameter param) {
    return resolve(param, Map.of());
  }

  private QueryPlan<?> resolve(MethodParameter param, Map<String, String[]> params) {
    NativeWebRequest webRequest = mock(NativeWebRequest.class);
    when(webRequest.getParameterMap()).thenReturn(params);
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
