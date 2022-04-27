package io.pixee.librisk;

import java.io.IOException;

public class DoesSystemCommands {

  void doesRuntimeExec() throws IOException {
    Runtime.getRuntime().exec("foo");
  }

  void doesProcessBuilderInit() {
    new ProcessBuilder();
  }

  void doesProcessBuilderStart(ProcessBuilder builder) throws IOException {
    builder.start();
  }

  void doesProcessBuilderCommand(int i, ProcessBuilder builder) {
    builder.command();
  }
}
