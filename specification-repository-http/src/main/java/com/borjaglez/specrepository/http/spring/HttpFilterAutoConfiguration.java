package com.borjaglez.specrepository.http.spring;

import static org.apiguardian.api.API.Status.INTERNAL;

import java.util.List;

import org.apiguardian.api.API;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.borjaglez.specrepository.http.HttpFilterParser;

@AutoConfiguration
@ConditionalOnClass(HandlerMethodArgumentResolver.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@API(status = INTERNAL, since = "1.0.0")
public class HttpFilterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  HttpFilterParser httpFilterParser() {
    return new HttpFilterParser();
  }

  @Bean
  @ConditionalOnMissingBean
  QueryPlanArgumentResolver queryPlanArgumentResolver(HttpFilterParser parser) {
    return new QueryPlanArgumentResolver(parser);
  }

  @Bean
  @ConditionalOnMissingBean(name = "httpFilterWebMvcConfigurer")
  WebMvcConfigurer httpFilterWebMvcConfigurer(QueryPlanArgumentResolver resolver) {
    return new WebMvcConfigurer() {
      @Override
      public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
      }
    };
  }
}
