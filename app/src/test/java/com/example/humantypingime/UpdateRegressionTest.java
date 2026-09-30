package com.example.humantypingime;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.junit.Assert.*;

public class UpdateRegressionTest {
    @Test public void completedFutureReturnsResult() throws Exception {
        FutureTask<String> future = new FutureTask<>(() -> "ready");
        future.run();
        assertEquals("ready", BoundedFutureWait.get(future, 1, TimeUnit.SECONDS));
        assertFalse(future.isCancelled());
    }

    @Test public void timeoutCancelsPendingFuture() throws Exception {
        FutureTask<String> future = new FutureTask<>(() -> "unused");
        try {
            BoundedFutureWait.get(future, 0, TimeUnit.SECONDS);
            fail("Expected timeout");
        } catch (TimeoutException expected) {
            assertTrue(future.isCancelled());
        }
    }

    @Test public void interruptionCancelsAndRestoresInterruptFlag() throws Exception {
        FutureTask<String> future = new FutureTask<>(() -> "unused");
        Thread.currentThread().interrupt();
        try {
            BoundedFutureWait.get(future, 1, TimeUnit.SECONDS);
            fail("Expected interruption");
        } catch (InterruptedException expected) {
            assertTrue(future.isCancelled());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test public void failedFuturePreservesUnderlyingError() throws Exception {
        IllegalStateException cause = new IllegalStateException("preparation failed");
        FutureTask<String> future = new FutureTask<>(() -> { throw cause; });
        future.run();
        try {
            BoundedFutureWait.get(future, 1, TimeUnit.SECONDS);
            fail("Expected execution error");
        } catch (ExecutionException expected) {
            assertSame(cause, expected.getCause());
        }
    }

    @Test public void blankTranscriptStaysBlank() {
        assertEquals("", TranscriptPostProcessor.process(null, null));
        assertEquals("", TranscriptPostProcessor.process("   ", null));
    }

    @Test public void fillersOnlyDoNotCreatePhantomPunctuation() {
        assertEquals("", TranscriptPostProcessor.process("um uh you know", null));
    }

    @Test public void repeatedWordsCollapse() {
        assertEquals("The draft is ready.", TranscriptPostProcessor.process(
                "the the the draft draft is ready", Collections.emptyList()));
    }

    @Test(timeout = 2000) public void longRepeatedTranscriptTerminates() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 10000; i++) text.append("hello ");
        assertEquals("Hello.", TranscriptPostProcessor.process(text.toString(), null));
    }

    @Test public void existingPunctuationSurvives() {
        assertEquals("Hello! Ready?", TranscriptPostProcessor.process("hello! ready?", null));
    }

    @Test public void templateIgnoresNullValues() {
        Template template = new Template("1", "Greeting", "greet", "Hi {name}, {topic}.");
        Map<String, String> values = new HashMap<>();
        values.put("name", "Scott");
        values.put("topic", null);
        assertEquals("Hi Scott, {topic}.", template.render(values));
    }

    @Test public void templateListsUniqueVariablesInOrder() {
        Template template = new Template("1", "Greeting", "greet", "{name}: {topic}, {name}");
        assertEquals(Arrays.asList("name", "topic"), template.getVariables());
    }

    @Test public void emptyTemplateHandlesNullInputs() {
        assertEquals("", new Template("1", "Empty", null, null).render(null));
        assertEquals("", Template.fromJson(null).getBody());
    }
}
