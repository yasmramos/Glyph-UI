package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;

import java.util.Objects;

/**
 * Single-line editable text field.
 *
 * <p><b>IME notes:</b> text input flows through the backend char/key
 * listeners and clipboard access goes through the backend-neutral
 * {@code Window.setClipboardString}/{@code getClipboardString} API. The JWM
 * backend additionally exposes a native IME bridge, so real input-method composition windows
 * and candidate lists (CJK input, dead-key composition on some platforms)
 * are NOT supported — characters arrive already committed. This is documented
 * in the README.</p>
 *
 * <p>Supported editing: caret movement (arrows/Home/End, with Shift for
 * selection), Backspace/Delete, Ctrl+C/X/V via the window clipboard hooks
 * installed by {@code Application}, Ctrl+A select-all, plain text insertion
 * through {@link #insertText(String)} (also fed by the char callback).</p>
 */
public class TextField extends Component
        implements io.github.yasmramos.glyphui.core.internal.ImeTextFieldBridge {

    @Override
    protected String defaultStyleTag() {
        return "input";
    }

    /** Toolkit key codes (GlyphKeys wire values) used inline below. */
    private static final int KEY_BACKSPACE = 259; // GlyphKeys.BACKSPACE
    private static final int KEY_DELETE = 261;    // GlyphKeys.DELETE
    private static final int KEY_RIGHT = 262;     // GlyphKeys.RIGHT
    private static final int KEY_LEFT = 263;      // GlyphKeys.LEFT
    private static final int KEY_DOWN = 264;      // GlyphKeys.DOWN
    private static final int KEY_UP = 265;        // GlyphKeys.UP
    private static final int KEY_HOME = 268;      // GlyphKeys.HOME
    private static final int KEY_END = 269;       // GlyphKeys.END
    private static final int KEY_ENTER = 257;     // GlyphKeys.ENTER

    /** Clipboard bridge installed by {@code Application} at startup. */
    private static Runnable clipboardCopyHandler;   // copies this field's selection to the system clipboard
    private static Runnable clipboardPasteHandler;  // reads the system clipboard and inserts it here

    private String text = "";

    /** Observable text property (lazily created, marshals to the UI thread). */
    private Property<String> textProperty;

    private String placeholder = "";

    /** Caret position (character index, 0..text.length()). */
    private int caret = 0;

    /** Anchor of the selection (caret..selectionAnchor is the selection). */
    private int selectionAnchor = 0;

    /** True between a press that began a selection and its end (release/hover). */
    private boolean selecting = false;

    private Paint backgroundPaint;
    private Paint borderPaint;
    private Paint textPaint;
    private Paint cursorPaint;
    private Paint selectionPaint;

    /**
     * Creates a text field.
     *
     * @param x      initial x position
     * @param y      initial y position
     * @param width  initial width
     * @param height initial height
     */
    public TextField(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    /**
     * Creates a zero-sized text field to be positioned by a layout manager.
     */
    public TextField() {
        super();
    }

    /**
     * Lazily creates the five reusable paints on first render. Construction
     * stays allocation-free (matching Button/Label/Panel/ImageView): tests
     * that build a TextField without an initialised Skija backend never
     * touch native paint objects.
     */
    private void ensurePaints() {
        if (backgroundPaint == null || backgroundPaint.isClosed()) {
            backgroundPaint = new Paint();
            backgroundPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setStroke(true);
            borderPaint.setStrokeWidth(1.0f);
            borderPaint.setAntiAlias(true);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
        if (cursorPaint == null || cursorPaint.isClosed()) {
            cursorPaint = new Paint();
            cursorPaint.setAntiAlias(true);
        }
        if (selectionPaint == null || selectionPaint.isClosed()) {
            selectionPaint = new Paint();
            selectionPaint.setAntiAlias(true);
        }
    }

    // ------------------------------------------------------------------
    // Clipboard bridge (installed by Application)
    // ------------------------------------------------------------------

    /**
     * Installs the process-wide clipboard hooks used by Ctrl+C/X/V. The
     * copy hook is expected to call {@link #getSelectedText()} on the
     * focused field and push it through {@code Window.setClipboardString};
     * the paste hook reads {@code Window.getClipboardString} and feeds it to
     * {@link #insertText(String)} on the focused field.
     *
     * @param copyHandler  runnable that copies the focused field's selection
     *                     to the system clipboard
     * @param pasteHandler runnable that pastes the system clipboard here
     */
    public static void setClipboardHooks(Runnable copyHandler, Runnable pasteHandler) {
        clipboardCopyHandler = copyHandler;
        clipboardPasteHandler = pasteHandler;
    }

    // ------------------------------------------------------------------
    // Content
    // ------------------------------------------------------------------

    /**
     * Gets the current text.
     *
     * @return the text (never null)
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the observable text property. Setting it from a background
     * thread marshals the change onto the UI thread automatically, matching
     * the threading contract of Button/Label (previously {@code setText}
     * mutated the buffer directly on the caller's thread).
     *
     * @return the text property (never null after first access)
     */
    public Property<String> textProperty() {
        if (textProperty == null) {
            // Application is passed as null on purpose: Property resolves
            // Application.getCurrent() dynamically at set time (Block 3.7),
            // so widgets constructed before the application exists still
            // marshal correctly once it starts.
            textProperty = new Property<>(null, this.text, newText -> {
                // Whole-text replacement: caret goes to the end, selection
                // cleared; notify so listeners see programmatic changes too.
                setTextInternal(newText, newText == null ? 0 : newText.length(), true);
            });
        }
        return textProperty;
    }

    /**
     * Replaces the whole text. Delegates to {@link #textProperty()} so the
     * change is marshalled onto the UI thread when invoked from a background
     * thread. Fires {@code onTextChanged} when the content actually changed.
     *
     * @param text the new text (null becomes "")
     */
    public void setText(String text) {
        textProperty().set(text);
    }

    /**
     * Replaces the whole text. When {@code notify} is true and the content
     * actually changed, the {@code onTextChanged} handler fires (used by the
     * markup loader's {@code onchange} attribute). Programmatic initialisation
     * (e.g. loading a value from markup) should pass {@code false}.
     *
     * <p>Note: this overload applies the change immediately on the calling
     * thread; cross-thread callers should use {@link #setText(String)} or
     * {@link #textProperty()} instead.</p>
     *
     * @param text   the new text (null becomes "")
     * @param notify whether to fire the change handler
     */
    public void setText(String text, boolean notify) {
        // Whole-text replacement: caret goes to the end, selection cleared.
        setTextInternal(text, text == null ? 0 : text.length(), notify);
    }

    /**
     * Single funnel for every text mutation (programmatic {@code setText},
     * key-driven editing, IME commits, selection deletes). Updates the
     * buffer, clamps the caret, clears the selection, invalidates the widget
     * and — when {@code notify} is true and the content actually changed —
     * fires {@link #onTextChanged}. Centralising this here guarantees the
     * change handler fires from ALL editing paths, not only
     * {@code setText(text, true)}.
     *
     * @param newText  the new buffer content (null becomes "")
     * @param newCaret caret position after the change (clamped)
     * @param notify   whether to fire the change handler
     */
    private void setTextInternal(String newText, int newCaret, boolean notify) {
        String old = this.text;
        this.text = (newText == null) ? "" : newText;
        this.caret = Math.max(0, Math.min(newCaret, this.text.length()));
        this.selectionAnchor = this.caret;
        // Buffer content drives the preferred width: relayout upward.
        invalidateLayout();
        if (notify && !old.equals(this.text) && onTextChanged != null) {
            onTextChanged.run();
        }
    }

    /** Per-field change listener fired when the text content changes. */
    private Runnable onTextChanged;

    /**
     * Registers a handler invoked whenever the field's text changes through
     * user editing or {@link #setText(String)}.
     *
     * @param onTextChanged the handler (may be null to clear)
     */
    public void setOnTextChanged(Runnable onTextChanged) {
        this.onTextChanged = onTextChanged;
    }

    /**
     * Gets the registered change handler.
     *
     * @return the handler or null
     */
    public Runnable getOnTextChanged() {
        return onTextChanged;
    }

    /**
     * Gets the placeholder shown when the field is empty and unfocused.
     *
     * @return the placeholder text
     */
    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * Sets the placeholder text.
     *
     * @param placeholder the placeholder (may be null/"")
     */
    public void setPlaceholder(String placeholder) {
        String updated = (placeholder == null) ? "" : placeholder;
        if (this.placeholder.equals(updated)) {
            return;
        }
        this.placeholder = updated;
        // Placeholder text participates in measurement too.
        invalidateLayout();
    }

    /** @return the caret index (0..length) */
    public int getCaret() {
        return caret;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation of the internal IME bridge; not part of the frozen
     * application-facing widget API.</p>
     */
    @Override
    public Font getFontForIme() {
        return getFont();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link #replaceRange(int, int, String, int)}. Not part
     * of the frozen application-facing widget API.</p>
     */
    @Override
    public void imeReplaceRange(int lo, int hi, String newText, int newCaret) {
        replaceRange(lo, hi, newText, newCaret);
    }

    /**
     * Replaces the current selection with {@code newText}, placing the caret
     * at {@code newCaret}. Used by the IME commit path (JWM backend), whose
     * replacement ranges are absolute buffer offsets rather than "insert at
     * caret".
     *
     * @param lo       start of the range to replace (clamped)
     * @param hi       end of the range to replace (clamped, >= lo)
     * @param newText  the committed text
     * @param newCaret caret position after the replacement (clamped)
     */
    void replaceRange(int lo, int hi, String newText, int newCaret) {
        if (!isEnabled()) {
            return;
        }
        int length = text.length();
        int from = Math.max(0, Math.min(lo, length));
        int to = Math.max(from, Math.min(hi, length));
        String inserted = newText == null ? "" : newText;
        // Route through the shared mutation funnel so onTextChanged fires
        // for IME replacements too (previously this path was silent).
        setTextInternal(text.substring(0, from) + inserted + text.substring(to),
                newCaret, true);
    }

    /** @return true if a non-empty selection exists */
    public boolean hasSelection() {
        return caret != selectionAnchor;
    }

    /** @return the selected substring (empty when no selection) */
    public String getSelectedText() {
        int lo = Math.min(caret, selectionAnchor);
        int hi = Math.max(caret, selectionAnchor);
        return text.substring(lo, hi);
    }

    /**
     * Gets the selected text, or null when there is no selection. Used by
     * the clipboard copy hook installed by {@code Application}.
     *
     * @return the selected substring, or null if nothing is selected
     */
    public String getSelectedTextOrNull() {
        return hasSelection() ? getSelectedText() : null;
    }

    /**
     * Selects the entire content.
     */
    public void selectAll() {
        selectionAnchor = 0;
        caret = text.length();
        invalidate();
    }

    /**
     * Clears any selection (keeps the caret where it is).
     */
    public void clearSelection() {
        selectionAnchor = caret;
        invalidate();
    }

    /**
     * Inserts text at the caret, replacing the current selection first.
     * This is the entry point used by the backend char callback.
     *
     * @param chars the text to insert (ignored when null/empty or disabled)
     */
    public void insertText(String chars) {
        if (!isEnabled() || chars == null || chars.isEmpty()) {
            return;
        }
        // Drop any pending selection first (counted into the same final
        // mutation below), then insert at the caret. Routing through
        // setTextInternal makes onTextChanged fire for typed characters —
        // previously this path mutated the buffer silently.
        int lo = Math.min(caret, selectionAnchor);
        int hi = Math.max(caret, selectionAnchor);
        String updated = text.substring(0, lo) + chars + text.substring(hi);
        setTextInternal(updated, lo + chars.length(), true);
    }

    /**
     * Applies a committed input-method (IME) text event, honouring the
     * backend's replacement range. Windows reports the replacement range as
     * {@code (-1, -1)} — a sentinel meaning "replace at the caret" — so it must
     * be read as "insert here", not clamped to position 0. Treating it as 0
     * (the previous behaviour) inserted every typed character at the front of
     * the buffer, turning "hola" into "aloh".
     *
     * <p>An explicit, non-empty range (a marked/composition region being
     * committed, e.g. on macOS) replaces that exact region instead. An empty
     * range ({@code start == end}) carries no information, so it is also read
     * as "at the caret".</p>
     *
     * @param committed        the committed text (ignored when null/empty or
     *                         disabled)
     * @param replacementStart start of the range to replace, or a negative
     *                         sentinel meaning "at the caret"
     * @param replacementEnd   end of the range to replace (>= start when
     *                         explicit), or a negative sentinel
     */
    public void applyImeText(String committed, int replacementStart, int replacementEnd) {
        if (!isEnabled() || committed == null || committed.isEmpty()) {
            return;
        }
        boolean atCaret = replacementStart < 0 || replacementEnd < 0
                || replacementStart == replacementEnd;
        if (atCaret) {
            insertText(committed);
            return;
        }
        int length = text.length();
        int lo = Math.max(0, Math.min(replacementStart, length));
        int hi = Math.max(lo, Math.min(replacementEnd, length));
        replaceRange(lo, hi, committed, lo + committed.length());
    }

    // ------------------------------------------------------------------
    // Editing primitives
    // ------------------------------------------------------------------

    /**
     * Deletes the current selection (if any) through the shared mutation
     * funnel, so {@link #onTextChanged} fires for cut/delete-selection paths.
     */
    private void deleteSelectionIfNeeded() {
        if (hasSelection()) {
            int lo = Math.min(caret, selectionAnchor);
            int hi = Math.max(caret, selectionAnchor);
            setTextInternal(text.substring(0, lo) + text.substring(hi), lo, true);
        }
    }

    private void moveCaret(int delta, boolean keepSelection) {
        int updated = Math.max(0, Math.min(text.length(), caret + delta));
        if (updated == caret && (keepSelection || selectionAnchor == updated)) {
            // Nothing moved and the selection state is unchanged: skip the
            // invalidation so no-op key repeats don't schedule repaints.
            return;
        }
        caret = updated;
        if (!keepSelection) {
            selectionAnchor = caret;
        }
        invalidate();
    }

    private void setCaretTo(int position, boolean keepSelection) {
        int updated = Math.max(0, Math.min(text.length(), position));
        if (updated == caret && (keepSelection || selectionAnchor == updated)) {
            // Same invariant as moveCaret: only invalidate on a real change.
            return;
        }
        caret = updated;
        if (!keepSelection) {
            selectionAnchor = caret;
        }
        invalidate();
    }

    /**
     * Handles Backspace: deletes the selection if present, otherwise the
     * character before the caret. All mutations go through
     * {@link #setTextInternal} so {@link #onTextChanged} fires.
     */
    private void handleBackspace() {
        if (hasSelection()) {
            deleteSelectionIfNeeded();
        } else if (caret > 0) {
            setTextInternal(text.substring(0, caret - 1) + text.substring(caret),
                    caret - 1, true);
        }
        // Nothing to delete and no selection: the visual state is unchanged,
        // so skip the invalidation entirely.
    }

    /**
     * Handles Delete: deletes the selection if present, otherwise the
     * character at the caret. All mutations go through
     * {@link #setTextInternal} so {@link #onTextChanged} fires.
     */
    private void handleDelete() {
        if (hasSelection()) {
            deleteSelectionIfNeeded();
        } else if (caret < text.length()) {
            setTextInternal(text.substring(0, caret) + text.substring(caret + 1),
                    caret, true);
        } else {
            invalidate();
        }
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!isFocused() || !isEnabled()) {
            return;
        }
        if (event.getType() == KeyEventType.RELEASE) {
            return;
        }
        boolean shift = event.isShiftDown();
        boolean ctrl = event.isCtrlDown();
        switch (event.getKeyCode()) {
            case KEY_LEFT:
                if (ctrl) {
                    setCaretTo(wordBackward(), shift);
                } else {
                    moveCaret(-1, shift);
                }
                break;
            case KEY_RIGHT:
                if (ctrl) {
                    setCaretTo(wordForward(), shift);
                } else {
                    moveCaret(1, shift);
                }
                break;
            case KEY_HOME:
                setCaretTo(0, shift);
                break;
            case KEY_END:
                setCaretTo(text.length(), shift);
                break;
            case KEY_BACKSPACE:
                handleBackspace();
                break;
            case KEY_DELETE:
                handleDelete();
                break;
            case KEY_ENTER:
                // Single-line field: Enter just commits (collapses selection)
                clearSelection();
                break;
            default:
                if (ctrl) {
                    char c = Character.toLowerCase(event.getKeyChar());
                    if (c == 'a') {
                        selectAll();
                    } else if (c == 'c' && hasSelection() && clipboardCopyHandler != null) {
                        clipboardCopyHandler.run();
                    } else if (c == 'x' && hasSelection()) {
                        if (clipboardCopyHandler != null) {
                            clipboardCopyHandler.run();
                        }
                        // deleteSelectionIfNeeded now routes through
                        // setTextInternal, so onTextChanged fires on cut.
                        deleteSelectionIfNeeded();
                    } else if (c == 'v' && clipboardPasteHandler != null) {
                        // Application reads the clipboard and calls insertText
                        clipboardPasteHandler.run();
                    }
                }
                break;
        }
    }

    /**
     * Called by the backend char callback plumbing (via {@code Application})
     * when a printable character is typed while this field has focus.
     *
     * @param ch the typed character
     */
    public void onCharTyped(char ch) {
        if (!isEnabled() || !isFocused()) {
            return;
        }
        if (ch >= 32 && ch != 127) { // ignore control chars
            insertText(String.valueOf(ch));
        }
    }

    private int wordBackward() {
        int i = Math.min(caret, selectionAnchor);
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private int wordForward() {
        int i = Math.max(caret, selectionAnchor);
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getType()) {
            case PRESS:
                if (event.getButton() == MouseButton.LEFT && contains(event.getX(), event.getY())) {
                    requestFocus();
                    selecting = true;
                    setCaretTo(positionAtX(event.getX()), false); // clears any selection
                    return true;
                }
                return false;
            case DRAG:
                // Application reports DRAG only while a button is held; extend
                // the selection from the press anchor out to the pointer.
                if (selecting && event.getButton() == MouseButton.LEFT) {
                    setCaretTo(positionAtX(event.getX()), true); // keep anchor
                    return true;
                }
                return false;
            case MOVE:
                // Hover with no button down (Application only sends MOVE then):
                // any in-progress selection gesture has ended, even if the
                // RELEASE happened outside this field and was never delivered.
                selecting = false;
                return false;
            case RELEASE:
                selecting = false;
                return false;
            default:
                return false;
        }
    }

    /** Maps a window-space x coordinate to the nearest caret index. */
    private int positionAtX(float mouseX) {
        Font font = getFont();
        float padding = getFieldPadding();
        float local = mouseX - x - padding;
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i <= text.length(); i++) {
            float pos = font.measureTextWidth(text.substring(0, i));
            float dist = Math.abs(pos - local);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Measurement & rendering
    // ------------------------------------------------------------------

    /**
     * Gets the field font: CSS {@code font-family}/{@code font-size}
     * declarations win through {@link Component#resolveFont}; otherwise the
     * shared theme BODY font (owned by {@code FontManager}) is returned.
     *
     * @return the font, never null
     */
    private Font getFont() {
        io.github.yasmramos.glyphui.style.Style s = getComputedStyle();
        if (s.has(StyleProperty.FONT_FAMILY) || s.has(StyleProperty.FONT_SIZE)) {
            return resolveFont(Theme.FontRole.BODY);
        }
        return getTheme().getFont(Theme.FontRole.BODY);
    }

    /** Intrinsic minimum width for a content-less field. */
    private static final float MIN_INTRINSIC_WIDTH = 80.0f;

    /**
     * Gets the preferred width from the content (text or placeholder) plus
     * padding, with a modest intrinsic minimum so an empty field still has a
     * usable click target. Used by the base measurement path when no explicit
     * CSS size was declared.
     *
     * @return the preferred width in pixels
     */
    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        Font font = getFont();
        float content = Math.max(font.measureTextWidth(text), font.measureTextWidth(placeholder));
        return Math.max(content + 2.0f * getFieldPadding(), MIN_INTRINSIC_WIDTH);
    }

    /**
     * Resolves the horizontal/vertical padding: a CSS {@code padding}
     * declaration wins over the theme's control padding.
     *
     * @return the padding in pixels
     */
    private float getFieldPadding() {
        return resolveFloatStyle(StyleProperty.PADDING, null, getTheme().getPadding());
    }

    @Override
    public float getPreferredHeight() {
        io.github.humbleui.skija.FontMetrics metrics = getFont().getMetrics();
        return (metrics.getDescent() - metrics.getAscent()) + 2.0f * getFieldPadding();
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        // An explicit CSS width/height (or bounds set through the layout /
        // constructor path) must win over content-based sizing: delegate to
        // the base measurement path, which honours declared sizes including
        // percentages resolved against the parent.
        io.github.yasmramos.glyphui.style.Style s = getComputedStyle();
        boolean cssSized = s != null && (s.has(StyleProperty.WIDTH) || s.has(StyleProperty.HEIGHT));
        if (sizeExplicitlySet || cssSized) {
            return super.measure(maxWidth, maxHeight);
        }
        // Text fields stretch horizontally: intrinsic width is a modest
        // minimum (placeholder/content based), height is one text line.
        float w = Math.min(getPreferredWidth(), sanitize(maxWidth));
        float h = Math.min(getPreferredHeight(), sanitize(maxHeight));
        return new Dimension(w, h);
    }

    private static float sanitize(float value) {
        if (Float.isNaN(value) || value == Float.POSITIVE_INFINITY) {
            return Float.MAX_VALUE;
        }
        return Math.max(0.0f, value);
    }

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();
        Theme theme = getTheme();

        // Background: CSS `background` wins, then the entry-surface theme
        // token (not the page background — a field painted with the page
        // color shows nothing but its border). The disabled path consults
        // the computed style first so `input:disabled { ... }` applies.
        int bgColor;
        if (enabled) {
            bgColor = resolveIntStyle(StyleProperty.BACKGROUND, null, theme.getInputBackgroundColor());
        } else {
            bgColor = hasStyleValue(StyleProperty.BACKGROUND)
                    ? resolveIntStyle(StyleProperty.BACKGROUND, null, theme.getControlDisabledColor())
                    : theme.getControlDisabledColor();
        }
        float cornerRadius = resolveFloatStyle(StyleProperty.BORDER_RADIUS, null,
                theme.getControlCornerRadius());
        backgroundPaint.setColor(bgColor);
        canvas.drawRRect(x, y, width, height, cornerRadius, cornerRadius, backgroundPaint);

        // Border: CSS `border-color` wins over the state-driven theme
        // fallbacks (accent when focused, plain border otherwise).
        int focusBorder = isFocused() ? theme.getAccentColor() : theme.getBorderColor();
        borderPaint.setColor(resolveIntStyle(StyleProperty.BORDER_COLOR, null, focusBorder));
        canvas.drawRRect(x + 0.5f, y + 0.5f, Math.max(0.0f, width - 1.0f), Math.max(0.0f, height - 1.0f),
                cornerRadius, cornerRadius, borderPaint);

        Font font = getFont();
        float padding = getFieldPadding();
        io.github.humbleui.skija.FontMetrics metrics = font.getMetrics();
        float lineHeight = metrics.getDescent() - metrics.getAscent();
        // Vertically center the glyph box ([baseline+ascent, baseline+descent],
        // ascent negative) in the field. The previous top-anchored
        // "y + padding - ascent" left the text sitting high in taller fields.
        float baseline = y + (height - lineHeight) / 2.0f - metrics.getAscent();

        // Selection highlight
        if (isFocused() && hasSelection()) {
            int lo = Math.min(caret, selectionAnchor);
            int hi = Math.max(caret, selectionAnchor);
            float startX = x + padding + font.measureTextWidth(text.substring(0, lo));
            float endX = x + padding + font.measureTextWidth(text.substring(0, hi));
            selectionPaint.setColor(withAlpha(theme.getAccentColor(), 90));
            canvas.drawRect(startX, baseline + metrics.getAscent() - 2.0f,
                    Math.max(0.0f, endX - startX), lineHeight + 4.0f, selectionPaint);
        }

        // Text or placeholder: CSS `color` wins over the theme foreground;
        // the disabled path re-resolves through the computed style so
        // `input:disabled { color: ... }` applies instead of jumping straight
        // to the dimmed theme token.
        if (!text.isEmpty()) {
            int textColor = enabled
                    ? resolveIntStyle(StyleProperty.COLOR, null, theme.getForegroundColor())
                    : resolveIntStyle(StyleProperty.COLOR, null, theme.getControlTextDisabledColor());
            textPaint.setColor(textColor);
            canvas.drawString(text, x + padding, baseline, textPaint, font);
        } else if (!isFocused() && !placeholder.isEmpty()) {
            textPaint.setColor(withAlpha(theme.getForegroundColor(), 120));
            canvas.drawString(placeholder, x + padding, baseline, textPaint, font);
        }

        // Caret
        if (isFocused() && enabled) {
            float caretX = x + padding + font.measureTextWidth(text.substring(0, caret));
            cursorPaint.setColor(resolveIntStyle(StyleProperty.ACCENT_COLOR, null, theme.getAccentColor()));
            canvas.drawRect(caretX, baseline + metrics.getAscent() - 2.0f,
                    1.5f, lineHeight + 4.0f, cursorPaint);
        }
    }

    /** Blends an alpha byte into an opaque ARGB color. */
    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    // ------------------------------------------------------------------
    // Accessibility
    // ------------------------------------------------------------------

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.TEXT_FIELD;
    }

    @Override
    public String getAccessibleName() {
        // Text fields fall back to their placeholder when no name was set;
        // the base class default (style tag) is only a last resort.
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return placeholder != null && !placeholder.isEmpty() ? placeholder : super.getAccessibleName();
    }

    @Override
    protected void onDispose() {
        if (backgroundPaint != null) {
            backgroundPaint.close();
            backgroundPaint = null;
        }
        if (borderPaint != null) {
            borderPaint.close();
            borderPaint = null;
        }
        if (textPaint != null) {
            textPaint.close();
            textPaint = null;
        }
        if (cursorPaint != null) {
            cursorPaint.close();
            cursorPaint = null;
        }
        if (selectionPaint != null) {
            selectionPaint.close();
            selectionPaint = null;
        }
        // Fonts come from FontManager and are shared — do not close them.
    }
}
