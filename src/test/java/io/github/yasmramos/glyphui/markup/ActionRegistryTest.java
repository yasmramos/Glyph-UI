package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.ui.Label;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests enum-keyed action registration and exact-name lookup.
 */
class ActionRegistryTest {

    private enum FileAction {
        SAVE,
        OPEN
    }

    private static final class FileActions implements Actions<FileAction> {
        private final List<String> calls;

        FileActions(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public FileAction[] ids() {
            return FileAction.values();
        }

        @Override
        public void register(ActionRegistrar<FileAction> registrar) {
            registrar.on(FileAction.SAVE, context -> calls.add("save:" + context.getSource()));
            registrar.on(FileAction.OPEN, () -> calls.add("open"));
        }
    }

    @Test
    void enumIdsAreRegisteredUnderTheirExactNames() {
        ActionRegistry registry = new ActionRegistry();
        registry.register(new FileActions(new ArrayList<>()));

        assertTrue(registry.knows("SAVE"));
        assertTrue(registry.knows("OPEN"));
        assertFalse(registry.knows("save"));
        assertFalse(registry.knows("Open"));
        assertEquals(java.util.Set.of("SAVE", "OPEN"), registry.names());
    }

    @Test
    void actionsReceiveTheInvocationContext() {
        ActionRegistry registry = new ActionRegistry();
        List<String> calls = new ArrayList<>();
        registry.register(new FileActions(calls));
        Bindings bindings = new Bindings();
        Label source = new Label("trigger");
        ActionContext context = new ActionContext(source, bindings);

        registry.get("SAVE").perform(context);

        assertEquals(List.of("save:" + source), calls);
        assertSame(bindings, context.getBindings());
        assertSame(source, context.getSource());
    }

    @Test
    void runnableOverloadRunsWithoutContext() {
        ActionRegistry registry = new ActionRegistry();
        List<String> calls = new ArrayList<>();
        registry.register(new FileActions(calls));

        registry.get("OPEN").perform(new ActionContext(null, new Bindings()));

        assertEquals(List.of("open"), calls);
    }

    @Test
    void unknownActionsReportTheExactName() {
        ActionRegistry registry = new ActionRegistry();
        registry.register(new FileActions(new ArrayList<>()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registry.get("DELETE"));

        assertTrue(error.getMessage().contains("DELETE"), error.getMessage());
        assertFalse(registry.knows("DELETE"));
        assertFalse(registry.knows(null));
    }

    @Test
    void registeringAStringActionWorksToo() {
        ActionRegistry registry = new ActionRegistry();
        List<String> calls = new ArrayList<>();
        registry.register("custom", context -> calls.add("custom"));

        registry.get("custom").perform(new ActionContext(null, new Bindings()));
        assertEquals(List.of("custom"), calls);
        assertThrows(IllegalArgumentException.class, () -> registry.register(" ", c -> { }));
        assertThrows(IllegalArgumentException.class, () -> registry.register("x", (Action) null));
    }

    @Test
    void everyDeclaredIdMustGetAHandler() {
        ActionRegistry registry = new ActionRegistry();
        Actions<FileAction> partial = new Actions<>() {
            @Override
            public FileAction[] ids() {
                return FileAction.values();
            }

            @Override
            public void register(ActionRegistrar<FileAction> registrar) {
                registrar.on(FileAction.SAVE, () -> { });
            }
        };

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registry.register(partial));

        assertTrue(error.getMessage().contains("OPEN"), error.getMessage());
        assertFalse(registry.knows("SAVE"));
        assertFalse(registry.knows("OPEN"));
    }

    @Test
    void undeclaredIdsAreRejected() {
        ActionRegistry registry = new ActionRegistry();
        Actions<FileAction> undeclared = new Actions<>() {
            @Override
            public FileAction[] ids() {
                return new FileAction[]{FileAction.SAVE};
            }

            @Override
            public void register(ActionRegistrar<FileAction> registrar) {
                registrar.on(FileAction.OPEN, () -> { });
            }
        };

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registry.register(undeclared));

        assertTrue(error.getMessage().contains("OPEN"), error.getMessage());
    }

    @Test
    void emptyOrNullGroupsAreRejected() {
        ActionRegistry registry = new ActionRegistry();
        Actions<FileAction> empty = new Actions<>() {
            @Override
            public FileAction[] ids() {
                return new FileAction[0];
            }

            @Override
            public void register(ActionRegistrar<FileAction> registrar) {
                // Nothing to bind.
            }
        };

        assertThrows(IllegalArgumentException.class, () -> registry.register((Actions<FileAction>) null));
        assertThrows(IllegalArgumentException.class, () -> registry.register(empty));
    }

    @Test
    void actionContextRequiresBindings() {
        assertThrows(IllegalArgumentException.class, () -> new ActionContext(null, null));
    }
}
