package com.borjaglez.specrepository.jpa.it;

import java.text.Normalizer;

/**
 * Stand-in for PostgreSQL's {@code unaccent} on H2, registered as a function alias by {@link
 * JpaIntegrationTestConfiguration}: it removes combining marks after a canonical decomposition.
 */
public final class H2Functions {
  private H2Functions() {}

  public static String unaccent(String value) {
    return value == null
        ? null
        : Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
  }
}
