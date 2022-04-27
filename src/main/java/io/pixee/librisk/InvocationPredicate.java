package io.pixee.librisk;

import java.util.function.Predicate;
import org.objectweb.asm.tree.MethodInsnNode;

public interface InvocationPredicate extends Predicate<MethodInsnNode> {

  Behavior getBehavior();
}
