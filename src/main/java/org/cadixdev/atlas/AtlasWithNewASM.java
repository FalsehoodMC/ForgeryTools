/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.cadixdev.atlas;

import org.cadixdev.atlas.jar.JarFile;
import org.cadixdev.atlas.util.JarRepacker;
import org.cadixdev.bombe.analysis.InheritanceProvider;
import org.cadixdev.bombe.asm.analysis.ClassProviderInheritanceProvider;
import org.cadixdev.bombe.jar.ClassProvider;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.objectweb.asm.Opcodes;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * An Atlas describes {@link JarEntryTransformer transformations}, and an environment
 * to transform Java binaries.
 *
 * <p>Atlases are independent of the specific binary being processed, allowing them to
 * be used for multiple binary transformations.
 *
 * @author Jamie Mansfield
 * @since 0.1.0
 */
public class AtlasWithNewASM implements Closeable {

    private final List<Function<AtlasTransformerContext, JarEntryTransformer>> transformers = new ArrayList<>();
    private final List<Path> classpath = new ArrayList<>();

    /**
     * Gets the classpath available to the {@link InheritanceProvider inheritance provider}.
     * <p>
     * The classpath is a list of paths that correspond to jar files, classes within
     * those jars will be made available to the inheritance provider.
     *
     * @return The classpath
     */
    public List<Path> getClasspath() {
        return this.classpath;
    }

    /**
     * Installs a {@link JarEntryTransformer transformer} to the Atlas, noting that
     * each installed transformer will be constructed once for each binary processed.
     *
     * @param transformer The transformer constructor
     * @return {@code this}, for chaining
     */
    public AtlasWithNewASM install(final Function<AtlasTransformerContext, JarEntryTransformer> transformer) {
        this.transformers.add(transformer);
        return this;
    }

    /**
     * Runs the Atlas on the given input binary, saving the result to the output path.
     *
     * @param input The input binary
     * @param output The output binary
     * @throws IOException Should an issue occur reading the input JAR, or
     *                     reading the output JAR
     * @see #run(JarFile, Path)
     */
    public void run(final Path input, final Path output) throws IOException {
        try (final JarFile jar = new JarFile(input)) {
            this.run(jar, output);
        }
    }

    /**
     * Runs the Atlas on the given input {@link JarFile jar}, saving the result to the
     * given output path.
     *
     * @param jar The input jar
     * @param output The output binary
     * @throws IOException Should an issue occur reading the input JAR, or
     *                     reading the output JAR
     */
    public void run(final JarFile jar, final Path output) throws IOException {
        // Create a classpath for the current JAR file
        final List<ClassProvider> classpath = new ArrayList<>();
        classpath.add(jar);
        for (final Path jarPath : this.classpath) {
            classpath.add(new JarFile(jarPath));
        }

        // Create the context for the JAR file
        final AtlasTransformerContext context = new AtlasTransformerContext(
                new ClassProviderInheritanceProvider(Opcodes.ASM9, new CompositeClassProvider(classpath)) // FORGERY: Use ASM9
        );

        // Construct the transformers
        final JarEntryTransformer[] transformers = new JarEntryTransformer[this.transformers.size()];
        for (int i = 0; i < this.transformers.size(); i++) {
            transformers[i] = this.transformers.get(i).apply(context);
        }

        // Transform the JAR, and save to the output path
        jar.transform(output, transformers);

        JarRepacker.verifyJarManifest(output);

        // Close the JarFiles we made earlier
        for (final ClassProvider classProvider : classpath) {
            if (classProvider == jar) continue;
            if (!(classProvider instanceof Closeable)) continue;

            ((Closeable) classProvider).close();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <strong>Note that this will clear the classpath!</strong>
     */
    @Override
    public void close() throws IOException {
        this.classpath.clear();
    }

}
