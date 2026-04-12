package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.borjaglez.specrepository.http.HttpFilterParser;
import com.borjaglez.specrepository.http.HttpFilterParserConfiguration;

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
}
