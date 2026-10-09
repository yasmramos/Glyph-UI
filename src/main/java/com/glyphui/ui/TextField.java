package com.glyphui.ui;

import com.glyphui.events.KeyEvent;
import com.glyphui.events.KeyEventType;
import com.glyphui.events.MouseEvent;
import com.glyphui.graphics.Canvas;
import com.glyphui.graphics.Dimension;
import com.glyphui.graphics.Theme;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;

/**
 * Single-line editable text field.
 *
 * <p><b>IME limitations (v0.1):</b> text input flows through GLFW's raw
 * {@code glfwSetCharCallback} / {@code glfwSetKeyCallback} and clipboard
 * access uses {@code glfwGetClipboardString}/{@code glfwSetClipboardString}.
 * GLFW does not expose an IME API, so real input-method composition windows
 * and candidate lists (CJK input, dead-key composition on some platforms)
 * are NOT supported — characters arrive already committed. This is documented
 * in the README.</p>
 *
 * <p>Supported editing: caret movement (arrows/Home/End, with Shift for
 * selection), Backspace/Delete, Ctrl+C/X/V via the window clipboard hooks
 * installed by {@code Application}, Ctrl+A select-all, plain text insertion
 * through {@link #insertText(String)} (also fed by the char callback).</p>
 */
public class TextField extends Component {

    @Override
    protected String defaultStyleTag() {
        return "input";
    }

    /** GLFW key codes used here without importing LWJGL into the widget layer. */
    private static final int KEY_BACKSPACE = 259; // GLFW_KEY_BACKSPACE
    private static final int KEY_DELETE = 261;    // GLFW_KEY_DELETE
    private static final int KEY_RIGHT = 262;     // GLFW_KEY_RIGHT
    private static final int KEY_LEFT = 263;      // GLFW_KEY_LEFT
    private static final int KEY_DOWN = 264;      // GLFW_KEY_DOWN
    private static final int KEY_UP = 265;        // GLFW_KEY_UP
    private static final int KEY_HOME = 268;      // GLFW_KEY_HOME
    private static final int KEY_END = 269;       // GLFW_KEY_END
    private static final int KEY_ENTER = 257;     // GLFW_KEY_ENTER

    /** Clipboard bridge installed by {@code Application} at startup. */
    private static Runnable clipboardCopyHandler;   // copies this field's selection to the system clipboard
    private static Runnable clipboardPasteHandler;  // reads the system clipboard and inserts it here

    private String text = "";
    private String placeholder = "";

    /** Caret position (character index, 0..text.length()). */
    private int caret = 0;

    /** Anchor of the selection (caret..selectionAnchor is the selection). */
    private int selectionAnchor = 0;

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
        initPaints();
    }

    /**
     * Creates a zero-sized text field to be positioned by a layout manager.
     */
    public TextField() {
        super();
        initPaints();
    }

    private void initPaints() {
        backgroundPaint = new Paint();
        backgroundPaint.setAntiAlias(true);

        borderPaint = new Paint();
        borderPaint.setStroke(true);
        borderPaint.setStrokeWidth(1.0f);
        borderPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setAntiAlias(true);

        cursorPaint = new Paint();
        cursorPaint.setAntiAlias(true);

        selectionPaint = new Paint();
        selectionPaint.setAntiAlias(true);
    }

    // ------------------------------------------------------------------
    // Clipboard bridge (installed by Application)
    // ------------------------------------------------------------------

    /**
     * Installs the process-wide clipboard hooks used by Ctrl+C/X/V. The
     * copy hook is expected to call {@link #getSelectedText()} on the
     * focused field and push it through {@code glfwSetClipboardString};
     * the paste hook reads {@code glfwGetClipboardString} and feeds it to
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
     * Replaces the whole text, resetting caret and selection.
     *
     * @param text the new text (null becomes "")
     */
    public void setText(String text) {
        setText(text, true);
    }

    /**
     * Replaces the whole text. When {@code notify} is true and the content
     * actually changed, the {@code onTextChanged} handler fires (used by the
     * markup loader's {@code onchange} attribute). Programmatic initialisation
     * (e.g. loading a value from markup) should pass {@code false}.
     *
     * @param text   the new text (null becomes "")
     * @param notify whether to fire the change handler
     */
    public void setText(String text, boolean notify) {
        String old = this.text;
        this.text = (text == null) ? "" : text;
        this.caret = this.text.length();
        this.selectionAnchor = this.caret;
        invalidate();
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
        this.placeholder = (placeholder == null) ? "" : placeholder;
        invalidate();
    }

    /** @return the caret index (0..length) */
    public int getCaret() {
        return caret;
    }

    /**
     * Gets the font used to lay out this field's text (the shared theme BODY
     * font, owned by {@code FontManager}). Exposed for the IME bridge, which
     * must measure the caret position exactly like {@link #render} does.
     *
     * @return the rendering font (never null once a theme exists)
     */
    public Font getFontForIme() {
        return getFont();
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
    public void replaceRange(int lo, int hi, String newText, int newCaret) {
        if (!isEnabled()) {
            return;
        }
        int length = text.length();
        int from = Math.max(0, Math.min(lo, length));
        int to = Math.max(from, Math.min(hi, length));
        String inserted = newText == null ? "" : newText;
        text = text.substring(0, from) + inserted + text.substring(to);
        caret = Math.max(0, Math.min(newCaret, text.length()));
        selectionAnchor = caret;
        invalidate();
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
     * This is the entry point used by the GLFW char callback.
     *
     * @param chars the text to insert (ignored when null/empty or disabled)
     */
    public void insertText(String chars) {
        if (!isEnabled() || chars == null || chars.isEmpty()) {
            return;
        }
        deleteSelectionIfNeeded();
        text = text.substring(0, caret) + chars + text.substring(caret);
        caret += chars.length();
        selectionAnchor = caret;
        invalidate();
    }

    // ------------------------------------------------------------------
    // Editing primitives
    // ------------------------------------------------------------------

    private void deleteSelectionIfNeeded() {
        if (hasSelection()) {
            int lo = Math.min(caret, selectionAnchor);
            int hi = Math.max(caret, selectionAnchor);
            text = text.substring(0, lo) + text.substring(hi);
            caret = lo;
            selectionAnchor = lo;
        }
    }

    private void moveCaret(int delta, boolean keepSelection) {
        caret = Math.max(0, Math.min(text.length(), caret + delta));
        if (!keepSelection) {
            selectionAnchor = caret;
        }
        invalidate();
    }

    private void setCaretTo(int position, boolean keepSelection) {
        caret = Math.max(0, Math.min(text.length(), position));
        if (!keepSelection) {
            selectionAnchor = caret;
        }
        invalidate();
    }

    private void handleBackspace() {
        if (hasSelection()) {
            deleteSelectionIfNeeded();
        } else if (caret > 0) {
            text = text.substring(0, caret - 1) + text.substring(caret);
            caret--;
            selectionAnchor = caret;
        }
        invalidate();
    }

    private void handleDelete() {
        if (hasSelection()) {
            deleteSelectionIfNeeded();
        } else if (caret < text.length()) {
            text = text.substring(0, caret) + text.substring(caret + 1);
        }
        invalidate();
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
                        deleteSelectionIfNeeded();
                        invalidate();
                    } else if (c == 'v' && clipboardPasteHandler != null) {
                        // Application reads glfwGetClipboardString and calls insertText
                        clipboardPasteHandler.run();
                    }
                }
                break;
        }
    }

    /**
     * Called by the GLFW char callback plumbing (via {@code Application})
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
        if (event.getType() == com.glyphui.events.MouseEventType.PRESS
                && event.getButton() == com.glyphui.events.MouseButton.LEFT
                && contains(event.getX(), event.getY())) {
            requestFocus();
            setCaretTo(positionAtX(event.getX()), false);
            return true;
        }
        return false;
    }

    /** Maps a window-space x coordinate to the nearest caret index. */
    private int positionAtX(float mouseX) {
        Font font = getFont();
        float padding = getTheme().getPadding();
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
     * Gets the shared theme BODY font (owned by {@code FontManager}).
     *
     * @return the font
     */
    private Font getFont() {
        return getTheme().getFont(Theme.FontRole.BODY);
    }

    @Override
    public float getPreferredHeight() {
        io.github.humbleui.skija.FontMetrics metrics = getFont().getMetrics();
        return (metrics.getDescent() - metrics.getAscent()) + 2.0f * getTheme().getPadding();
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        // Text fields stretch horizontally: intrinsic width is a modest
        // minimum (placeholder/content based), height is one text line.
        Font font = getFont();
        float content = Math.max(font.measureTextWidth(text), font.measureTextWidth(placeholder));
        float w = Math.min(Math.max(content + 2.0f * getTheme().getPadding(), 80.0f),
                sanitize(maxWidth));
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
    public void render(Canvas canvas) {
        Theme theme = getTheme();

        // Background
        backgroundPaint.setColor(theme.getBackgroundColor());
        canvas.drawRRect(x, y, width, height,
                theme.getControlCornerRadius(), theme.getControlCornerRadius(), backgroundPaint);

        // Border: accent when focused, theme border otherwise
        borderPaint.setColor(isFocused() ? theme.getAccentColor() : theme.getBorderColor());
        canvas.drawRRect(x + 0.5f, y + 0.5f, Math.max(0.0f, width - 1.0f), Math.max(0.0f, height - 1.0f),
                theme.getControlCornerRadius(), theme.getControlCornerRadius(), borderPaint);

        Font font = getFont();
        float padding = theme.getPadding();
        io.github.humbleui.skija.FontMetrics metrics = font.getMetrics();
        float lineHeight = metrics.getDescent() - metrics.getAscent();
        float baseline = y + padding + metrics.getAscent() * -1.0f;

        // Selection highlight
        if (isFocused() && hasSelection()) {
            int lo = Math.min(caret, selectionAnchor);
            int hi = Math.max(caret, selectionAnchor);
            float startX = x + padding + font.measureTextWidth(text.substring(0, lo));
            float endX = x + padding + font.measureTextWidth(text.substring(0, hi));
            selectionPaint.setColor(withAlpha(theme.getAccentColor(), 90));
            canvas.drawRect(startX, y + padding - 2.0f, Math.max(0.0f, endX - startX),
                    lineHeight + 4.0f, selectionPaint);
        }

        // Text or placeholder
        if (!text.isEmpty()) {
            textPaint.setColor(enabled ? theme.getForegroundColor() : theme.getControlTextDisabledColor());
            canvas.drawString(text, x + padding, baseline, textPaint, font);
        } else if (!isFocused() && !placeholder.isEmpty()) {
            textPaint.setColor(withAlpha(theme.getForegroundColor(), 120));
            canvas.drawString(placeholder, x + padding, baseline, textPaint, font);
        }

        // Caret
        if (isFocused() && enabled) {
            float caretX = x + padding + font.measureTextWidth(text.substring(0, caret));
            cursorPaint.setColor(theme.getAccentColor());
            canvas.drawRect(caretX, y + padding - 2.0f, 1.5f, lineHeight + 4.0f, cursorPaint);
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
        String base = super.getAccessibleName();
        return (base != null && !base.equals(getId())) ? base : placeholder;
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
