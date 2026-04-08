package com.borjaglez.specrepository.boot4;

import java.lang.annotation.Annotation;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.AbstractRepositoryConfigurationSourceSupport;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.JpaRepositoryConfigExtension;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.util.StringUtils;

import com.borjaglez.specrepository.jpa.SpecificationRepositoryFactoryBean;
import com.borjaglez.specrepository.jpa.SpecificationRepositoryImpl;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;
import com.borjaglez.specrepository.jpa.spi.SpecificationRepositoryCustomizer;
import com.borjaglez.specrepository.jpa.spi.ValueConverter;
import com.borjaglez.specrepository.jpa.support.PathResolver;
import com.borjaglez.specrepository.jpa.support.QueryPlanSpecificationFactory;
import com.borjaglez.specrepository.jpa.support.SpecificationRepositoryConfiguration;

@AutoConfiguration(
    beforeName =
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
    afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    after = TaskExecutionAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnClass(JpaRepository.class)
@ConditionalOnMissingBean({JpaRepositoryFactoryBean.class, JpaRepositoryConfigExtension.class})
@ConditionalOnBooleanProperty(name = "spring.data.jpa.repositories.enabled", matchIfMissing = true)
@Import(SpecificationJpaRepositoriesRegistrar.class)
public class SpecificationRepositoryAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  SpecificationRepositoryConfiguration specificationRepositoryConfiguration(
      ObjectProvider<OperatorHandler> operatorHandlers,
      ObjectProvider<ValueConverter> valueConverters,
      ObjectProvider<SpecificationRepositoryCustomizer> customizers,
      ObjectProvider<ConversionService> conversionServices,
      ObjectProvider<PathResolver> pathResolvers,
      ObjectProvider<QueryPlanSpecificationFactory> specificationFactories) {
    SpecificationRepositoryConfiguration.Builder builder =
        SpecificationRepositoryConfiguration.builder();
    valueConverters.orderedStream().forEach(builder::addValueConverter);
    builder.addDefaultValueConverters().addDefaultOperatorHandlers();
    operatorHandlers.orderedStream().forEach(builder::addOperatorHandler);
    conversionServices.ifAvailable(builder::conversionService);
    pathResolvers.ifAvailable(builder::pathResolver);
    specificationFactories.ifAvailable(builder::specificationFactory);
    customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
    return builder.build();
  }
}

final class SpecificationJpaRepositoriesRegistrar
    extends AbstractRepositoryConfigurationSourceSupport {

  private BootstrapMode bootstrapMode;

  @Override
  protected Class<? extends Annotation> getAnnotation() {
    return EnableJpaRepositories.class;
  }

  @Override
  protected Class<?> getConfiguration() {
    return EnableJpaRepositoriesConfiguration.class;
  }

  @Override
  protected RepositoryConfigurationExtension getRepositoryConfigurationExtension() {
    return new JpaRepositoryConfigExtension();
  }

  @Override
  protected BootstrapMode getBootstrapMode() {
    return this.bootstrapMode != null ? this.bootstrapMode : BootstrapMode.DEFAULT;
  }

  @Override
  public void setEnvironment(Environment environment) {
    super.setEnvironment(environment);
    configureBootstrapMode(environment);
  }

  private void configureBootstrapMode(Environment environment) {
    String property = environment.getProperty("spring.data.jpa.repositories.bootstrap-mode");
    if (StringUtils.hasText(property)) {
      this.bootstrapMode = BootstrapMode.valueOf(property.toUpperCase(Locale.ENGLISH));
    }
  }

  @EnableJpaRepositories(
      repositoryBaseClass = SpecificationRepositoryImpl.class,
      repositoryFactoryBeanClass = SpecificationRepositoryFactoryBean.class)
  private static final class EnableJpaRepositoriesConfiguration {}
}
