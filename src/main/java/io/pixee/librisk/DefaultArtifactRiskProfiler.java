package io.pixee.librisk;

import static io.pixee.librisk.MethodInvocationPredicate.MatchingOptions.CASE_INSENSITIVE;
import static io.pixee.librisk.MethodInvocationPredicate.MatchingOptions.CONTAINS;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class DefaultArtifactRiskProfiler implements ArtifactRiskProfiler {

  private final Set<MethodInvocationPredicate> riskyBehaviorPredicates;

  DefaultArtifactRiskProfiler() {
    this.riskyBehaviorPredicates = buildRiskyBehaviorPredicates();
  }

  private Set<MethodInvocationPredicate> buildRiskyBehaviorPredicates() {
    return Set.of(
        new MethodInvocationPredicate(
            Behavior.BASE64,
            "base64",
            Set.of(CONTAINS, CASE_INSENSITIVE),
            "decode",
            Set.of(CONTAINS, CASE_INSENSITIVE)),
        new MethodInvocationPredicate(
            Behavior.BASE64,
            "base64",
            Set.of(CONTAINS, CASE_INSENSITIVE),
            "encode",
            Set.of(CONTAINS, CASE_INSENSITIVE)),
        new MethodInvocationPredicate(
            Behavior.DESERIALIZATION,
            "java/io/ObjectInputStream",
            Set.of(),
            "readObject",
            Set.of()));
  }

  @Override
  public ArtifactRiskProfile profile(final File binary) throws IOException {

    Set<String> failedClasses = new HashSet<>();

    Set<BinaryBehaviorFound> riskyBehaviors = new HashSet<>();

    JarFile jarFile = new JarFile(binary);
    Enumeration<JarEntry> entries = jarFile.entries();
    LOG.info("Reading entries");
    while (entries.hasMoreElements()) {
      JarEntry jarEntry = entries.nextElement();
      LOG.info("Looking at: {}", jarEntry.getName());
      if (jarEntry.getName().endsWith(".class")) {
        try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
          byte[] bytes = Objects.requireNonNull(ByteStreams.toByteArray(inputStream));
          ClassReader reader = new ClassReader(bytes);
          ClassNode classNode = new ClassNode();
          reader.accept(classNode, 0);

          for (MethodNode method : classNode.methods) {
            List<MethodInsnNode> methodInsns = findAll(method.instructions, MethodInsnNode.class);
            methodInsns.forEach(
                invokeMethodInsn -> {
                  MethodDescriptor methodDescriptor =
                      MethodDescriptor.from(method, invokeMethodInsn);
                  for (MethodInvocationPredicate predicate : riskyBehaviorPredicates) {
                    if (predicate.test(invokeMethodInsn)) {
                      riskyBehaviors.add(
                          new BinaryBehaviorFound(
                              predicate.getBehavior(),
                              new BinaryLocation(
                                  jarEntry.getName(),
                                  methodDescriptor,
                                  findLineNumberForInstruction(
                                      method.instructions, invokeMethodInsn))));
                    }
                  }
                });
          }
        } catch (IOException e) {
          failedClasses.add(jarEntry.getName());
          LOG.error("Problem reading {}", jarEntry.getName(), e);
        }
      }
    }
    return new DefaultArtifactRiskProfile(riskyBehaviors, failedClasses);
  }

  /**
   * Returns the last line number instruction before the given bytecode instruction, or empty if not
   * found.
   */
  private static Optional<Integer> findLineNumberForInstruction(
      final InsnList insnList, final AbstractInsnNode insnNode) {
    Objects.requireNonNull(insnList);
    Objects.requireNonNull(insnNode);

    int idx = insnList.indexOf(insnNode);
    if (idx < 0) {
      throw new IllegalArgumentException("can't find instruction in list");
    }

    ListIterator<AbstractInsnNode> insnIt = insnList.iterator(idx);
    while (insnIt.hasPrevious()) {
      AbstractInsnNode node = insnIt.previous();
      if (node instanceof LineNumberNode) {
        return Optional.of(((LineNumberNode) node).line);
      }
    }

    return Optional.empty();
  }

  /** Find all instructions of the given type in the instruction list. */
  private <T> List<T> findAll(final InsnList insnList, final Class<T> insnType) {
    List<T> instances = new ArrayList<>();
    for (int i = 0; i < insnList.size(); i++) {
      AbstractInsnNode insn = insnList.get(i);
      if (insnType.isInstance(insn)) {
        instances.add((T) insn);
      }
    }
    return instances;
  }

  private static final Logger LOG = LogManager.getLogger(DefaultArtifactRiskProfiler.class);
}
