package io.pixee.librisk;

import java.util.Objects;
import java.util.Set;
import org.objectweb.asm.tree.MethodInsnNode;

public final class MethodOnlyMethodInvocationPredicate implements InvocationPredicate {

  private final Behavior behavior;
  private final String methodName;
  private final Set<MatchingOptions> methodNameMatchingOptions;

  MethodOnlyMethodInvocationPredicate(
      final Behavior behavior,
      final String methodName,
      final Set<MatchingOptions> methodNameMatchingOptions) {
    this.behavior = Objects.requireNonNull(behavior);
    this.methodName = Objects.requireNonNull(methodName);
    this.methodNameMatchingOptions = Objects.requireNonNull(methodNameMatchingOptions);
  }

  MethodOnlyMethodInvocationPredicate(final Behavior behavior, final String methodName) {
    this(behavior, methodName, Set.of());
  }

  @Override
  public boolean test(final MethodInsnNode method) {
    return checkName(method);
  }

  private boolean checkName(final MethodInsnNode method) {
    if (methodNameMatchingOptions.contains(MatchingOptions.CASE_INSENSITIVE)) {
      if (methodNameMatchingOptions.contains(MatchingOptions.CONTAINS)) {
        return methodName.toLowerCase().contains(method.name.toLowerCase());
      } else {
        return methodName.equalsIgnoreCase(method.name);
      }
    }
    if (methodNameMatchingOptions.contains(MatchingOptions.CONTAINS)) {
      return methodName.contains(method.name.toLowerCase());
    }
    return methodName.equals(method.name.toLowerCase());
  }

  @Override
  public Behavior getBehavior() {
    return behavior;
  }
}
