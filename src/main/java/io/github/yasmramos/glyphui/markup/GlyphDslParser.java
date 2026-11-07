package io.github.yasmramos.glyphui.markup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recursive-descent parser for the Compose-style {@code .glyph} DSL, the
 * non-XML front-end of the same declarative pipeline.
 *
 * <p>Where {@link Parser} reads angle-bracket markup ({@code <Button/>}), this
 * parser reads a call-and-block syntax that mirrors Kotlin UI DSLs:</p>
 *
 * <pre>{@code
 * Stylesheet("app.css")
 *
 * Column(id = "root", class = "page", gap = 8, padding = 24) {
 *     Text("Glyph-UI", class = "title")
 *     Row(class = "card", gap = 8) {
 *         TextField(id = "name", placeholder = "Your name")
 *         Button("Greet", onClick = sayHello)
 *     }
 * }
 * }</pre>
 *
 * <h2>Grammar</h2>
 * <pre>
 * document   := trivia* ( statement trivia* )*
 * statement  := identifier '(' arguments ')' ( '{' statement* '}' )?
 * arguments  := ( argument ( ',' argument )* )?
 * argument   := identifier '=' value | value
 * value      := string | bare | binding
 * bare       := run of value characters (numbers, {@code #hex}, keywords)
 * binding    := '{' identifier ( '.' identifier )* '}'
 * </pre>
 *
 * <h2>Mapping to the raw AST</h2>
 * <ul>
 *   <li>a statement becomes an {@link Element} named after the component;</li>
 *   <li>a positional {@code string}/{@code bare} becomes a {@link TextNode}
 *       child (the widget's text), a positional {@code {path}} a
 *       {@link BindingNode} child;</li>
 *   <li>a named argument becomes an {@link Attribute}. The name is normalised
 *       from camelCase to the loader's canonical spelling
 *       ({@code onClick} → {@code onclick}, {@code fontSize} →
 *       {@code font-size}), {@code = true} becomes a valueless presence flag
 *       and {@code = false} is dropped;</li>
 *   <li>{@code Stylesheet("app.css")} becomes a {@code <link rel="stylesheet">}
 *       element so the existing stylesheet extraction applies unchanged.</li>
 * </ul>
 *
 * <p>Producing the same {@link Document} shape means {@link UiLoader}, the CSS
 * engine and the widget vocabulary need no knowledge of which front-end ran.</p>
 *
 * <h2>Error strategy</h2>
 * Follows the XML pipeline: recoverable structural problems (missing comma,
 * missing parenthesis, empty binding) are collected as {@link ParseError}s so
 * one pass reports every issue, while an unterminated string aborts with a
 * {@link MarkupException}. No regular expressions or parsing libraries are
 * used: every decision is an explicit character comparison.
 */
public final class GlyphDslParser {

    /** Source text being parsed. */
    private final String src;

    /** Current read offset. */
    private int pos;

    /** 1-based line of the current offset. */
    private int line = 1;

    /** 1-based column of the current offset. */
    private int column = 1;

    /** Accumulated recoverable diagnostics. */
    private final List<ParseError> errors = new ArrayList<>();

    private GlyphDslParser(String source) {
        this.src = source == null ? "" : source;
    }

    /**
     * Parses a {@code .glyph} DSL document.
     *
     * @param source the DSL text (null → empty document)
     * @return the parse result with the raw AST and every recoverable error
     * @throws MarkupException on an unterminated string
     */
    public static ParseResult parse(String source) {
        return new GlyphDslParser(source).run();
    }

    /**
     * Decides whether a document should be routed to this parser rather than
     * the XML one: any source whose first non-whitespace character is not
     * {@code &lt;} is treated as DSL. Empty input is not DSL (the XML parser
     * yields an empty document for it).
     *
     * @param source the raw document text (may be null)
     * @return true when the text should be parsed as the DSL
     */
    public static boolean looksLikeDsl(String source) {
        if (source == null) {
            return false;
        }
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c != '<';
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Document and statements
    // ------------------------------------------------------------------

    private ParseResult run() {
        Document doc = new Document(1, 1);
        skipTrivia();
        while (!atEnd()) {
            Element element = parseStatement();
            if (element != null) {
                doc.addChild(element);
            }
            skipTrivia();
        }
        AstNode.linkParents(doc);
        return new ParseResult(doc, errors);
    }

    /**
     * Parses one component statement: name, argument list and optional block.
     *
     * @return the element, or null when the statement was unrecoverable
     */
    private Element parseStatement() {
        skipTrivia();
        int l = line;
        int c = column;
        if (!isIdentStart(peek())) {
            error("expected a component name", l, c);
            advance();
            return null;
        }
        String name = readIdentifier();
        skipTrivia();
        if (peek() != '(') {
            // Trailing-lambda form: a container may omit the argument list
            // entirely, e.g. `Column { ... }`, matching Compose's DSL.
            if (peek() == '{') {
                Element element = new Element(name, false, l, c);
                advance(); // '{'
                parseBlock(element);
                return element;
            }
            error("expected '(' or '{' after '" + name + "'", line, column);
            return null;
        }
        advance(); // '('

        if ("stylesheet".equals(name.toLowerCase(Locale.ROOT))) {
            return parseStylesheetHeader(l, c);
        }

        Element element = new Element(name, false, l, c);
        parseArguments(element);
        skipTrivia();
        if (peek() == '{') {
            advance(); // '{'
            parseBlock(element);
        } else {
            element.setSelfClosing(true);
            element.setClosePosition(line, column);
        }
        return element;
    }

    /** Parses the trailing {@code }) of a Stylesheet(...) header into a link. */
    private Element parseStylesheetHeader(int l, int c) {
        skipTrivia();
        String href = "";
        if (peek() == '"') {
            href = readString();
        } else {
            error("Stylesheet(...) expects a quoted path", line, column);
            skipToArgumentEnd();
        }
        skipTrivia();
        if (peek() == ')') {
            advance();
        } else if (!atEnd()) {
            error("expected ')' to close Stylesheet(...)", line, column);
            skipToArgumentEnd();
        }
        Element link = new Element("link", true, l, c);
        link.addAttribute(new Attribute("rel", "stylesheet", true, l, c));
        if (!href.isEmpty()) {
            link.addAttribute(new Attribute("href", href, true, l, c));
        }
        link.setClosePosition(line, column);
        return link;
    }

    /** Parses {@code (arg, arg, ...)} up to and including the closing ')'. */
    private void parseArguments(Element element) {
        skipTrivia();
        if (peek() == ')') {
            advance();
            return;
        }
        while (!atEnd()) {
            skipTrivia();
            int l = line;
            int c = column;
            char ch = peek();
            if (isIdentStart(ch)) {
                String ident = readIdentifier();
                skipTrivia();
                if (peek() == '=') {
                    advance(); // '='
                    skipTrivia();
                    parseNamedValue(element, ident, l, c);
                } else {
                    // Bare positional word: treated as the widget's text.
                    element.addChild(new TextNode(ident, l, c));
                }
            } else if (ch == '"') {
                element.addChild(new TextNode(readString(), l, c));
            } else if (ch == '{') {
                element.addChild(readBinding());
            } else if (ch == ')') {
                advance();
                return;
            } else if (isBareValueStart(ch)) {
                element.addChild(new TextNode(readBareValue(), l, c));
            } else {
                error("unexpected character '" + ch + "' in argument list", l, c);
                advance();
            }

            skipTrivia();
            if (peek() == ',') {
                advance();
                continue;
            }
            if (peek() == ')') {
                advance();
                return;
            }
            if (atEnd()) {
                error("unclosed argument list; expected ')'", line, column);
                return;
            }
            error("expected ',' or ')' in argument list", line, column);
            skipToArgumentEnd();
            return;
        }
    }

    /** Parses {@code name = value} after the '=' has been consumed. */
    private void parseNamedValue(Element element, String rawName, int l, int c) {
        String name = normalizeName(rawName);
        skipTrivia();
        char ch = peek();
        if (ch == '"') {
            element.addAttribute(new Attribute(name, readString(), true, l, c));
            return;
        }
        if (ch == '{') {
            // The raw Attribute model holds a String value, so a binding cannot
            // be attached to an attribute yet; report it instead of silently
            // dropping the value.
            BindingNode binding = readBinding();
            error("binding {" + binding.getRawPath()
                    + "} is not supported as an attribute value; use it as content",
                    l, c);
            return;
        }
        if (isBareValueStart(ch)) {
            String raw = readBareValue();
            if ("true".equals(raw)) {
                // Presence flag: e.g. indeterminate = true.
                element.addAttribute(new Attribute(name, null, false, l, c));
            } else if (!"false".equals(raw)) {
                element.addAttribute(new Attribute(name, raw, false, l, c));
            }
            return;
        }
        error("expected a value after '" + rawName + "='", line, column);
        advance();
    }

    /** Parses {@code { child statements }} up to and including the '}'. */
    private void parseBlock(Element element) {
        while (true) {
            skipTrivia();
            if (atEnd()) {
                error("unclosed block for '" + element.getTagName() + "'",
                        line, column);
                return;
            }
            if (peek() == '}') {
                advance();
                element.setClosePosition(line, column);
                return;
            }
            Element child = parseStatement();
            if (child != null) {
                element.addChild(child);
            }
        }
    }

    // ------------------------------------------------------------------
    // Leaves
    // ------------------------------------------------------------------

    /** Parses {@code {identifier(.identifier)*}} into a binding node. */
    private BindingNode readBinding() {
        int l = line;
        int c = column;
        advance(); // '{'
        List<String> segments = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        boolean expectIdent = true;
        int closeLine = l;
        int closeCol = c;
        while (!atEnd()) {
            skipTrivia();
            char ch = peek();
            if (ch == '}') {
                closeLine = line;
                closeCol = column;
                advance();
                break;
            }
            if (ch == '.') {
                if (expectIdent) {
                    error("'.' must separate two identifiers in a binding",
                            line, column);
                }
                raw.append('.');
                expectIdent = true;
                advance();
                continue;
            }
            if (isIdentStart(ch)) {
                if (!expectIdent) {
                    error("expected '.' between binding segments", line, column);
                }
                String ident = readIdentifier();
                segments.add(ident);
                raw.append(ident);
                expectIdent = false;
                continue;
            }
            error("invalid character '" + ch + "' in binding path", line, column);
            advance();
        }
        if (segments.isEmpty()) {
            error("empty binding; expected {identifier.property}", l, c);
            segments.add("");
        }
        BindingNode node = new BindingNode(segments, raw.toString(), l, c);
        node.setClosePosition(closeLine, closeCol);
        return node;
    }

    // ------------------------------------------------------------------
    // Trivia and lexical helpers
    // ------------------------------------------------------------------

    /** Skips whitespace, {@code //} line comments and {@code /* *}{@code /} blocks. */
    private void skipTrivia() {
        while (!atEnd()) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                advance();
                continue;
            }
            if (c == '/' && peekAt(1) == '/') {
                while (!atEnd() && peek() != '\n') {
                    advance();
                }
                continue;
            }
            if (c == '/' && peekAt(1) == '*') {
                int sl = line;
                int sc = column;
                advance();
                advance();
                boolean closed = false;
                while (!atEnd()) {
                    if (peek() == '*' && peekAt(1) == '/') {
                        advance();
                        advance();
                        closed = true;
                        break;
                    }
                    advance();
                }
                if (!closed) {
                    error("unterminated block comment", sl, sc);
                }
                continue;
            }
            return;
        }
    }

    private String readIdentifier() {
        int start = pos;
        while (!atEnd() && isIdentPart(peek())) {
            advance();
        }
        return src.substring(start, pos);
    }

    /** Lexes a double-quoted string, resolving {@code \"} and {@code \\}. */
    private String readString() {
        int l = line;
        int c = column;
        advance(); // opening quote
        StringBuilder sb = new StringBuilder();
        while (!atEnd()) {
            char ch = peek();
            if (ch == '\\' && (peekAt(1) == '"' || peekAt(1) == '\\')) {
                advance();
                sb.append(peek());
                advance();
                continue;
            }
            if (ch == '"') {
                advance();
                return sb.toString();
            }
            sb.append(ch);
            advance();
        }
        throw new MarkupException("unterminated string", l, c);
    }

    /** Lexes a run of bare value characters (numbers, {@code #hex}, keywords). */
    private String readBareValue() {
        int start = pos;
        while (!atEnd() && isBareValuePart(peek())) {
            advance();
        }
        return src.substring(start, pos);
    }

    /** Skips to just past the next ',' or ')', used for error recovery. */
    private void skipToArgumentEnd() {
        while (!atEnd() && peek() != ',' && peek() != ')') {
            advance();
        }
        if (!atEnd()) {
            advance();
        }
    }

    /**
     * Normalises a DSL argument name to the loader's canonical spelling:
     * {@code onXxx} collapses to {@code onxxx} (event handlers) and any other
     * camelCase becomes kebab-case ({@code fontSize} → {@code font-size}).
     *
     * @param raw the name as written in the DSL
     * @return the canonical attribute name
     */
    static String normalizeName(String raw) {
        if (raw.length() > 2 && raw.charAt(0) == 'o' && raw.charAt(1) == 'n'
                && Character.isUpperCase(raw.charAt(2))) {
            return "on" + raw.substring(2).toLowerCase(Locale.ROOT);
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void error(String message, int l, int c) {
        errors.add(new ParseError(message, l, c));
    }

    private boolean atEnd() {
        return pos >= src.length();
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private char peekAt(int offset) {
        int i = pos + offset;
        return i < src.length() ? src.charAt(i) : '\0';
    }

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

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }

    private static boolean isBareValueStart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9')
                || c == '-' || c == '+' || c == '#' || c == '.';
    }

    private static boolean isBareValuePart(char c) {
        return isBareValueStart(c) || c == '%' || c == '/' || c == ':'
                || (c > 0x7F && !Character.isISOControl(c));
    }
}
