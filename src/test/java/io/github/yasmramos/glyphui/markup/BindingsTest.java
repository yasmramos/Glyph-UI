package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.reactive.Observer;
import io.github.yasmramos.glyphui.reactive.Trackable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests global binding paths and the scoped fallback used by loops.
 */
class BindingsTest {

    private static final class Constant<T> implements Trackable<T> {
        private final T value;

        Constant(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public Subscription subscribe(Observer observer) {
            return () -> {
                // Constant values never change.
            };
        }
    }

    @Test
    void registeredPathsResolveDirectly() {
        Bindings bindings = new Bindings();
        Constant<String> title = new Constant<>("Inbox");

        bindings.register("viewModel.title", title);

        assertTrue(bindings.knows("viewModel.title"));
        assertSame(title, bindings.resolve("viewModel.title"));
        assertEquals("Inbox", bindings.resolve("viewModel.title").get());
    }

    @Test
    void scopedChildrenSeeParentBindings() {
        Bindings root = new Bindings();
        Constant<String> title = new Constant<>("Inbox");
        root.register("title", title);

        Bindings child = root.scoped();

        assertTrue(child.knows("title"));
        assertSame(title, child.resolve("title"));
    }

    @Test
    void scopedLocalsShadowTheParent() {
        Bindings root = new Bindings();
        Constant<String> global = new Constant<>("global");
        root.register("item", global);

        Bindings child = root.scoped();
        Constant<String> local = new Constant<>("local");
        child.register("item", local);
        child.register("index", new Constant<>(3));

        assertSame(local, child.resolve("item"));
        assertSame(global, root.resolve("item"));
        assertEquals(3, child.resolve("index").get());
        assertFalse(root.knows("index"));
    }

    @Test
    void fallbackWalksNestedScopes() {
        Bindings root = new Bindings();
        Constant<String> shared = new Constant<>("shared");
        root.register("shared", shared);

        Bindings middle = root.scoped();
        middle.register("middleOnly", new Constant<>("m"));
        Bindings inner = middle.scoped();
        inner.register("innerOnly", new Constant<>("i"));

        assertSame(shared, inner.resolve("shared"));
        assertEquals("m", inner.resolve("middleOnly").get());
        assertEquals("i", inner.resolve("innerOnly").get());
        assertFalse(middle.knows("innerOnly"));
    }

    @Test
    void unknownPathsReportThePath() {
        Bindings bindings = new Bindings();
        bindings.register("known", new Constant<>("value"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> bindings.resolve("missing.path"));

        assertTrue(error.getMessage().contains("missing.path"), error.getMessage());
        assertFalse(bindings.knows("missing.path"));
        assertFalse(bindings.knows(null));
    }

    @Test
    void invalidRegistrationsAreRejected() {
        Bindings bindings = new Bindings();

        assertThrows(IllegalArgumentException.class,
                () -> bindings.register(null, new Constant<>("value")));
        assertThrows(IllegalArgumentException.class,
                () -> bindings.register(" ", new Constant<>("value")));
        assertThrows(IllegalArgumentException.class, () -> bindings.register("path", null));
    }
}
