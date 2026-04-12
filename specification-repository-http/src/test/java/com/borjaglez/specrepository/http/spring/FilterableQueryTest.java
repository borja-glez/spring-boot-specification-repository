package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.borjaglez.specrepository.core.QueryPlan;

class FilterableQueryTest {

  static class TestEntity {}

  @SuppressWarnings("unused")
  void sampleMethod(
      @FilterableQuery(
              value = TestEntity.class,
              filterableFields = {"name", "status"},
              sortableFields = {"name"})
          QueryPlan<TestEntity> query) {}

  @SuppressWarnings("unused")
  void defaultsMethod(@FilterableQuery(TestEntity.class) QueryPlan<TestEntity> query) {}

  @Test
  void shouldExposeAnnotationValues() throws Exception {
    Method method = getClass().getDeclaredMethod("sampleMethod", QueryPlan.class);
    Annotation[][] annotations = method.getParameterAnnotations();
    FilterableQuery fq = (FilterableQuery) annotations[0][0];

    assertThat(fq.value()).isEqualTo(TestEntity.class);
    assertThat(fq.filterableFields()).containsExactly("name", "status");
    assertThat(fq.sortableFields()).containsExactly("name");
  }

  @Test
  void shouldHaveEmptyDefaultsForFieldLists() throws Exception {
    Method method = getClass().getDeclaredMethod("defaultsMethod", QueryPlan.class);
    Annotation[][] annotations = method.getParameterAnnotations();
    FilterableQuery fq = (FilterableQuery) annotations[0][0];

    assertThat(fq.filterableFields()).isEmpty();
    assertThat(fq.sortableFields()).isEmpty();
  }
}
