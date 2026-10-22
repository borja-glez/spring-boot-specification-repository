package com.borjaglez.specrepository.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.apiguardian.api.API;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the module boundaries of the http module. */
@AnalyzeClasses(
    packages = "com.borjaglez.specrepository.http",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

  @ArchTest
  static final ArchRule httpDoesNotDependOnJpa =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.borjaglez.specrepository.jpa..", "jakarta.persistence..", "org.hibernate..");

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
