package com.borjaglez.specrepository.examples.boot4postgres;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.borjaglez.specrepository.jpa.EnableSpecificationRepositories;

@SpringBootApplication
@EnableSpecificationRepositories
public class Boot4PostgresDemoApplication {
  public static void main(String[] args) {
    org.springframework.boot.SpringApplication.run(Boot4PostgresDemoApplication.class, args);
  }
}
