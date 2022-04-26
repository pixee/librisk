package io.pixee.librisk;

import java.util.Optional;

record BinaryLocation(
    String jarEntryPath, MethodDescriptor methodDescriptor, Optional<Integer> lineNumber) {}
