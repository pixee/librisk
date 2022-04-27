package io.pixee.librisk;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

record MethodDescriptor(
    String name, String returnType, List<String> argumentTypes, Optional<Integer> firstLine) {

  static MethodDescriptor from(final MethodNode method) {
    String returnTypeName = Type.getReturnType(method.desc).getClassName();
    Type[] argumentTypes = Type.getArgumentTypes(method.desc);
    List<String> argumentTypeNames = Arrays.stream(argumentTypes).map(Type::getClassName).toList();
    return new MethodDescriptor(
        method.name, returnTypeName, argumentTypeNames, findFirstLine(method.instructions));
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
