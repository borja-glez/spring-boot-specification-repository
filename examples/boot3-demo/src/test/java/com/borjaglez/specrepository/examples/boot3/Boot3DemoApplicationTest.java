package com.borjaglez.specrepository.examples.boot3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.borjaglez.specrepository.examples.boot3.repository.ProductRepository;

@SpringBootTest(classes = Boot3DemoApplication.class)
class Boot3DemoApplicationTest {

  @Autowired private ProductRepository productRepository;

  @Test
  void contextLoads() {
    assertThat(productRepository.query()).isNotNull();
  }
}
