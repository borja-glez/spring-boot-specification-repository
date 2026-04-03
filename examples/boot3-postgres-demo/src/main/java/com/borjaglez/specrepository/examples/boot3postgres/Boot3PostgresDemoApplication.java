package com.borjaglez.specrepository.examples.boot3postgres;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.borjaglez.specrepository.jpa.EnableSpecificationRepositories;

@SpringBootApplication
@EnableSpecificationRepositories
public class Boot3PostgresDemoApplication {
  public static void main(String[] args) {
    org.springframework.boot.SpringApplication.run(Boot3PostgresDemoApplication.class, args);
  }
}
