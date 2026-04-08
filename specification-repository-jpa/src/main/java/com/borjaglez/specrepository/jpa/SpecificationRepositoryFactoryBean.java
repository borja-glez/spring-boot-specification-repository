package com.borjaglez.specrepository.jpa;

import java.io.Serializable;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.util.Assert;

import com.borjaglez.specrepository.jpa.support.SpecificationRepositoryConfiguration;

public class SpecificationRepositoryFactoryBean<
        R extends Repository<T, I>, T, I extends Serializable>
    extends JpaRepositoryFactoryBean<R, T, I> implements BeanFactoryAware {

  private ListableBeanFactory beanFactory;

  public SpecificationRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
    super(repositoryInterface);
  }

  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    Assert.isInstanceOf(ListableBeanFactory.class, beanFactory, "beanFactory must be listable");
    super.setBeanFactory(beanFactory);
    this.beanFactory = (ListableBeanFactory) beanFactory;
  }

  @Override
  protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
    return new SpecificationRepositoryFactory(entityManager, resolveConfiguration());
  }

  private SpecificationRepositoryConfiguration resolveConfiguration() {
    if (beanFactory == null) {
      return SpecificationRepositoryConfiguration.defaultConfiguration();
    }
    Map<String, SpecificationRepositoryConfiguration> configurations =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
            beanFactory, SpecificationRepositoryConfiguration.class, false, true);
    if (configurations.isEmpty()) {
      return SpecificationRepositoryConfiguration.defaultConfiguration();
    }
    if (configurations.size() > 1) {
      throw new IllegalStateException(
          "Expected a single SpecificationRepositoryConfiguration bean but found "
              + configurations.size());
    }
    return configurations.values().iterator().next();
  }

  private static final class SpecificationRepositoryFactory extends JpaRepositoryFactory {
    private final EntityManager entityManager;
    private final SpecificationRepositoryConfiguration configuration;

    private SpecificationRepositoryFactory(
        EntityManager entityManager, SpecificationRepositoryConfiguration configuration) {
      super(entityManager);
      this.entityManager = entityManager;
      this.configuration = configuration;
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
      return SpecificationRepositoryImpl.class;
    }

    @Override
    protected JpaRepositoryImplementation<?, ?> getTargetRepository(
        RepositoryInformation information, EntityManager entityManager) {
      JpaEntityInformation<?, Serializable> entityInformation =
          getEntityInformation(information.getDomainType());
      return (JpaRepositoryImplementation<?, ?>)
          getTargetRepositoryViaReflection(
              information, entityInformation, this.entityManager, configuration);
    }
  }
}
