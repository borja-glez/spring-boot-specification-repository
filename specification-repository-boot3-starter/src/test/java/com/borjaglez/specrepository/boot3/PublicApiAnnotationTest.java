package com.borjaglez.specrepository.boot3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apiguardian.api.API;
import org.junit.jupiter.api.Test;

/** Every public top-level type of the module declares its stability with {@link API @API}. */
class PublicApiAnnotationTest {

  @Test
  void everyPublicTopLevelTypeShouldBeAnnotatedWithApi() throws Exception {
    List<Class<?>> types = topLevelTypes(SpecificationRepositoryAutoConfiguration.class);

    assertThat(types).contains(SpecificationRepositoryAutoConfiguration.class);
    assertThat(
            types.stream()
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .filter(type -> !type.isAnnotationPresent(API.class))
                .map(Class::getName))
        .as("public top-level types without @API")
        .isEmpty();
  }

  /** The top-level types compiled into the same classes directory or jar as {@code anchor}. */
  static List<Class<?>> topLevelTypes(Class<?> anchor) throws IOException, URISyntaxException {
    Path root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .map(root::relativize)
          .map(Path::toString)
          .filter(name -> name.endsWith(".class"))
          .filter(name -> !name.contains("$"))
          .filter(name -> !name.endsWith("package-info.class"))
          .map(name -> name.substring(0, name.length() - ".class".length()))
          .map(name -> name.replace('\\', '.').replace('/', '.'))
          .<Class<?>>map(name -> load(name, anchor.getClassLoader()))
          .toList();
    }
  }

  private static Class<?> load(String name, ClassLoader loader) {
    try {
      return Class.forName(name, false, loader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }
}
