package com.borjaglez.specrepository.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.apiguardian.api.API;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the module boundaries of the core module. */
@AnalyzeClasses(
    packages = "com.borjaglez.specrepository.core",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

  @ArchTest
  static final ArchRule coreIsFreeOfPersistenceAndSpringExceptData =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
          .orShould()
          .dependOnClassesThat(
              com.tngtech.archunit.base.DescribedPredicate.and(
                  com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage(
                      "org.springframework.."),
                  com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage(
                      "org.springframework.data.domain..")));

  @ArchTest
  static final ArchRule coreDoesNotDependOnOtherModules =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.borjaglez.specrepository.jpa..",
              "com.borjaglez.specrepository.http..",
              "com.borjaglez.specrepository.boot3..",
              "com.borjaglez.specrepository.boot4..");

  @ArchTest
  static final ArchRule noFieldInjection =
      fields()
          .should()
          .notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");

  @ArchTest
  static final ArchRule publicTypesDeclareStability =
      classes()
          .that()
          .arePublic()
          .and()
          .areTopLevelClasses()
          .and()
          .haveSimpleNameNotEndingWith("package-info")
          .should()
          .beAnnotatedWith(API.class);
}
