package com.dlsc.animatorfx.sample;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ResolvedModule;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample application runs on the runtime alone: no editor, no importers, no AtlantaFX, no Ikonli, no GemsFX.
 */
class IndependenceTest {

    private static final List<String> TOOLING = List.of("com.dlsc.animatorfx.editor", "com.dlsc.animatorfx.importer",
            "atlantafx", "org.kordamp.ikonli", "com.dlsc.gemsfx");

    private static final Module APPLICATION = SampleApp.class.getModule();

    @Test
    void theApplicationIsANamedModuleThatRequiresOnlyThePlayer() {
        assertEquals("com.dlsc.animatorfx.sample", APPLICATION.getName());
        Set<String> required = APPLICATION.getDescriptor().requires().stream()
                .map(ModuleDescriptor.Requires::name).collect(Collectors.toSet());
        assertEquals(Set.of("java.base", "com.dlsc.animatorfx.player"), required);
    }

    @Test
    void everythingThePlayerBringsIsTheModelJavaFxOrTheJdk() {
        Set<String> closure = new TreeSet<>();
        Deque<String> pending = new ArrayDeque<>(List.of("com.dlsc.animatorfx.player"));
        while (!pending.isEmpty()) {
            String name = pending.pop();
            if (closure.add(name)) {
                ResolvedModule module = ModuleLayer.boot().configuration().findModule(name).orElseThrow();
                module.reference().descriptor().requires().forEach(requires -> pending.push(requires.name()));
            }
        }
        assertTrue(closure.contains("com.dlsc.animatorfx.model"));
        assertTrue(closure.contains("javafx.controls"));
        for (String name : closure) {
            assertTrue(name.equals("com.dlsc.animatorfx.player") || name.equals("com.dlsc.animatorfx.model")
                    || name.startsWith("javafx.") || name.startsWith("java.") || name.startsWith("jdk."),
                    "Unexpected module in the runtime closure: " + name + " of " + closure);
        }
        assertEquals(Set.of("java.base"), ModuleLayer.boot().findModule("com.dlsc.animatorfx.model").orElseThrow()
                .getDescriptor().requires().stream().map(ModuleDescriptor.Requires::name).collect(Collectors.toSet()));
    }

    @Test
    void noToolingModuleIsResolved() {
        for (Module module : ModuleLayer.boot().modules()) {
            for (String prefix : TOOLING) {
                assertFalse(module.getName().startsWith(prefix), "Tooling module on the module path: " + module);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com.dlsc.animatorfx.editor.AnimatorApp",
            "com.dlsc.animatorfx.editor.Launcher",
            "com.dlsc.animatorfx.importer.SvgImporter",
            "com.dlsc.animatorfx.importer.LottieImporter",
            "atlantafx.base.theme.PrimerDark",
            "org.kordamp.ikonli.javafx.FontIcon",
            "org.kordamp.ikonli.Ikon",
            "com.dlsc.gemsfx.DialogPane"})
    void toolingClassesCannotBeLoaded(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(className, false, ClassLoader.getSystemClassLoader()));
    }

    @Test
    void theClassPathCarriesNoToolingEither() {
        String classPath = System.getProperty("java.class.path", "") + System.getProperty("jdk.module.path", "");
        for (String artifact : List.of("animatorfx-editor", "animatorfx-import", "atlantafx", "ikonli", "gemsfx")) {
            assertFalse(classPath.contains(artifact), artifact + " in " + classPath);
        }
    }
}
