package com.example.arraymax;

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Reflection helpers, so the tests compile even when the student's class or method is missing.
 * A missing class or method then fails one test with a readable message instead of breaking the build.
 */
final class TestUtils {

    static final String BASE_PACKAGE = "com.example.arraymax.";

    private TestUtils() {
    }

    // ---------- finding ----------

    static Class<?> loadClass(String simpleName) {
        try {
            return Class.forName(BASE_PACKAGE + simpleName);
        } catch (ClassNotFoundException e) {
            return fail("The class '" + simpleName + "' was not found in package '"
                    + BASE_PACKAGE.substring(0, BASE_PACKAGE.length() - 1) + "'. Check its name and package.");
        }
    }

    static Method findPublicStatic(String className, String methodName, Object... args) {
        Class<?> clazz = loadClass(className);
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())
                    && accepts(method.getParameterTypes(), args)) {
                return method;
            }
        }
        return fail("'" + className + "' has no public static method " + methodName + "(" + describe(args)
                + "). Check the name, the parameter types and the modifiers.");
    }

    // ---------- invoking ----------

    /** Calls a public static method. An exception thrown by the student's code fails the test with its message. */
    static Object invokeStatic(String className, String methodName, Object... args) {
        Method method = findPublicStatic(className, methodName, args);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            return fail(className + "." + methodName + "() threw an exception: " + e.getCause());
        } catch (IllegalAccessException e) {
            return fail(className + "." + methodName + "() could not be called: " + e);
        }
    }

    /** Calls a public static method that is expected to throw, and returns what it threw. Fails if nothing is thrown. */
    static Throwable thrownBy(String className, String methodName, Object... args) {
        Method method = findPublicStatic(className, methodName, args);
        try {
            Object result = method.invoke(null, args);
            return fail(className + "." + methodName + "(" + describe(args) + ") should throw an exception, but returned "
                    + result + ".");
        } catch (InvocationTargetException e) {
            return e.getCause();
        } catch (IllegalAccessException e) {
            return fail(className + "." + methodName + "() could not be called: " + e);
        }
    }

    // ---------- checking ----------

    static void expectEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            fail(what + "\nExpected: " + expected + "\nBut was:  " + actual);
        }
    }

    // ---------- internals ----------

    private static boolean accepts(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            Class<?> type = parameterTypes[i].isPrimitive() ? boxed(parameterTypes[i]) : parameterTypes[i];
            if (args[i] == null ? parameterTypes[i].isPrimitive() : !type.isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxed(Class<?> primitive) {
        if (primitive == int.class) {
            return Integer.class;
        }
        if (primitive == boolean.class) {
            return Boolean.class;
        }
        if (primitive == long.class) {
            return Long.class;
        }
        if (primitive == double.class) {
            return Double.class;
        }
        return primitive;
    }

    private static String describe(Object[] args) {
        return Arrays.stream(args)
                .map(arg -> arg instanceof int[] array ? Arrays.toString(array) : String.valueOf(arg))
                .collect(Collectors.joining(", "));
    }
}
