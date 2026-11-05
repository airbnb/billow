package com.airbnb.billow;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableSet;
import ognl.ClassResolver;
import ognl.MemberAccess;
import ognl.Node;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;

/**
 * Evaluates user-supplied OGNL expressions (the {@code q}/{@code query} and {@code s}/{@code sort}
 * request parameters) inside a sandboxed {@link OgnlContext}.
 *
 * <p>OGNL is a full expression language: with the default context an expression such as
 * {@code @java.lang.Runtime@getRuntime().exec("...")} executes arbitrary code on the server
 * (see https://github.com/airbnb/billow/issues/35). Billow only needs OGNL to read properties of
 * the cached AWS model objects and to compare them, so the context used here:
 *
 * <ul>
 *   <li>refuses to resolve any class name, which disables static field/method references
 *       ({@code @Class@member}), constructors ({@code new Class(...)}) and {@code instanceof};</li>
 *   <li>only permits access to public, non-static members declared on an allowlist of packages
 *       and classes (Billow's own model classes, the AWS SDK model classes, collections,
 *       Joda-Time and a small set of {@code java.lang} value types);</li>
 *   <li>refuses reflective escape hatches such as {@code getClass()}, mutable model methods,
 *       collection mutators, assignment and dynamic evaluation so an expression cannot alter
 *       the shared cache.</li>
 * </ul>
 */
final class QueryExpression {
    private QueryExpression() {
    }

    /** Packages whose classes' members may be accessed. Exact match, no sub-packages. */
    private static final ImmutableSet<String> ALLOWED_PACKAGES = ImmutableSet.of(
        "com.airbnb.billow",
        "java.util",
        "com.google.common.collect",
        "org.joda.time"
    );

    /** Package patterns whose classes' members may be accessed. */
    private static final Pattern ALLOWED_PACKAGE_PATTERN =
        Pattern.compile("com\\.amazonaws\\.services\\.[a-z0-9]+\\.model");

    /** {@code java.lang} classes whose members may be accessed; everything else in java.lang is refused. */
    private static final ImmutableSet<Class<?>> ALLOWED_JAVA_LANG_CLASSES = ImmutableSet.<Class<?>>of(
        Object.class,
        String.class,
        CharSequence.class,
        Comparable.class,
        Iterable.class,
        Boolean.class,
        Character.class,
        Number.class,
        Byte.class,
        Short.class,
        Integer.class,
        Long.class,
        Float.class,
        Double.class,
        Enum.class,
        Math.class
    );

    /**
     * Members that are never accessible, even on allowed classes: reflective escape hatches, and
     * collection mutators so an expression cannot alter the shared cache.
     */
    private static final ImmutableSet<String> DENIED_MEMBER_NAMES = ImmutableSet.of(
        "getClass", "forName", "wait", "notify", "notifyAll", "clone", "finalize",
        "add", "addAll", "put", "putAll", "putIfAbsent", "remove", "removeAll", "removeIf", "retainAll",
        "clear", "replace", "replaceAll", "compute", "computeIfAbsent", "computeIfPresent", "merge", "sort"
    );

    private static final ImmutableSet<String> DENIED_NODE_TYPES = ImmutableSet.of(
        "ognl.ASTAssign", "ognl.ASTEval"
    );

    /**
     * Parses and evaluates {@code expression} against {@code root} in the sandboxed context.
     */
    static Object evaluate(Object compiledExpression, Object root) throws OgnlException {
        return Ognl.getValue(compiledExpression, newContext(root), root);
    }

    static Object parse(String expression) throws OgnlException {
        final Node node = (Node) Ognl.parseExpression(expression);
        rejectWritesAndDynamicEvaluation(node);
        return node;
    }

    private static void rejectWritesAndDynamicEvaluation(Node node) throws OgnlException {
        // ASTEval parses a new expression at runtime, bypassing validation of the original tree.
        if (DENIED_NODE_TYPES.contains(node.getClass().getName()))
            throw new OgnlException("assignment and dynamic evaluation are not permitted in query expressions");
        for (int i = 0; i < node.jjtGetNumChildren(); i++)
            rejectWritesAndDynamicEvaluation(node.jjtGetChild(i));
    }

    static OgnlContext newContext(Object root) {
        final Map context = Ognl.createDefaultContext(root, DenyAllClassResolver.INSTANCE, null,
            RestrictedMemberAccess.INSTANCE);
        return (OgnlContext) context;
    }

    /** Refuses to resolve any class, so expressions cannot reference static members or constructors. */
    static final class DenyAllClassResolver implements ClassResolver {
        static final DenyAllClassResolver INSTANCE = new DenyAllClassResolver();

        @Override
        public Class classForName(String className, Map context) throws ClassNotFoundException {
            throw new ClassNotFoundException("class references are not permitted in query expressions: " + className);
        }
    }

    /** Permits only public instance members on allowlisted classes, and never setters. */
    static final class RestrictedMemberAccess implements MemberAccess {
        static final RestrictedMemberAccess INSTANCE = new RestrictedMemberAccess();

        @Override
        public Object setup(Map context, Object target, Member member, String propertyName) {
            return null;
        }

        @Override
        public void restore(Map context, Object target, Member member, String propertyName, Object state) {
        }

        @Override
        public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
            if (member == null)
                return false;

            final int modifiers = member.getModifiers();
            if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers))
                return false;

            final String name = member.getName();
            if (DENIED_MEMBER_NAMES.contains(name))
                return false;
            if (member instanceof Method && name.startsWith("set"))
                return false;

            final Class<?> declaringClass = member.getDeclaringClass();
            if (!isAllowedClass(declaringClass))
                return false;
            if (member instanceof Method && isModelClass(declaringClass)) {
                final Method method = (Method) member;
                return ((name.startsWith("get") || name.startsWith("is"))
                    && method.getParameterTypes().length == 0)
                    || name.equals("equals") || name.equals("hashCode") || name.equals("toString");
            }
            return true;
        }

        private static boolean isModelClass(Class<?> clazz) {
            final Package pkg = clazz.getPackage();
            if (pkg == null)
                return false;
            final String name = pkg.getName();
            return "com.airbnb.billow".equals(name) || ALLOWED_PACKAGE_PATTERN.matcher(name).matches();
        }

        static boolean isAllowedClass(Class<?> clazz) {
            if (clazz == null)
                return false;
            if (clazz.isArray())
                return isAllowedClass(clazz.getComponentType());
            if (clazz.isPrimitive())
                return true;

            final Package pkg = clazz.getPackage();
            final String packageName = pkg == null ? "" : pkg.getName();

            if ("java.lang".equals(packageName))
                return ALLOWED_JAVA_LANG_CLASSES.contains(clazz);
            if (clazz == org.joda.time.base.BaseDateTime.class
                || clazz == org.joda.time.base.AbstractDateTime.class
                || clazz == org.joda.time.base.AbstractInstant.class)
                return true;
            if (ALLOWED_PACKAGES.contains(packageName))
                return true;
            return ALLOWED_PACKAGE_PATTERN.matcher(packageName).matches();
        }
    }
}
