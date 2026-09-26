package com.borjaglez.specrepository.http.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the slice imports files. The Boot 4 file cannot be exercised by a real slice here because
 * the module is built against Boot 3, so its name and content are checked directly.
 */
class HttpFilterWebMvcTestImportsTest {

  private static final String BOOT3_IMPORTS =
      "META-INF/spring/org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc.imports";

  private static final String BOOT4_IMPORTS =
      "META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc.imports";

  @ParameterizedTest
  @ValueSource(strings = {BOOT3_IMPORTS, BOOT4_IMPORTS})
  void shouldListHttpFilterAutoConfiguration(String resource) throws IOException {
    assertThat(readOwnImports(resource))
        .containsExactly(HttpFilterAutoConfiguration.class.getName());
  }

  private static List<String> readOwnImports(String resource) throws IOException {
    ClassLoader classLoader = HttpFilterWebMvcTestImportsTest.class.getClassLoader();
    for (URL url : Collections.list(classLoader.getResources(resource))) {
      try (InputStream in = url.openStream()) {
        List<String> lines =
            new String(in.readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        if (lines.contains(HttpFilterAutoConfiguration.class.getName())) {
          return lines;
        }
      }
    }
    return List.of();
  }
}
