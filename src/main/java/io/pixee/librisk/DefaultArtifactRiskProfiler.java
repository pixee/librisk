package io.pixee.librisk;

import static io.pixee.librisk.MatchingOptions.CASE_INSENSITIVE;
import static io.pixee.librisk.MatchingOptions.CONTAINS;

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

  private final JarLoader jarLoader;
  private final Set<InvocationPredicate> riskyBehaviorPredicates;

  /** A set of seams for reading jars. */
  interface JarLoader {
    JarReader load(File file) throws IOException;
  }

  interface JarReader {
    Optional<ClassEntry> nextClassNode() throws IOException;

    Set<String> getFailedClasses();
  }

  DefaultArtifactRiskProfiler() {
    this(new DefaultJarLoader());
  }

  DefaultArtifactRiskProfiler(final JarLoader jarLoader) {
    this.jarLoader = Objects.requireNonNull(jarLoader);
    this.riskyBehaviorPredicates = buildRiskyBehaviorPredicates();
  }

  private Set<InvocationPredicate> buildRiskyBehaviorPredicates() {
    return Set.of(

        // base64
        new TypeAndMethodInvocationPredicate(
            Behavior.BASE64,
            "base64",
            Set.of(CONTAINS, CASE_INSENSITIVE),
            "decode",
            Set.of(CONTAINS, CASE_INSENSITIVE)),
        new TypeAndMethodInvocationPredicate(
            Behavior.BASE64,
            "base64",
            Set.of(CONTAINS, CASE_INSENSITIVE),
            "encode",
            Set.of(CONTAINS, CASE_INSENSITIVE)),

        // compilation
        new MethodOnlyMethodInvocationPredicate(Behavior.COMPILATION, "defineClass"),
        new MethodOnlyMethodInvocationPredicate(Behavior.COMPILATION, "parseExpression"),
        new MethodOnlyMethodInvocationPredicate(
            Behavior.COMPILATION, "evaluateExpression", Set.of()),
        new TypeAndMethodInvocationPredicate(
            Behavior.COMPILATION, "java/lang/Instrumentation", "redefineClass"),
        new TypeAndMethodInvocationPredicate(
            Behavior.COMPILATION, "java/lang/Instrumentation", "retransformClass"),
        new TypeAndMethodInvocationPredicate(
            Behavior.COMPILATION, "java/lang/Instrumentation", "appendToBootstrap"),

        // deserialization
        new TypeAndMethodInvocationPredicate(
            Behavior.DESERIALIZATION, "java/io/ObjectInputStream", "readObject"),
        new TypeAndMethodInvocationPredicate(
            Behavior.DESERIALIZATION, "java/io/ObjectInputStream", "defaultReadObject"),
        new TypeAndMethodInvocationPredicate(
            Behavior.DESERIALIZATION, "Kryo", Set.of(CONTAINS), "readObject", Set.of()),
        new TypeAndMethodInvocationPredicate(
            Behavior.DESERIALIZATION, "XStream", Set.of(CONTAINS), "fromXML", Set.of()),

        // native operations
        new TypeAndMethodInvocationPredicate(
            Behavior.NATIVE_OPERATION, "sun.misc.Unsafe", "getUnsafe"),
        new TypeAndMethodInvocationPredicate(
            Behavior.NATIVE_OPERATION, "jdk.unsupported.Unsafe", "getUnsafe"),
        new TypeAndMethodInvocationPredicate(
            Behavior.NATIVE_OPERATION, "jdk.internal.misc.Unsafe", "getUnsafe"),
        new TypeAndMethodInvocationPredicate(
            Behavior.NATIVE_OPERATION, "java/lang/System", "loadLibrary"),

        // outbound calls
        new TypeAndMethodInvocationPredicate(
            Behavior.OUTBOUND_HTTP, "java/net/URLConnection", "open"),
        new TypeAndMethodInvocationPredicate(
            Behavior.OUTBOUND_HTTP, "HttpClient", Set.of(CONTAINS), "open", Set.of()),
        new TypeAndMethodInvocationPredicate(
            Behavior.OUTBOUND_HTTP, "HttpClient", Set.of(CONTAINS), "newBuilder", Set.of()),
        new TypeAndMethodInvocationPredicate(
            Behavior.OUTBOUND_HTTP, "OkHttpClient", Set.of(CONTAINS), "newBuilder", Set.of()),
        new TypeAndMethodInvocationPredicate(
            Behavior.OUTBOUND_HTTP, "OkHttpClient$Builder", Set.of(CONTAINS), "<init>", Set.of()),

        // security
        new TypeAndMethodInvocationPredicate(
            Behavior.SECURITY_OPERATION, "java/lang/System", "setSecurityManager"),

        // system commands
        new TypeAndMethodInvocationPredicate(Behavior.SYSTEM_COMMANDS, "java/lang/Runtime", "exec"),
        new TypeAndMethodInvocationPredicate(
            Behavior.SYSTEM_COMMANDS, "java/lang/ProcessBuilder", "command"),
        new TypeAndMethodInvocationPredicate(
            Behavior.SYSTEM_COMMANDS, "java/lang/ProcessBuilder", "start"),
        new TypeAndMethodInvocationPredicate(
            Behavior.SYSTEM_COMMANDS, "java/lang/ProcessBuilder", "<init>"),
        new MethodOnlyMethodInvocationPredicate(Behavior.ZIP, "zip", Set.of(CONTAINS)));
  }

  private static class DefaultJarLoader implements JarLoader {
    @Override
    public JarReader load(final File file) throws IOException {
      return new DefaultJarReader(file);
    }
  }

  private static class DefaultJarReader implements JarReader {

    private final Enumeration<JarEntry> entries;
    private final JarFile jarFile;
    private final Set<String> failedClasses;

    private DefaultJarReader(final File binary) throws IOException {
      this.jarFile = new JarFile(binary);
      this.entries = jarFile.entries();
      this.failedClasses = new HashSet<>();
    }

    private ClassNode readClassNode(final InputStream inputStream) throws IOException {
      byte[] bytes = Objects.requireNonNull(ByteStreams.toByteArray(inputStream));
      ClassReader reader = new ClassReader(bytes);
      ClassNode classNode = new ClassNode();
      reader.accept(classNode, 0);
      return classNode;
    }

    @Override
    public Optional<ClassEntry> nextClassNode() throws IOException {
      while (entries.hasMoreElements()) {
        JarEntry jarEntry = entries.nextElement();
        if (jarEntry.getName().endsWith(".class")) {
          try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
            ClassNode classNode = readClassNode(inputStream);
            return Optional.of(new ClassEntry(classNode, jarEntry.getName()));
          }
        }
      }
      return Optional.empty();
    }

    @Override
    public Set<String> getFailedClasses() {
      return failedClasses;
    }
  }

  record ClassEntry(ClassNode classNode, String jarEntryPath) {}

  @Override
  public ArtifactRiskProfile profile(final File binary) throws IOException {
    Set<BinaryBehaviorFound> riskyBehaviors = new HashSet<>();
    JarReader jarReader = jarLoader.load(binary);
    Optional<ClassEntry> classEntryRef;
    while ((classEntryRef = jarReader.nextClassNode()).isPresent()) {
      ClassEntry classEntry = classEntryRef.get();
      ClassNode classNode = classEntry.classNode();
      for (MethodNode method : classNode.methods) {
        List<MethodInsnNode> methodInsns = findAll(method.instructions, MethodInsnNode.class);
        methodInsns.forEach(
            invokeMethodInsn -> {
              MethodDescriptor containingMethodDescriptor = MethodDescriptor.from(method);
              for (InvocationPredicate predicate : riskyBehaviorPredicates) {
                if (predicate.test(invokeMethodInsn)) {
                  LOG.info("Found risky behavior in {}", containingMethodDescriptor);
                  riskyBehaviors.add(
                      new BinaryBehaviorFound(
                          predicate.getBehavior(),
                          new BinaryLocation(
                              classEntry.jarEntryPath(),
                              containingMethodDescriptor,
                              findLineNumberForInstruction(method.instructions, invokeMethodInsn)),
                          toMethodInvocation(invokeMethodInsn)));
                }
              }
            });
      }
    }

    return new DefaultArtifactRiskProfile(riskyBehaviors, jarReader.getFailedClasses());
  }

  private MethodInvocation toMethodInvocation(final MethodInsnNode methodInsn) {
    return new MethodInvocation(methodInsn.owner, methodInsn.name, methodInsn.desc);
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
