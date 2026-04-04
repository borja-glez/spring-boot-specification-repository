package com.borjaglez.specrepository.boot4;

import java.lang.annotation.Annotation;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.AbstractRepositoryConfigurationSourceSupport;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.JpaRepositoryConfigExtension;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.util.StringUtils;

import com.borjaglez.specrepository.jpa.SpecificationRepositoryImpl;

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
public class SpecificationRepositoryAutoConfiguration {}

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

  @EnableJpaRepositories(repositoryBaseClass = SpecificationRepositoryImpl.class)
  private static final class EnableJpaRepositoriesConfiguration {}
}
