package io.github.rkaushikdev.doublerbay.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * Checks on what a type will accept, for the refusals the compiler now makes.
 *
 * <p>A length built from a fraction of a mil, a cycle count added to an hour count, a frozen value
 * reassigned: none of these compile, so a test of them asks the type what it accepts instead.
 */
public final class Types {

    private Types() {}

    /** Every public constructor of a type takes exactly these parameter types. */
    public static void constructorsTakeOnly(Class<?> type, Class<?>... parameters) {
        Constructor<?>[] constructors = type.getConstructors();
        assertThat(constructors)
                .as("%s has a public constructor", type.getSimpleName())
                .isNotEmpty();
        for (Constructor<?> constructor : constructors) {
            assertThat(constructor.getParameterTypes())
                    .as("%s takes %s", constructor, List.of(parameters))
                    .containsExactly(parameters);
        }
    }

    /** Every public method of a type with this name takes exactly one parameter of this type. */
    public static void methodTakesOnly(Class<?> type, String name, Class<?> parameter) {
        List<Method> named = Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(name))
                .toList();
        assertThat(named).as("%s has a method %s", type.getSimpleName(), name).isNotEmpty();
        for (Method method : named) {
            assertThat(method.getParameterTypes())
                    .as("%s takes a %s", method, parameter.getSimpleName())
                    .containsExactly(parameter);
        }
    }

    /** A value whose state can never be reassigned once it is built: every field is final. */
    public static void isFrozen(Class<?> type) {
        List<Field> fields = Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        assertThat(fields).as("%s holds some state", type.getSimpleName()).isNotEmpty();
        for (Field field : fields) {
            assertThat(Modifier.isFinal(field.getModifiers()))
                    .as("%s.%s is final", type.getSimpleName(), field.getName())
                    .isTrue();
        }
        boolean setters = Arrays.stream(type.getMethods())
                .anyMatch(method -> method.getName().startsWith("set") && method.getParameterCount() == 1);
        assertThat(setters).as("%s has no setters", type.getSimpleName()).isFalse();
    }
}
