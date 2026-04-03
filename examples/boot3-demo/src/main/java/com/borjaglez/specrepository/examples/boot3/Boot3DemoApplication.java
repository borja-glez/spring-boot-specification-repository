package com.borjaglez.specrepository.examples.boot3;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.borjaglez.specrepository.jpa.EnableSpecificationRepositories;

@SpringBootApplication
@EnableSpecificationRepositories
public class Boot3DemoApplication {
  public static void main(String[] args) {
    org.springframework.boot.SpringApplication.run(Boot3DemoApplication.class, args);
  }
}
