package com.borjaglez.specrepository.examples.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.borjaglez.specrepository.examples.boot4.repository.ProductRepository;

@SpringBootTest(classes = Boot4DemoApplication.class)
class Boot4DemoApplicationTest {

  @Autowired private ProductRepository productRepository;

  @Test
  void contextLoads() {
    assertThat(productRepository.query()).isNotNull();
  }
}
