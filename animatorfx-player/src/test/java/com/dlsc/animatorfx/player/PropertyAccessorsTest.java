package com.dlsc.animatorfx.player;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Repository rule: the accessors of a JavaFX property are final. */
class PropertyAccessorsTest {

    private static List<String> nonFinalAccessors(Class<?> type) {
        List<String> properties = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().endsWith("Property") && method.getParameterCount() == 0
                    && Modifier.isPublic(method.getModifiers())) {
                String name = method.getName().substring(0, method.getName().length() - "Property".length());
                properties.add(name);
            }
        }
        assertFalse(properties.isEmpty(), type + " declares properties");

        List<String> offenders = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isFinal(method.getModifiers())) {
                continue;
            }
            for (String property : properties) {
                String capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
                String name = method.getName();
                if (name.equals(property + "Property") || name.equals("get" + capitalized)
                        || name.equals("is" + capitalized) || name.equals("set" + capitalized)) {
                    offenders.add(type.getSimpleName() + "." + name);
                }
            }
        }
        return offenders;
    }

    @Test
    void playerPropertyAccessorsAreFinal() {
        assertEquals(List.of(), nonFinalAccessors(AnimationPlayer.class));
    }

    @Test
    void viewPropertyAccessorsAreFinal() {
        assertEquals(List.of(), nonFinalAccessors(AnimationView.class));
    }
}
