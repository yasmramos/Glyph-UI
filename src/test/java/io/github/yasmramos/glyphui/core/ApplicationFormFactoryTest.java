package io.github.yasmramos.glyphui.core;

import io.github.yasmramos.glyphui.SkijaTestSupport;
import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.ui.Form;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Application#createForm(String, int, int)}, the one-call
 * factory that creates a {@link Form} and initializes the application with it.
 *
 * <p>Creating the native window needs Skija + JWM natives, so these tests are
 * skipped (as an assumption) when the host cannot load them.</p>
 */
class ApplicationFormFactoryTest extends TestBase {

    @Test
    void createFormInitializesTheApplicationAndReturnsItsWindow() {
        Assumptions.assumeTrue(SkijaTestSupport.isAvailable(),
                "Skija native library required to initialize an Application");

        Application app = new Application();
        Form form;
        try {
            form = app.createForm("factory-test", 320, 240);
        } catch (IllegalStateException e) {
            app.close();
            Assumptions.abort("Application.createForm() unavailable on this host");
            return;
        }
        // TestBase closes the current application after the test.
        assertSame(form, app.getWindow(),
                "the created form must be the application's window");
        assertSame(app, Application.getCurrent(),
                "a successful createForm() publishes the application");
    }

    @Test
    void createFormOnAnAlreadyInitializedApplicationThrows() {
        Assumptions.assumeTrue(SkijaTestSupport.isAvailable(),
                "Skija native library required to initialize an Application");

        Application app = new Application();
        try {
            app.createForm("first", 320, 240);
        } catch (IllegalStateException e) {
            app.close();
            Assumptions.abort("Application.createForm() unavailable on this host");
            return;
        }
        assertThrows(IllegalStateException.class,
                () -> app.createForm("second", 320, 240),
                "a second createForm() must fail fast, like init()");
    }
}
