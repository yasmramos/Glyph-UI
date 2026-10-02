package com.glyphui.reactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DisposableScopeTest {

    @Test
    public void closesResourcesInRegistrationOrder() {
        DisposableScope scope = new DisposableScope();
        List<String> order = new ArrayList<>();
        scope.add(() -> order.add("a"));
        scope.add(() -> order.add("b"));
        scope.add(() -> order.add("c"));

        scope.dispose();

        assertEquals(List.of("a", "b", "c"), order);
        assertTrue(scope.isDisposed());
        assertEquals(0, scope.size());
    }

    @Test
    public void disposeIsIdempotent() {
        DisposableScope scope = new DisposableScope();
        int[] closed = {0};
        scope.add(() -> closed[0]++);

        scope.dispose();
        scope.dispose();
        scope.close();

        assertEquals(1, closed[0]);
    }

    @Test
    public void addReturnsTheResource() {
        DisposableScope scope = new DisposableScope();
        AutoCloseable resource = () -> { };
        assertSame(resource, scope.add(resource));
    }

    @Test
    public void aResourceAddedAfterDisposalIsClosedImmediately() {
        DisposableScope scope = new DisposableScope();
        scope.dispose();
        int[] closed = {0};

        scope.add(() -> closed[0]++);

        assertEquals(1, closed[0]);
        assertEquals(0, scope.size());
    }

    @Test
    public void aFailureDoesNotSkipTheRemainingResources() {
        DisposableScope scope = new DisposableScope();
        List<String> order = new ArrayList<>();
        scope.add(() -> order.add("first"));
        scope.add(() -> {
            throw new IllegalArgumentException("one");
        });
        scope.add(() -> order.add("third"));
        scope.add(() -> {
            throw new IllegalStateException("two");
        });

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, scope::dispose);

        assertEquals(List.of("first", "third"), order);
        assertEquals(1, thrown.getSuppressed().length);
        assertTrue(thrown.getSuppressed()[0] instanceof IllegalStateException);
    }

    @Test
    public void checkedExceptionsAreWrapped() {
        DisposableScope scope = new DisposableScope();
        scope.add(() -> {
            throw new Exception("checked");
        });

        IllegalStateException thrown = assertThrows(IllegalStateException.class, scope::dispose);

        assertEquals("checked", thrown.getCause().getMessage());
    }
}
