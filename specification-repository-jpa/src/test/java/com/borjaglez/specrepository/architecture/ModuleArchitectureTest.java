package com.borjaglez.specrepository.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.apiguardian.api.API;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the module boundaries of the jpa module. */
@AnalyzeClasses(
    packages = "com.borjaglez.specrepository.jpa",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

  @ArchTest
  static final ArchRule jpaDoesNotDependOnHttpOrStarters =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
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
