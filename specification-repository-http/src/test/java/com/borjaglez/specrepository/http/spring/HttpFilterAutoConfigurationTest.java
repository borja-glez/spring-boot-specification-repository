package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.borjaglez.specrepository.http.HttpFilterParser;
import com.borjaglez.specrepository.http.HttpFilterParserConfiguration;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.HttpUnknownOperatorException;

class HttpFilterAutoConfigurationTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(HttpFilterAutoConfiguration.class));

  @Test
  void shouldRegisterBeans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(HttpFilterParser.class);
          assertThat(context).hasSingleBean(QueryPlanArgumentResolver.class);
        });
  }

  @Test
  void shouldRespectCustomParserBean() {
    HttpFilterParser customParser =
        new HttpFilterParser(HttpFilterParserConfiguration.builder().filterParam("custom").build());
    contextRunner
        .withBean(HttpFilterParser.class, () -> customParser)
        .run(
            context -> {
              assertThat(context).hasSingleBean(HttpFilterParser.class);
              assertThat(context.getBean(HttpFilterParser.class)).isSameAs(customParser);
            });
  }

  @Test
  void shouldKeepTheParserDefaultsWithoutProperties() {
    contextRunner.run(
        context -> {
          HttpFilterProperties properties = context.getBean(HttpFilterProperties.class);
          assertThat(properties.toParserConfiguration())
              .usingRecursiveComparison()
              .isEqualTo(HttpFilterParserConfiguration.defaults());
          HttpFilterParser parser = context.getBean(HttpFilterParser.class);
          assertThat(parser.parse(Map.of("filter", filters(20))).filters()).hasSize(20);
          assertThatThrownBy(() -> parser.parse(Map.of("filter", filters(21))))
              .isInstanceOf(HttpFilterSyntaxException.class)
              .hasMessageContaining("too many filters (max 20)");
        });
  }

  @Test
  void shouldBuildTheParserFromProperties() {
    contextRunner
        .withPropertyValues(
            "specrepository.http.filter-param=q",
            "specrepository.http.max-filters=2",
            "specrepository.http.allowed-operators=eq,in")
        .run(
            context -> {
              HttpFilterParser parser = context.getBean(HttpFilterParser.class);
              assertThat(parser.parse(Map.of("q", filters(2))).filters()).hasSize(2);
              assertThat(parser.parse(Map.of("filter", filters(1))).filters()).isEmpty();
              assertThatThrownBy(() -> parser.parse(Map.of("q", filters(3))))
                  .isInstanceOf(HttpFilterSyntaxException.class)
                  .hasMessageContaining("too many filters (max 2)");
              assertThatThrownBy(() -> parser.parse(Map.of("q", List.of("name:contains:x"))))
                  .isInstanceOf(HttpUnknownOperatorException.class);
            });
  }

  @Test
  void shouldFailToStartWithAnInvalidLimit() {
    contextRunner
        .withPropertyValues("specrepository.http.max-value-length=0")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessage("maxValueLength must be at least 1"));
  }

  @Test
  void shouldPreferCustomParserBeanOverProperties() {
    HttpFilterParser customParser = new HttpFilterParser();
    contextRunner
        .withPropertyValues("specrepository.http.max-filters=0")
        .withBean(HttpFilterParser.class, () -> customParser)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(HttpFilterParser.class)).isSameAs(customParser);
            });
  }

  @Test
  void shouldRespectCustomResolverBean() {
    QueryPlanArgumentResolver customResolver =
        new QueryPlanArgumentResolver(new HttpFilterParser());
    contextRunner
        .withBean(QueryPlanArgumentResolver.class, () -> customResolver)
        .run(
            context -> {
              assertThat(context).hasSingleBean(QueryPlanArgumentResolver.class);
              assertThat(context.getBean(QueryPlanArgumentResolver.class)).isSameAs(customResolver);
            });
  }

  @Test
  void shouldRegisterWebMvcConfigurerThatAddsResolver() {
    contextRunner.run(
        context -> {
          WebMvcConfigurer configurer = context.getBean(WebMvcConfigurer.class);
          List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
          configurer.addArgumentResolvers(resolvers);
          assertThat(resolvers).hasSize(1);
          assertThat(resolvers.get(0)).isInstanceOf(QueryPlanArgumentResolver.class);
        });
  }

  private static List<String> filters(int count) {
    List<String> filters = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      filters.add("name:eq:" + i);
    }
    return filters;
  }
}
