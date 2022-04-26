package io.pixee.librisk;

import java.util.Optional;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

record MethodDescriptor(
    String[] annotations,
    String name,
    Type returnType,
    Type[] argumentTypes,
    Optional<Integer> firstLine) {

  static MethodDescriptor from(final MethodNode method, final MethodInsnNode methodInsn) {
    Type returnType = Type.getReturnType(methodInsn.desc);
    Type[] argumentTypes = Type.getArgumentTypes(methodInsn.desc);
    return new MethodDescriptor(
        new String[0],
        methodInsn.name,
        returnType,
        argumentTypes,
        findFirstLine(method.instructions));
  }

  private static Optional<Integer> findFirstLine(final InsnList insnList) {
    for (final AbstractInsnNode inNode : insnList) {
      if (inNode instanceof LineNumberNode) {
        return Optional.of(((LineNumberNode) inNode).line);
      }
    }
    return Optional.empty();
  }
}
