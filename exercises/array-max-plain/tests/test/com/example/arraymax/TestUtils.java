package com.example.arraymax;

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reflection helpers, so the tests compile even when the student's class or method is missing
 * and fail with a readable message instead of a compile error.
 */
final class TestUtils {

    private TestUtils() {
    }

    static Class<?> findClass(String qualifiedName) {
        try {
            return Class.forName(qualifiedName);
        } catch (ClassNotFoundException e) {
            return fail("The class " + qualifiedName + " was not found. Check its name and package.");
        }
    }

    static Method findStaticMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
                return fail("The method " + name + " in " + owner.getSimpleName() + " must be public and static.");
            }
            return method;
        } catch (NoSuchMethodException e) {
            return fail("The method " + name + " was not found in " + owner.getSimpleName() + ".");
        }
    }

    /**
     * Invokes a static method. An exception thrown by the student's code is rethrown unwrapped,
     * so tests can use assertThrows on it directly.
     */
    static Object invokeStatic(Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            return fail("The method " + method.getName() + " threw a checked exception.", cause);
        } catch (IllegalAccessException e) {
            return fail("The method " + method.getName() + " could not be called.", e);
        }
    }
}
