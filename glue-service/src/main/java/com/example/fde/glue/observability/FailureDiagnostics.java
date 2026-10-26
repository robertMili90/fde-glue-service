package com.example.fde.glue.observability;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class FailureDiagnostics {
  private FailureDiagnostics() {}

  public static String describe(Throwable failure) {
    StringBuilder diagnostic = new StringBuilder();
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    for (int depth = 0; failure != null && depth < 8 && visited.add(failure); depth++) {
      if (depth > 0) {
        diagnostic.append("; causedBy=");
      }
      diagnostic.append(failure.getClass().getName());
      if (failure instanceof SQLException databaseFailure) {
        String sqlState = databaseFailure.getSQLState();
        if (sqlState != null && sqlState.matches("[A-Z0-9]{5}")) {
          diagnostic.append(" sqlState=").append(sqlState);
        }
        diagnostic.append(" vendorCode=").append(databaseFailure.getErrorCode());
      }
      // Never copy exception messages: provider and SQL errors may include bodies,
      // credentials or bound values. Preserve stack locations and cause types.
      StackTraceElement[] stack = failure.getStackTrace();
      int includedFrames = 0;
      for (StackTraceElement frame : stack) {
        if (includedFrames == 0 || frame.getClassName().startsWith("com.example.fde.")) {
          diagnostic.append(" at ").append(frame);
          includedFrames++;
        }
        if (includedFrames >= 8) {
          break;
        }
      }
      failure = failure.getCause();
    }
    return diagnostic.toString();
  }
}
