package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.style.StyleProperty;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Form}, the {@code JFrame}-style top-level window: the
 * root-panel structure it mounts (title bar / menu bar / content pane), the
 * covariant fluent setters, and the behaviour of the individual chrome parts.
 *
 * <p>The mounting tests drive {@link Form#onAdopted(Panel)} directly with a
 * plain {@link Panel}, so they exercise the real assembly logic without
 * needing an {@code Application} or any native windowing library.</p>
 */
public class FormTest extends TestBase {

    @Test
    public void undecoratedFormEmbedsTheMenuBarInTheTitleBar() {
        Form form = new Form("App", 800, 600).decorated(false);
        form.getMenuBar().addMenu("File");
        Panel root = new Panel();
        form.onAdopted(root);

        List<Component> kids = root.getChildren();
        assertEquals(2, kids.size(), "title bar + content pane");
        assertInstanceOf(TitleBar.class, kids.get(0));
        assertSame(form.getContentPane(), kids.get(1));

        TitleBar bar = (TitleBar) kids.get(0);
        assertSame(form.getMenuBar(), bar.getMenuBar(),
                "the menu bar is embedded into the title bar's leading zone");
        assertTrue(bar.getMenuBar().isEmbedded(),
                "an embedded menu bar drops its own chrome");
    }

    @Test
    public void decoratedFormKeepsNativeChromeAndShowsNoTitleBar() {
        Form form = new Form("App", 800, 600);
        Panel root = new Panel();
        form.onAdopted(root);

        assertEquals(1, root.getChildren().size(),
                "a decorated form adds only the content pane");
        assertSame(form.getContentPane(), root.getChildren().get(0));
    }

    @Test
    public void decoratedFormWithExplicitMenuBarMountsMenuAndContentOnly() {
        Form form = new Form("App", 800, 600);
        form.setMenuBar(new MenuBar());
        Panel root = new Panel();
        form.onAdopted(root);

        List<Component> kids = root.getChildren();
        assertEquals(2, kids.size());
        assertInstanceOf(MenuBar.class, kids.get(0));
        assertFalse(((MenuBar) kids.get(0)).isEmbedded(),
                "a decorated form keeps the menu bar as its own row");
        assertSame(form.getContentPane(), kids.get(1));
    }

    @Test
    public void contentPaneGrowsToFillLeftoverSpace() {
        Form form = new Form("App", 800, 600);
        Panel root = new Panel();
        form.onAdopted(root);

        assertTrue(form.getContentPane().getInlineStyle()
                        .has(StyleProperty.FLEX_GROW),
                "content pane must absorb leftover height below the chrome");
        assertEquals(1.0f, form.getContentPane().getInlineStyle()
                .getFloat(StyleProperty.FLEX_GROW, 0.0f), 0.001f);
    }

    @Test
    public void userFlexGrowOnContentPaneIsPreserved() {
        Form form = new Form("App", 800, 600);
        form.getContentPane().setInlineStyle(
                io.github.yasmramos.glyphui.style.Style.builder()
                        .setFloat(StyleProperty.FLEX_GROW, 2.0f).build());
        Panel root = new Panel();
        form.onAdopted(root);

        assertEquals(2.0f, form.getContentPane().getInlineStyle()
                .getFloat(StyleProperty.FLEX_GROW, 0.0f), 0.001f);
    }

    @Test
    public void addDelegatesToTheContentPane() {
        Form form = new Form("App", 800, 600);
        Label label = new Label("Hi");
        assertSame(form, form.add(label));
        assertTrue(form.getContentPane().getChildren().contains(label));
    }

    @Test
    public void getMenuBarCreatesOneOnFirstUse() {
        Form form = new Form("App", 800, 600);
        MenuBar bar = form.getMenuBar();
        assertNotNull(bar);
        assertSame(bar, form.getMenuBar(), "created once and cached");
    }

    @Test
    public void addMenuCreatesTheBarAndAppends() {
        Form form = new Form("App", 800, 600);
        Menu file = form.addMenu("File");
        assertNotNull(file);
        assertEquals(1, form.getMenuBar().getMenus().size());
        assertSame(file, form.getMenuBar().getMenus().get(0));
    }

    @Test
    public void settingChromeAfterAdoptionRemountsImmediately() {
        Form form = new Form("App", 800, 600).decorated(false);
        Panel root = new Panel();
        form.onAdopted(root);
        assertEquals(2, root.getChildren().size(), "title bar + content");

        MenuBar bar = new MenuBar();
        form.setMenuBar(bar);
        assertEquals(2, root.getChildren().size(),
                "the menu bar is embedded, not a separate row");
        assertSame(bar, ((TitleBar) root.getChildren().get(0)).getMenuBar());

        form.setMenuBar(null);
        assertNull(((TitleBar) root.getChildren().get(0)).getMenuBar(),
                "menu bar removed again");
    }

    @Test
    public void mountedChildrenInheritTheRootContext() {
        io.github.yasmramos.glyphui.SkijaTestSupport.assumeAvailable();
        io.github.yasmramos.glyphui.core.Application app =
                new io.github.yasmramos.glyphui.core.Application();
        if (!io.github.yasmramos.glyphui.core.internal.TestHooks
                .initForTesting(app, new Form("ctx", 320, 240))) {
            app.close();
            org.junit.jupiter.api.Assumptions.abort(
                    "Application.init() unavailable on this host");
        }
        // TestBase closes the current application after the test.
        Form form = new Form("ctx", 320, 240).decorated(false);
        form.addMenu("File");
        form.onAdopted(app.getRootPanel());

        assertSame(app, form.getContentPane().context().app(),
                "content pane must inherit the owning application");
        assertSame(app, form.getTitleBar().context().app(),
                "title bar must inherit the owning application");
        assertSame(app, form.getTitleBar().getMenuBar().context().app(),
                "the embedded menu bar must inherit the owning application too");
    }

    @Test
    public void fluentSettersKeepTheFormType() {
        Form form = new Form("App", 800, 600);
        Form chained = form.title("T").size(640, 480).decorated(false)
                .resizable(false).centered(true);
        assertSame(form, chained, "each fluent setter returns the same Form");
    }

    @Test
    public void titleChangeKeepsTheCustomTitleBarInStep() {
        Form form = new Form("First", 800, 600).decorated(false);
        Panel root = new Panel();
        form.onAdopted(root);
        TitleBar bar = (TitleBar) root.getChildren().get(0);

        form.setTitle("Second");
        Label titleLabel = (Label) bar.getChildren().get(0);
        assertEquals("Second", titleLabel.getText(),
                "setTitle must propagate to the custom title bar");
    }

    @Test
    public void isDecoratedReflectsTheFluentHint() {
        Form undecorated = new Form("A", 100, 100).decorated(false);
        assertFalse(undecorated.isDecorated());
        Form decorated = new Form("B", 100, 100);
        assertTrue(decorated.isDecorated());
    }
}
