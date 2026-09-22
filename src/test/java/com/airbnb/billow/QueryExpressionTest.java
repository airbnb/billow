package com.airbnb.billow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.internal.SdkInternalList;
import com.amazonaws.services.rds.model.Endpoint;
import ognl.OgnlException;
import org.junit.Test;

public class QueryExpressionTest {
    private static final Map<String, String> TAGS = new HashMap<>();

    static {
        TAGS.put("env", "prod");
        TAGS.put("role", "worker");
    }

    private static final SQSQueue QUEUE = new SQSQueue(
        "https://sqs.us-east-1.amazonaws.com/123456789012/my-queue",
        1L, 2L, 3L, 4L, 5L, 6L, 7L, 42L, 8L,
        "arn:aws:sqs:us-east-1:123456789012:my-queue",
        TAGS);

    private static Object eval(String expression, Object root) throws OgnlException {
        return QueryExpression.evaluate(QueryExpression.parse(expression), root);
    }

    private static void assertRejected(String expression, Object root) {
        try {
            final Object value = eval(expression, root);
            fail("expected expression to be rejected but it evaluated to: " + value);
        } catch (OgnlException expected) {
            // ok
        }
    }

    // --- Documented query shapes keep working ---

    @Test
    public void allowsPropertyComparison() throws OgnlException {
        assertEquals(true, eval("approximateNumberOfMessages > 10", QUEUE));
        assertEquals(false, eval("approximateNumberOfMessages > 100", QUEUE));
        assertEquals(true, eval("approximateNumberOfMessages == 42 && delaySeconds == 4", QUEUE));
    }

    @Test
    public void allowsStringMethodsAndMapLookups() throws OgnlException {
        assertEquals(true, eval("url.startsWith(\"https://sqs.us-east-1\")", QUEUE));
        assertEquals(true, eval("url.contains(\"my-queue\")", QUEUE));
        assertEquals(true, eval("tags['env'] == \"prod\"", QUEUE));
        assertEquals(true, eval("tags.get(\"role\") == \"worker\"", QUEUE));
        assertEquals(2, eval("tags.size()", QUEUE));
        assertEquals(true, eval("tags.containsKey(\"env\")", QUEUE));
    }

    @Test
    public void allowsSortExpressions() throws OgnlException {
        assertEquals(42L, eval("approximateNumberOfMessages", QUEUE));
        assertEquals(QUEUE.getUrl(), eval("url", QUEUE));
    }

    @Test
    public void allowsLiteralsAndArithmetic() throws OgnlException {
        assertEquals(true, eval("1 + 1 == 2", QUEUE));
        assertEquals(true, eval("approximateNumberOfMessages * 2 == 84", QUEUE));
    }

    @Test
    public void allowsAwsSdkModelClasses() throws OgnlException {
        final Endpoint endpoint = new Endpoint().withAddress("db.example.internal").withPort(5432);
        assertEquals(true, eval("address == \"db.example.internal\" && port == 5432", endpoint));
    }

    @Test
    public void allowsAwsSdkCollectionSubclasses() throws OgnlException {
        // The AWS SDK returns lists as com.amazonaws.internal.SdkInternalList; the members used
        // (size, get, isEmpty) are declared on java.util.ArrayList and so remain accessible.
        final SdkInternalList<String> list = new SdkInternalList<>();
        list.add("a");
        assertEquals(1, eval("size()", list));
        assertEquals("a", eval("get(0)", list));
        assertEquals(false, eval("isEmpty()", list));
    }

    // --- Remote code execution vectors are rejected (airbnb/billow#35) ---

    @Test
    public void rejectsStaticMethodCalls() {
        assertRejected("@java.lang.Runtime@getRuntime()", QUEUE);
        assertRejected("@java.lang.Runtime@getRuntime().exec(\"id\")", QUEUE);
        assertRejected("@java.lang.System@getProperty(\"java.version\")", QUEUE);
        assertRejected("@java.lang.Class@forName(\"java.lang.Runtime\")", QUEUE);
    }

    @Test
    public void rejectsStaticFieldAccess() {
        assertRejected("@java.lang.System@out", QUEUE);
        assertRejected("@java.io.File@separator", QUEUE);
    }

    @Test
    public void rejectsConstructors() {
        assertRejected("new java.lang.ProcessBuilder(\"id\").start()", QUEUE);
        assertRejected("new java.io.File(\"/etc/passwd\")", QUEUE);
        assertRejected("new java.lang.String(\"harmless\")", QUEUE);
    }

    @Test
    public void rejectsReflectionViaGetClass() {
        assertRejected("getClass()", QUEUE);
        assertRejected("getClass().forName(\"java.lang.Runtime\")", QUEUE);
        assertRejected("url.getClass()", QUEUE);
        assertRejected("url.class", QUEUE);
        assertRejected("tags.class.classLoader", QUEUE);
    }

    @Test
    public void rejectsEscapingThroughTheOgnlContext() {
        assertRejected("#context.setMemberAccess(null)", QUEUE);
        assertRejected("#context.getMemberAccess()", QUEUE);
        assertRejected("#context.setClassResolver(null)", QUEUE);
        assertRejected("#context['_memberAccess'] = null", QUEUE);
        assertRejected("#context['_classResolver'] = null", QUEUE);
        assertRejected("#context._classResolver.classForName(\"java.lang.Runtime\", #context)", QUEUE);
    }

    @Test
    public void rejectsNonModelClasses() {
        assertRejected("url.bytes.class", QUEUE);
        assertRejected("@java.lang.Thread@currentThread()", QUEUE);
        assertRejected("@ognl.OgnlRuntime@getMethodValue(#context, #root, \"url\")", QUEUE);
    }

    // --- Expressions cannot mutate the shared cache ---

    @Test
    public void rejectsSettersOnModelObjects() {
        final Endpoint endpoint = new Endpoint().withAddress("original").withPort(5432);
        assertRejected("setAddress(\"evil\")", endpoint);
        assertRejected("address = \"evil\"", endpoint);
        assertEquals("original", endpoint.getAddress());
    }

    @Test
    public void rejectsCollectionMutators() {
        assertRejected("tags.put(\"env\", \"evil\")", QUEUE);
        assertRejected("tags.remove(\"env\")", QUEUE);
        assertRejected("tags.clear()", QUEUE);
        assertRejected("tags['env'] = \"evil\"", QUEUE);
        assertRejected("tags.env = \"evil\"", QUEUE);
        assertEquals("prod", TAGS.get("env"));

        final SdkInternalList<String> list = new SdkInternalList<>();
        list.add("a");
        assertRejected("[0] = \"evil\"", list);
        assertRejected("add(\"evil\")", list);
        assertRejected("set(0, \"evil\")", list);
        assertRejected("iterator().remove()", list);
        assertEquals("a", list.get(0));
    }

    @Test
    public void allowedClassRuleIsAnAllowlist() {
        assertTrue(QueryExpression.RestrictedMemberAccess.isAllowedClass(SQSQueue.class));
        assertTrue(QueryExpression.RestrictedMemberAccess.isAllowedClass(Endpoint.class));
        assertTrue(QueryExpression.RestrictedMemberAccess.isAllowedClass(String.class));
        assertTrue(QueryExpression.RestrictedMemberAccess.isAllowedClass(HashMap.class));
        assertTrue(QueryExpression.RestrictedMemberAccess.isAllowedClass(org.joda.time.DateTime.class));

        assertFalse(QueryExpression.RestrictedMemberAccess.isAllowedClass(Runtime.class));
        assertFalse(QueryExpression.RestrictedMemberAccess.isAllowedClass(System.class));
        assertFalse(QueryExpression.RestrictedMemberAccess.isAllowedClass(Class.class));
        assertFalse(QueryExpression.RestrictedMemberAccess.isAllowedClass(ClassLoader.class));
        assertFalse(QueryExpression.RestrictedMemberAccess.isAllowedClass(Thread.class));
        assertFalse(QueryExpression.RestrictedMemberAccess.isAllowedClass(java.io.File.class));
        assertFalse(QueryExpression.RestrictedMemberAccess.isAllowedClass(java.lang.reflect.Method.class));
        assertFalse(QueryExpression.RestrictedMemberAccess.isAllowedClass(ognl.OgnlContext.class));
        assertFalse(QueryExpression.RestrictedMemberAccess.isAllowedClass(
            com.amazonaws.services.sqs.AmazonSQSClient.class));
    }
}
