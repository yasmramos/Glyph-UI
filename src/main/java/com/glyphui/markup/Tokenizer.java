package com.glyphui.markup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hand-written, three-mode character scanner for {@code .glyph} markup.
 * No regular expressions and no reflection are used; every decision is an
 * explicit character comparison inside a plain loop.
 *
 * <p>Modes:</p>
 * <ul>
 *   <li><b>CONTENT</b> — free text between tags. Recognises:
 *       <ul>
 *         <li>{@code &#123;&#123;} / {@code &#125;&#125;} → literal brace escapes,
 *             folded into the surrounding TEXT token</li>
 *         <li>{@code &#123;path&#125;} → BINDING_OPEN then BINDING mode</li>
 *         <li>{@code &lt;} / {@code &lt;/} → OPEN_TAG / OPEN_CLOSE_TAG, switch to TAG mode</li>
 *         <li>{@code &lt;!---- --&gt;} comments are skipped entirely</li>
 *         <li>a stray {@code &#125;} (not doubled) → recoverable ERROR token</li>
 *       </ul></li>
 *   <li><b>TAG</b> — inside {@code <...>}. Emits TAG_NAME first, then a
 *       sequence of ATTR_NAME / EQUALS / STRING / BARE_VALUE tokens, ending
 *       with CLOSE_TAG ({@code >}) or CLOSE_SELF_TAG ({@code />}).</li>
 *   <li><b>BINDING</b> — inside {@code &#123;...&#125;} in content. Accepts only
 *       the path grammar {@code ident(.ident)*}; every other character
 *       ({@code !}, {@code &}, {@code >}, {@code ( }, digits, ...) produces an
 *       ERROR token carrying a positioned message.</li>
 * </ul>
 *
 * <p>Identifiers match {@code [A-Za-z_][A-Za-z0-9_]*}. Attribute names may
 * additionally contain {@code :} and {@code -} so that raw prefixes such as
 * {@code bind:text} or {@code for-each} lex as a single ATTR_NAME token —
 * interpreting them is left to the validator/binder.</p>
 *
 * <p>All positions are 1-based line/column pairs. The token stream always
 * ends with exactly one EOF token. ERROR tokens never terminate the scan:
 * callers (the parser) decide how to report or recover from them.</p>
 */
public final class Tokenizer {

    /** Internal scanner modes. */
    private enum Mode { CONTENT, TAG, BINDING }

    /** Full source text. */
    private final String src;

    /** Current read offset into {@link #src}. */
    private int pos;

    /** 1-based line of the current offset. */
    private int line = 1;

    /** 1-based column of the current offset. */
    private int column = 1;

    /** Scanner state machine mode. */
    private Mode mode = Mode.CONTENT;

    /**
     * Whether the next identifier in TAG mode is a tag name (true right
     * after OPEN_TAG/OPEN_CLOSE_TAG, false afterwards).
     */
    private boolean expectTagName = true;

    /**
     * Whether the next value-ish token in TAG mode is a bare attribute value
     * (set right after EQUALS).
     */
    private boolean expectValue = false;

    /** Tokens collected by {@link #tokenize()}. */
    private final List<Token> tokens = new ArrayList<>();

    /** Line of the OPEN_TAG token of the tag currently being scanned. */
    private int tagOpenLine = 1;

    /** Column of the OPEN_TAG token of the tag currently being scanned. */
    private int tagOpenColumn = 1;

    /** Line of the BINDING_OPEN token of the binding currently being scanned. */
    private int bindingOpenLine = 1;

    /** Column of the BINDING_OPEN token of the binding currently being scanned. */
    private int bindingOpenColumn = 1;

    /**
     * Creates a tokenizer over the given source.
     *
     * @param source the complete {@code .glyph} document text (null → empty)
     */
    public Tokenizer(String source) {
        this.src = source == null ? "" : source;
    }

    /**
     * Scans the whole input in one pass.
     *
     * @return unmodifiable token list, always terminated by a single EOF token
     * @throws MarkupException on unrecoverable conditions: unterminated
     *         comment, unterminated string, unexpected end of input inside a
     *         tag or inside a binding
     */
    public List<Token> tokenize() {
        while (pos < src.length()) {
            switch (mode) {
                case CONTENT -> scanContent();
                case TAG -> scanTag();
                case BINDING -> scanBinding();
            }
        }
        if (mode == Mode.TAG) {
            throw new MarkupException(
                    "unexpected end of input inside tag opened here",
                    tagOpenLine, tagOpenColumn);
        }
        if (mode == Mode.BINDING) {
            throw new MarkupException(
                    "unexpected end of input inside binding opened here",
                    bindingOpenLine, bindingOpenColumn);
        }
        tokens.add(new Token(TokenKind.EOF, "", line, column));
        return Collections.unmodifiableList(tokens);
    }

    // ------------------------------------------------------------------
    // CONTENT mode
    // ------------------------------------------------------------------

    /**
     * Scans content until a markup construct is found. Accumulates literal
     * text (with {@code &#123;&#123;}/{@code &#125;&#125;} folded) into a single TEXT
     * token, then emits the structural token and switches mode.
     *
     * <p>Escape folding note: {@code &#123;&#123;} / {@code &#125;&#125;} fold into
     * the surrounding text run without splitting it, so each TEXT token starts
     * exactly at the position of its first literal character.</p>
     */
    private void scanContent() {
        StringBuilder buf = new StringBuilder();
        int textLine = line;
        int textCol = column;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '{') {
                if (peekAt(1) == '{') {           // {{ → literal "{" folded
                    buf.append('{');              // into the current text run
                    advance();
                    advance();
                    continue;
                }
                // The structural token splits the text run: emit what came
                // before it first, so following text starts right after '}'.
                flushText(buf, textLine, textCol);
                int openLine = line;
                int openCol = column;
                advance();                        // consume '{'
                emit(new Token(TokenKind.BINDING_OPEN, "{", openLine, openCol));
                bindingOpenLine = openLine;
                bindingOpenColumn = openCol;
                mode = Mode.BINDING;
                return;
            }
            if (c == '}') {
                if (peekAt(1) == '}') {           // }} → literal "}" folded
                    buf.append('}');              // into the current text run
                    advance();
                    advance();
                    continue;
                }
                flushText(buf, textLine, textCol);
                Token err = new Token(TokenKind.ERROR,
                        "unmatched '}' in text", line, column);
                advance();
                emit(err);
                textLine = line;
                textCol = column;
                continue;
            }
            if (c == '<') {
                if (isCommentStart()) {
                    // Flush before skipping so the run keeps its own start
                    // position even when the comment spans several lines.
                    flushText(buf, textLine, textCol);
                    skipComment();
                    textLine = line;
                    textCol = column;
                    continue;
                }
                flushText(buf, textLine, textCol);
                int ltLine = line;
                int ltCol = column;
                advance();                        // consume '<'
                boolean closing = peek() == '/';
                if (closing) {
                    advance();
                    emit(new Token(TokenKind.OPEN_CLOSE_TAG, "</", ltLine, ltCol));
                } else {
                    emit(new Token(TokenKind.OPEN_TAG, "<", ltLine, ltCol));
                }
                tagOpenLine = ltLine;
                tagOpenColumn = ltCol;
                expectTagName = true;
                expectValue = false;
                mode = Mode.TAG;
                return;
            }
            buf.append(c);
            advance();
        }
        flushText(buf, textLine, textCol);
    }

    /**
     * Emits the accumulated buffer as a TEXT token (when non-empty) and
     * resets it. The caller keeps tracking the run start position locally.
     *
     * @param buf      the text accumulator
     * @param textLine 1-based line where the run started
     * @param textCol  1-based column where the run started
     */
    private void flushText(StringBuilder buf, int textLine, int textCol) {
        if (!buf.isEmpty()) {
            emit(new Token(TokenKind.TEXT, buf.toString(), textLine, textCol));
            buf.setLength(0);
        }
    }

    /**
     * @return true when the cursor is at the start of a {@code <!--} comment.
     */
    private boolean isCommentStart() {
        return matchesAt(pos, "<!--");
    }

    /**
     * Skips a {@code <!-- ... -->} comment including its delimiters.
     *
     * @throws MarkupException when the comment is never closed
     */
    private void skipComment() {
        int startLine = line;
        int startCol = column;
        // Consume "<!--"
        for (int i = 0; i < 4 && pos < src.length(); i++) {
            advance();
        }
        while (pos < src.length()) {
            if (matchesAt(pos, "-->")) {
                for (int i = 0; i < 3; i++) {
                    advance();
                }
                return;
            }
            advance();
        }
        throw new MarkupException("unterminated comment opened here",
                startLine, startCol);
    }

    // ------------------------------------------------------------------
    // TAG mode
    // ------------------------------------------------------------------

    /**
     * Scans tokens inside a tag: name, attributes, values and the closing
     * {@code >} / {@code />}. Whitespace is skipped.
     */
    private void scanTag() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (isWhitespace(c)) {
                advance();
                continue;
            }
            if (c == '>') {
                int l = line;
                int col = column;
                advance();
                emit(new Token(TokenKind.CLOSE_TAG, ">", l, col));
                mode = Mode.CONTENT;
                return;
            }
            if (c == '/' && peekAt(1) == '>') {
                int l = line;
                int col = column;
                advance();
                advance();
                emit(new Token(TokenKind.CLOSE_SELF_TAG, "/>", l, col));
                mode = Mode.CONTENT;
                return;
            }
            if (c == '=') {
                int l = line;
                int col = column;
                // Look ahead past the '=' and any whitespace: "name=" followed
                // by '>' or '/>' (or EOF) has no usable value. In that case an
                // ERROR token is emitted anchored at the '=' itself, which is
                // exactly where the missing value should have been.
                int probe = pos + 1;
                while (probe < src.length() && isWhitespace(src.charAt(probe))) {
                    probe++;
                }
                boolean noValue = probe >= src.length()
                        || src.charAt(probe) == '>'
                        || (src.charAt(probe) == '/'
                            && probe + 1 < src.length()
                            && src.charAt(probe + 1) == '>');
                if (noValue) {
                    // Emit an ERROR token anchored exactly at the '=' position
                    // (where the value should have been) instead of a plain
                    // EQUALS. The parser forwards it as "attribute 'x' is
                    // missing a value" with the correct line/column. Consuming
                    // the '=' here also guarantees forward progress.
                    advance();
                    emit(new Token(TokenKind.ERROR,
                            "attribute value missing after '='", l, col));
                    continue;
                }
                advance();                        // consume '='
                emit(new Token(TokenKind.EQUALS, "=", l, col));
                expectValue = true;
                continue;
            }
            if (c == '"') {
                // A quoted string is only legal as an attribute value; the
                // parser relies on STRING appearing exclusively after EQUALS.
                Token str = lexString();
                emit(new Token(TokenKind.STRING, str.value(), str.line(), str.column()));
                expectValue = false;
                continue;
            }
            if (isIdentStart(c) || (expectValue && isBareValueStart(c))) {
                int l = line;
                int col = column;
                StringBuilder word = new StringBuilder();
                if (expectValue) {
                    while (pos < src.length() && isBareValuePart(src.charAt(pos))) {
                        word.append(src.charAt(pos));
                        advance();
                    }
                    emit(new Token(TokenKind.BARE_VALUE, word.toString(), l, col));
                    expectValue = false;
                } else {
                    while (pos < src.length() && isNamePart(src.charAt(pos))) {
                        word.append(src.charAt(pos));
                        advance();
                    }
                    emit(new Token(expectTagName ? TokenKind.TAG_NAME : TokenKind.ATTR_NAME,
                            word.toString(), l, col));
                    expectTagName = false;
                }
                continue;
            }
            Token err = new Token(TokenKind.ERROR,
                    "illegal character '" + c + "' in tag", line, column);
            advance();
            emit(err);
        }
        // EOF handled by tokenize().
    }

    /**
     * Lexes a double-quoted string. Backslash escapes {@code \"} and
     * {@code \\} are resolved; the token value excludes the quotes.
     *
     * @return the STRING token
     * @throws MarkupException on an unterminated string
     */
    private Token lexString() {
        int l = line;
        int col = column;
        advance(); // opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\\' && (peekAt(1) == '"' || peekAt(1) == '\\')) {
                advance();
                sb.append(src.charAt(pos));
                advance();
                continue;
            }
            if (c == '"') {
                advance(); // closing quote
                return new Token(TokenKind.STRING, sb.toString(), l, col);
            }
            sb.append(c);
            advance();
        }
        throw new MarkupException("unterminated string opened here", l, col);
    }

    // ------------------------------------------------------------------
    // BINDING mode
    // ------------------------------------------------------------------

    /**
     * Scans a {@code {ident.ident}} path. Only identifiers, dots and the
     * closing brace are legal; anything else yields an ERROR token with a
     * positioned message and returns to CONTENT mode after skipping ahead to
     * the next plausible boundary (the closing brace or end of input), so a
     * single bad expression does not cascade into dozens of bogus tokens.
     */
    private void scanBinding() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (isWhitespace(c)) {
                advance();
                continue;
            }
            if (c == '.') {
                int l = line;
                int col = column;
                advance();
                emit(new Token(TokenKind.DOT, ".", l, col));
                continue;
            }
            if (c == '}') {
                int l = line;
                int col = column;
                advance();
                emit(new Token(TokenKind.BINDING_CLOSE, "}", l, col));
                mode = Mode.CONTENT;
                return;
            }
            if (isIdentStart(c)) {
                int l = line;
                int col = column;
                StringBuilder sb = new StringBuilder();
                while (pos < src.length() && isIdentPart(src.charAt(pos))) {
                    sb.append(src.charAt(pos));
                    advance();
                }
                emit(new Token(TokenKind.IDENT, sb.toString(), l, col));
                continue;
            }
            // Anything else (!, &, >, (, digit, quote, ...) is illegal here.
            emit(new Token(TokenKind.ERROR,
                    "invalid character '" + c + "' not allowed in binding path;"
                    + " expected {identifier.property}", line, column));
            // Recover: skip to the closing brace so the rest of the document
            // can still be tokenized.
            while (pos < src.length() && src.charAt(pos) != '}') {
                advance();
            }
            if (pos < src.length()) {
                int l = line;
                int col = column;
                advance();
                emit(new Token(TokenKind.BINDING_CLOSE, "}", l, col));
            } else {
                throw new MarkupException(
                        "unexpected end of input inside binding opened here",
                        bindingOpenLine, bindingOpenColumn);
            }
            mode = Mode.CONTENT;
            return;
        }
        // EOF handled by tokenize().
    }

    // ------------------------------------------------------------------
    // Character helpers and cursor primitives
    // ------------------------------------------------------------------

    private void emit(Token t) {
        tokens.add(t);
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private char peekAt(int offset) {
        int i = pos + offset;
        return i < src.length() ? src.charAt(i) : '\0';
    }

    private boolean matchesAt(int index, String literal) {
        if (index + literal.length() > src.length()) {
            return false;
        }
        for (int i = 0; i < literal.length(); i++) {
            if (src.charAt(index + i) != literal.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Advances the cursor by one character, maintaining line/column tracking
     * (a {@code \n} starts a new line; {@code \r\n} counts as one newline).
     */
    private void advance() {
        char c = src.charAt(pos);
        pos++;
        if (c == '\n') {
            line++;
            column = 1;
        } else if (c == '\r') {
            if (pos < src.length() && src.charAt(pos) == '\n') {
                pos++;
            }
            line++;
            column = 1;
        } else {
            column++;
        }
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    /** Identifier start: {@code [A-Za-z_]}. */
    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    /** Identifier continuation: {@code [A-Za-z0-9_]}. */
    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }

    /** Name part in TAG mode: identifiers plus {@code :} and {@code -}. */
    private static boolean isNamePart(char c) {
        return isIdentPart(c) || c == ':' || c == '-';
    }

    /** First character of a bare attribute value. */
    private static boolean isBareValueStart(char c) {
        return isNamePart(c) || (c >= '0' && c <= '9');
    }

    /** Continuation character of a bare attribute value. */
    private static boolean isBareValuePart(char c) {
        return isBareValueStart(c) || c == '.' || c == ',' || c == '#' || c == '%'
                || c == '(' || c == ')' || isBareValueSpecialUnicode(c);
    }

    private static boolean isBareValueSpecialUnicode(char c) {
        // Allow any non-ASCII printable character inside bare values so that
        // localized literals keep working without quoting.
        return c > 0x7F && !Character.isISOControl(c);
    }
}
