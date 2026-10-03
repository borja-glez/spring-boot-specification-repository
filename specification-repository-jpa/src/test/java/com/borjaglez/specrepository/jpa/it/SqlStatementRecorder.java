package com.borjaglez.specrepository.jpa.it;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate {@link StatementInspector} that records the SQL sent to the database while {@link
 * #capture(Runnable)} runs, so tests can assert on the generated statements.
 */
final class SqlStatementRecorder implements StatementInspector {
  private final ThreadLocal<List<String>> recording = new ThreadLocal<>();

  @Override
  public String inspect(String sql) {
    List<String> statements = recording.get();
    if (statements != null) {
      statements.add(sql);
    }
    return sql;
  }

  /** Runs the action and returns every SQL statement it prepared, in order. */
  List<String> capture(Runnable action) {
    List<String> statements = new ArrayList<>();
    recording.set(statements);
    try {
      action.run();
    } finally {
      recording.remove();
    }
    return statements;
  }
}
