package com.borjaglez.specrepository.examples.boot4;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.borjaglez.specrepository.jpa.EnableSpecificationRepositories;

@SpringBootApplication
@EnableSpecificationRepositories
public class Boot4DemoApplication {
  public static void main(String[] args) {
    org.springframework.boot.SpringApplication.run(Boot4DemoApplication.class, args);
  }
}
