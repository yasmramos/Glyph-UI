package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;

/**
 * Multi-line editable text area: the classic {@code <textarea>} widget.
 *
 * <p>Editing reuses the {@link TextField} model (single {@code String} buffer,
 * caret + selection anchor, clipboard and IME) and adds the behaviour that a
 * multi-line field needs: Enter inserts a newline instead of submitting,
 * Up/Down/Home/End move the caret by visual line, and the content scrolls
 * vertically so the caret stays in view. The value is exposed through the
 * inherited {@link #getText()} / {@link #setText(String)} pair, newlines
 * included.</p>
 *
 * <p>Unlike the single-line field, this widget keeps a stable size driven by
 * the style/layout (a text area is normally given a width and height by CSS or
 * its parent); its intrinsic size is a modest minimum. It is not a rich-text
 * editor: no per-run styling, wrapping or line numbers.</p>
 */
public class TextArea extends TextField {

    /** Key codes (GlyphKeys wire values) handled here rather than in TextField. */
    private static final int KEY_ENTER = 257;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;
    private static final int KEY_DOWN = 264;
    private static final int KEY_UP = 265;
    private static final int KEY_HOME = 268;
    private static final int KEY_END = 269;

    /** Intrinsic minimum width for an empty area. */
    private static final float MIN_INTRINSIC_WIDTH = 160.0f;

    /** Intrinsic minimum height: roughly three body lines plus padding. */
    private static final float MIN_INTRINSIC_HEIGHT = 72.0f;

    /** Width of the caret, in pixels (matches the single-line field). */
    private static final float CARET_WIDTH = 1.5f;

    private Paint backgroundPaint;
    private Paint borderPaint;
    private Paint textPaint;
    private Paint cursorPaint;
    private Paint selectionPaint;

    /** Vertical scroll, in pixels, keeping the caret line inside the box. */
    private float verticalScrollOffset = 0.0f;

    /**
     * Creates a text area.
     *
     * @param x      initial x position
     * @param y      initial y position
     * @param width  initial width
     * @param height initial height
     */
    public TextArea(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    /**
     * Creates a zero-sized text area to be positioned by a layout manager.
     */
    public TextArea() {
        super();
    }

    @Override
    protected String defaultStyleTag() {
        return "textarea";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.TEXT_AREA;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Unlike the single-line field, newlines and tabs survive: pasted
     * multi-line text keeps its shape. {@code \r\n} and lone {@code \r} are
     * normalised to {@code \n} first, and other control characters are
     * dropped.</p>
     */
    @Override
    public void insertText(String chars) {
        if (!isEnabled() || chars == null || chars.isEmpty()) {
            return;
        }
        String clean = sanitizeMultiline(chars);
        if (clean.isEmpty()) {
            return;
        }
        String text = getText();
        int lo = Math.min(caret, selectionAnchor);
        int hi = Math.max(caret, selectionAnchor);
        String updated = text.substring(0, lo) + clean + text.substring(hi);
        replaceText(updated, lo + clean.length());
    }

    /**
     * Routes a full-buffer replacement through the inherited mutation funnel
     * (which fires {@code onTextChanged} and clamps the caret), then forces a
     * relayout because a multi-line value can change the preferred height.
     */
    private void replaceText(String updated, int newCaret) {
        setText(updated, false);
        caret = Math.max(0, Math.min(newCaret, updated.length()));
        selectionAnchor = caret;
        invalidateLayout();
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!isFocused() || !isEnabled() || event.getType() == KeyEventType.RELEASE) {
            return;
        }
        boolean shift = event.isShiftDown();
        switch (event.getKeyCode()) {
            case KEY_LEFT:
                moveCaret(event.isCtrlDown() ? wordBackward() - caret : -1, shift);
                break;
            case KEY_RIGHT:
                moveCaret(event.isCtrlDown() ? wordForward() - caret : 1, shift);
                break;
            case KEY_UP:
                moveCaretVertically(-1, shift);
                break;
            case KEY_DOWN:
                moveCaretVertically(1, shift);
                break;
            case KEY_HOME:
                setCaret(lineStartAt(getText(), caret), shift);
                break;
            case KEY_END:
                setCaret(lineEndAt(getText(), caret), shift);
                break;
            case KEY_BACKSPACE:
                handleBackspace();
                break;
            case KEY_DELETE:
                handleDelete();
                break;
            case KEY_ENTER:
                // Multi-line field: Enter inserts a newline instead of
                // committing. onSubmit never fires here.
                insertText("\n");
                break;
            default:
                // Ctrl+A/C/X/V and anything else keep the single-line
                // behaviour (the clipboard hooks work on any TextField).
                super.onKeyEvent(event);
                break;
        }
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
                    setCaret(positionAt(event.getX(), event.getY()), false);
                    return true;
                }
                return false;
            default:
                // Dragging/selection extension is not supported yet; only the
                // caret is repositioned on press. Return false so the event
                // can bubble as usual.
                return false;
        }
    }

    // ------------------------------------------------------------------
    // Editing primitives
    // ------------------------------------------------------------------

    private void moveCaret(int delta, boolean keepSelection) {
        setCaret(caret + delta, keepSelection);
    }

    private void setCaret(int position, boolean keepSelection) {
        int updated = Math.max(0, Math.min(getText().length(), position));
        if (updated == caret && (keepSelection || selectionAnchor == updated)) {
            return;
        }
        caret = updated;
        if (!keepSelection) {
            selectionAnchor = caret;
        }
        invalidate();
    }

    /** Moves the caret one visual line up ({@code -1}) or down ({@code +1}). */
    private void moveCaretVertically(int direction, boolean keepSelection) {
        String text = getText();
        int lineStart = lineStartAt(text, caret);
        int column = caret - lineStart;
        int target;
        if (direction < 0) {
            if (lineStart == 0) {
                target = 0;
            } else {
                int previousStart = lineStartAt(text, lineStart - 1);
                target = Math.min(previousStart + column, lineEndAt(text, previousStart));
            }
        } else {
            int lineEnd = lineEndAt(text, caret);
            if (lineEnd >= text.length()) {
                target = text.length();
            } else {
                int nextStart = lineEnd + 1;
                target = Math.min(nextStart + column, lineEndAt(text, nextStart));
            }
        }
        setCaret(target, keepSelection);
    }

    private void handleBackspace() {
        String text = getText();
        if (hasSelection()) {
            deleteSelection();
        } else if (caret > 0) {
            replaceText(text.substring(0, caret - 1) + text.substring(caret), caret - 1);
        }
    }

    private void handleDelete() {
        String text = getText();
        if (hasSelection()) {
            deleteSelection();
        } else if (caret < text.length()) {
            replaceText(text.substring(0, caret) + text.substring(caret + 1), caret);
        }
    }

    private void deleteSelection() {
        String text = getText();
        int lo = Math.min(caret, selectionAnchor);
        int hi = Math.max(caret, selectionAnchor);
        replaceText(text.substring(0, lo) + text.substring(hi), lo);
    }

    private int wordBackward() {
        String text = getText();
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
        String text = getText();
        int i = Math.max(caret, selectionAnchor);
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    // ------------------------------------------------------------------
    // Line helpers
    // ------------------------------------------------------------------

    private static int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static int lineIndexAt(String text, int index) {
        int lines = 0;
        int limit = Math.min(index, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static int lineStartAt(String text, int index) {
        int limit = Math.min(index, text.length());
        for (int i = limit - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        return 0;
    }

    private static int lineEndAt(String text, int index) {
        int next = text.indexOf('\n', Math.max(0, Math.min(index, text.length())));
        return next < 0 ? text.length() : next;
    }

    private static String longestLine(String text) {
        String longest = "";
        int start = 0;
        while (start <= text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                end = text.length();
            }
            if (end - start > longest.length()) {
                longest = text.substring(start, end);
            }
            if (end == text.length()) {
                break;
            }
            start = end + 1;
        }
        return longest;
    }

    /**
     * Keeps newlines and tabs while dropping other ISO control characters,
     * after normalising {@code \r\n} and lone {@code \r} to {@code \n}.
     */
    private static String sanitizeMultiline(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\r') {
                // Normalise CRLF and lone CR to a single LF.
                if (i < value.length() && value.charAt(i) == '\n') {
                    i++;
                }
                out.append('\n');
            } else if (cp == '\n' || cp == '\t') {
                out.appendCodePoint(cp);
            } else if (!Character.isISOControl(cp)) {
                out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Measurement & rendering
    // ------------------------------------------------------------------

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return getWidth();
        }
        Font font = areaFont();
        float pad = areaPadding();
        float content = Math.max(font.measureTextWidth(longestLine(getText())),
                font.measureTextWidth(getPlaceholder()));
        return Math.max(content + 2.0f * pad, MIN_INTRINSIC_WIDTH);
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return getHeight();
        }
        Font font = areaFont();
        float pad = areaPadding();
        float lineHeight = lineHeight(font);
        float content = countLines(getText()) * lineHeight;
        return Math.max(content + 2.0f * pad, MIN_INTRINSIC_HEIGHT);
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        Style style = getComputedStyle();
        boolean cssSized = style != null
                && (style.has(StyleProperty.WIDTH) || style.has(StyleProperty.HEIGHT));
        if (sizeExplicitlySet || cssSized) {
            return super.measure(maxWidth, maxHeight);
        }
        float w = Math.min(getPreferredWidth(), areaSanitize(maxWidth));
        float h = Math.min(getPreferredHeight(), areaSanitize(maxHeight));
        return new Dimension(w, h);
    }

    private static float areaSanitize(float value) {
        if (Float.isNaN(value) || value == Float.POSITIVE_INFINITY) {
            return Float.MAX_VALUE;
        }
        return Math.max(0.0f, value);
    }

    /**
     * Resolves the area font: CSS {@code font-family}/{@code font-size} win
     * through {@link Component#resolveFont}; otherwise the shared theme BODY
     * font is used. Mirrors the single-line field without depending on its
     * private helper.
     */
    private Font areaFont() {
        Style style = getComputedStyle();
        if (style.has(StyleProperty.FONT_FAMILY) || style.has(StyleProperty.FONT_SIZE)) {
            return resolveFont(Theme.FontRole.BODY);
        }
        return getTheme().getFont(Theme.FontRole.BODY);
    }

    /** Resolves the padding: CSS {@code padding} wins over the theme value. */
    private float areaPadding() {
        return resolveFloatStyle(StyleProperty.PADDING, null, getTheme().getPadding());
    }

    private static float lineHeight(Font font) {
        FontMetrics metrics = font.getMetrics();
        float height = metrics.getDescent() - metrics.getAscent();
        return height > 0.0f ? height : Math.max(font.getSize(), 12.0f);
    }

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

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();
        Theme theme = getTheme();

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
        canvas.drawRRect(0, 0, width, height, cornerRadius, cornerRadius, backgroundPaint);

        int focusBorder = isFocused() ? theme.getAccentColor() : theme.getBorderColor();
        borderPaint.setColor(resolveIntStyle(StyleProperty.BORDER_COLOR, null, focusBorder));
        canvas.drawRRect(0.5f, 0.5f, Math.max(0.0f, width - 1.0f), Math.max(0.0f, height - 1.0f),
                cornerRadius, cornerRadius, borderPaint);

        Font font = areaFont();
        float padding = areaPadding();
        FontMetrics metrics = font.getMetrics();
        float lineHeight = lineHeight(font);
        float baselineOffset = -metrics.getAscent();

        String text = getText();
        float contentLeft = padding;
        float contentTop = padding;
        float contentWidth = Math.max(0.0f, width - 2.0f * padding);
        float contentHeight = Math.max(0.0f, height - 2.0f * padding);
        int lineCount = countLines(text);

        // Keep the caret line inside the box.
        int caretLine = lineIndexAt(text, caret);
        float caretTop = caretLine * lineHeight;
        if (caretTop - verticalScrollOffset < 0.0f) {
            verticalScrollOffset = caretTop;
        } else if (caretTop + lineHeight - verticalScrollOffset > contentHeight) {
            verticalScrollOffset = caretTop + lineHeight - contentHeight;
        }
        float maxScroll = Math.max(0.0f, lineCount * lineHeight - contentHeight);
        verticalScrollOffset = Math.max(0.0f, Math.min(verticalScrollOffset, maxScroll));

        int saveCount = canvas.save();
        canvas.clipRect(contentLeft, contentTop, contentWidth, contentHeight);

        int textColor = enabled
                ? resolveIntStyle(StyleProperty.COLOR, null, theme.getForegroundColor())
                : resolveIntStyle(StyleProperty.COLOR, null, theme.getControlTextDisabledColor());
        textPaint.setColor(textColor);

        int selLo = Math.min(caret, selectionAnchor);
        int selHi = Math.max(caret, selectionAnchor);
        boolean hasSelection = isFocused() && selLo != selHi;
        if (hasSelection) {
            selectionPaint.setColor(areaWithAlpha(theme.getAccentColor(), 90));
        }

        int lineStart = 0;
        int lineIndex = 0;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String lineText = text.substring(lineStart, lineEnd);
            float lineY = contentTop + lineIndex * lineHeight - verticalScrollOffset;
            float baseline = lineY + baselineOffset;

            if (hasSelection) {
                int selStart = Math.max(selLo, lineStart);
                int selEnd = Math.min(selHi, lineEnd);
                if (selEnd > selStart) {
                    float sx = contentLeft + font.measureTextWidth(text.substring(lineStart, selStart));
                    float ex = contentLeft + font.measureTextWidth(text.substring(lineStart, selEnd));
                    canvas.drawRect(sx, lineY, Math.max(0.0f, ex - sx), lineHeight, selectionPaint);
                }
                if (selHi > lineEnd && selLo <= lineEnd) {
                    // The selection crosses this line's break: paint a block
                    // for the newline itself so the highlight looks continuous.
                    float nx = contentLeft + font.measureTextWidth(lineText);
                    canvas.drawRect(nx, lineY, font.measureTextWidth(" "), lineHeight, selectionPaint);
                }
            }

            if (!lineText.isEmpty()) {
                canvas.drawString(lineText, contentLeft, baseline, textPaint, font);
            }

            if (isFocused() && enabled && caretLine == lineIndex
                    && caret >= lineStart && caret <= lineEnd) {
                float caretX = contentLeft + font.measureTextWidth(text.substring(lineStart, caret));
                cursorPaint.setColor(resolveIntStyle(StyleProperty.ACCENT_COLOR, null,
                        theme.getAccentColor()));
                canvas.drawRect(caretX, lineY, CARET_WIDTH, lineHeight, cursorPaint);
            }

            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
            lineIndex++;
        }

        if (text.isEmpty() && !isFocused() && !getPlaceholder().isEmpty()) {
            textPaint.setColor(areaWithAlpha(theme.getForegroundColor(), 120));
            canvas.drawString(getPlaceholder(), contentLeft, contentTop + baselineOffset, textPaint, font);
        }

        canvas.restoreToCount(saveCount);
    }

    /** Maps a widget-space point to the nearest caret index. */
    private int positionAt(float mouseX, float mouseY) {
        Font font = areaFont();
        float padding = areaPadding();
        String text = getText();
        float lineHeight = lineHeight(font);
        int clickedLine = (int) Math.floor((mouseY - y - padding + verticalScrollOffset) / lineHeight);
        clickedLine = Math.max(0, Math.min(countLines(text) - 1, clickedLine));
        int lineStart = 0;
        for (int i = 0; i < clickedLine; i++) {
            int next = text.indexOf('\n', lineStart);
            if (next < 0) {
                break;
            }
            lineStart = next + 1;
        }
        int lineEnd = lineEndAt(text, lineStart);
        float local = mouseX - x - padding;
        float pos = 0.0f;
        for (int i = lineStart; i < lineEnd; i++) {
            String cp = text.substring(i, i + 1);
            float advance = font.measureTextWidth(cp);
            if (pos + advance / 2.0f >= local) {
                return i;
            }
            pos += advance;
        }
        return lineEnd;
    }

    private static int areaWithAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    @Override
    protected void onDispose() {
        super.onDispose();
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
    }
}
