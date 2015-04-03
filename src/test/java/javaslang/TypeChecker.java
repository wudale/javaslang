/*     / \____  _    ______   _____ / \____   ____  _____
 *    /  \__  \/ \  / \__  \ /  __//  \__  \ /    \/ __  \   Javaslang
 *  _/  // _\  \  \/  / _\  \\_  \/  // _\  \  /\  \__/  /   Copyright 2014-2015 Daniel Dietrich
 * /___/ \_____/\____/\_____/____/\___\_____/_/  \_/____/    Licensed under the Apache License, Version 2.0
 */
package javaslang;

import javaslang.collection.List;
import javaslang.collection.Stream;
import javaslang.collection.Traversable;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class TypeChecker {

    @Test
    @Ignore
    public void shouldHaveAConsistentTypeSystem() throws Exception {
        final Stream<String> msgs = loadClasses("src-gen/main/java").appendAll(loadClasses("src/main/java"))
                .map(clazz -> Tuple.of(clazz, getUnoverriddenMethods(clazz)))
                .filter(findings -> !findings._2.isEmpty())
                .sort((t1, t2) -> t1._1.getName().compareTo(t2._1.getName()))
                .map(findings -> String.format("%s has to override the following methods with return type %s:\n%s",
                        findings._1.getName(), findings._1.getSimpleName(), findings._2.map(m -> "* " + m).join("\n")));
        if (!msgs.isEmpty()) {
            throw new AssertionError(msgs.join("\n\n", "Unoverriden methods found.\n", ""));
        }
    }

    Traversable<Method> getUnoverriddenMethods(Class<?> clazz) {
        final Traversable<Class<?>> superClasses = Stream.of(clazz.getInterfaces())
                .append(clazz.getSuperclass())
                .filter(c -> c != null);
        if (superClasses.isEmpty()) {
            return Stream.nil();
        } else {
            final Traversable<ComparableMethod> superMethods = getOverridableMethods(superClasses).filter(comparableMethod ->
                    // We're interested in methods that should be overridden with actual type as return type.
                    // Because we check this recursively, the class hierarchy is consistent here.
                    comparableMethod.m.getDeclaringClass().equals(comparableMethod.m.getReturnType()));
            final Traversable<ComparableMethod> thisMethods = getOverridableMethods(Stream.of(clazz));
            return superMethods.filter(superMethod -> thisMethods
                    .findFirst(thisMethod -> thisMethod.equals(superMethod))
                            // TODO: special case if visibility is package private and classes are in different package
                    .map(thisMethod -> !clazz.equals(thisMethod.m.getReturnType()))
                    .orElse(true))
                    .map(comparableMethod -> comparableMethod.m)
                    // TODO: .sort()
                    ;
        }
    }

    // TODO: change Traversable to Seq after running TypeChecker and fixing findings
    Traversable<ComparableMethod> getOverridableMethods(Traversable<Class<?>> classes) {
        return classes
                .flatMap(clazz ->
                        Stream.of(clazz.getDeclaredMethods()).filter((Method m) ->
                                // https://javax0.wordpress.com/2014/02/26/syntethic-and-bridge-methods/
                                !m.isBridge() && !m.isSynthetic() &&
                                        // private, static and final methods cannot be overridden
                                        !Modifier.isPrivate(m.getModifiers()) && !Modifier.isFinal(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()) &&
                                        // we also don't want to cope with methods declared in Object
                                        !m.getDeclaringClass().equals(Object.class))
                                .map(ComparableMethod::new));
    }

    Stream<Class<?>> loadClasses(String srcDir) throws Exception {
        final Path path = Paths.get(srcDir);
        final java.util.List<Class<?>> classes = new ArrayList<>();
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isDirectory()) {
                    final String name = file.subpath(path.getNameCount(), file.getNameCount()).toString();
                    if (name.endsWith(".java") && !name.endsWith(File.separator + "package-info.java")) {
                        final String className = name.substring(0, name.length() - ".java".length())
                                .replaceAll("/", ".")
                                .replaceAll("\\\\", ".");
                        try {
                            final Class<?> clazz = getClass().getClassLoader().loadClass(className);
                            classes.add(clazz);
                        } catch (ClassNotFoundException e) {
                            throw new IOException(e);
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return Stream.of(classes);
    }

    static class ComparableMethod {

        final Method m;

        ComparableMethod(Method m) {
            this.m = m;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof ComparableMethod) {
                final ComparableMethod that = (ComparableMethod) o;
                return Objects.equals(this.m.getName(), that.m.getName()) &&
                        Arrays.equals(this.m.getParameterTypes(), that.m.getParameterTypes());
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(m.getName(), Arrays.hashCode(m.getParameterTypes()));
        }

        @Override
        public String toString() {
            return m.getName() +
                    List.of(m.getParameterTypes()).map(Class::getName).join(", ", "(", ")") +
                    ": " +
                    m.getReturnType().getName();
        }
    }
}
