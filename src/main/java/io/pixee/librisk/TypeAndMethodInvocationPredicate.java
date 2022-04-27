package io.pixee.librisk;

import java.util.Objects;
import java.util.Set;
import org.objectweb.asm.tree.MethodInsnNode;

public final class TypeAndMethodInvocationPredicate implements InvocationPredicate {

  private final Behavior behavior;
  private final String name;
  private final String owner;
  private final Set<MatchingOptions> ownerMatchingOptions;
  private final Set<MatchingOptions> nameMatchingOptions;

  TypeAndMethodInvocationPredicate(
      final Behavior behavior,
      final String owner,
      final Set<MatchingOptions> ownerMatchingOptions,
      final String name,
      final Set<MatchingOptions> nameMatchingOptions) {
    this.behavior = Objects.requireNonNull(behavior);
    this.owner = Objects.requireNonNull(owner);
    this.name = Objects.requireNonNull(name);
    this.ownerMatchingOptions = Objects.requireNonNull(ownerMatchingOptions);
    this.nameMatchingOptions = Objects.requireNonNull(nameMatchingOptions);
  }

  TypeAndMethodInvocationPredicate(final Behavior behavior, final String owner, final String name) {
    this(behavior, owner, Set.of(), name, Set.of());
  }

  @Override
  public boolean test(final MethodInsnNode method) {
    boolean ownerMatches = checkOwner(method);
    if (ownerMatches) {
      return checkName(method);
    }
    return false;
  }

  private boolean checkName(final MethodInsnNode method) {
    if (nameMatchingOptions.contains(MatchingOptions.CASE_INSENSITIVE)) {
      if (nameMatchingOptions.contains(MatchingOptions.CONTAINS)) {
        return name.toLowerCase().contains(method.name.toLowerCase());
      } else {
        return name.equalsIgnoreCase(method.name);
      }
    }
    if (nameMatchingOptions.contains(MatchingOptions.CONTAINS)) {
      return name.contains(method.name.toLowerCase());
    }
    return name.equals(method.name.toLowerCase());
  }

  private boolean checkOwner(final MethodInsnNode method) {
    if (ownerMatchingOptions.contains(MatchingOptions.CASE_INSENSITIVE)) {
      if (ownerMatchingOptions.contains(MatchingOptions.CONTAINS)) {
        return owner.toLowerCase().contains(method.owner.toLowerCase());
      } else {
        return owner.equalsIgnoreCase(method.owner);
      }
    }
    if (ownerMatchingOptions.contains(MatchingOptions.CONTAINS)) {
      return owner.contains(method.owner.toLowerCase());
    }
    return owner.equals(method.owner);
  }

  @Override
  public Behavior getBehavior() {
    return behavior;
  }
}
