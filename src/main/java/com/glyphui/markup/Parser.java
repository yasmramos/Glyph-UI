package com.glyphui.markup;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser that turns a {@code .glyph} source string into a
 * {@link Document} AST via the token stream of {@link Tokenizer}.
 *
 * <p><b>Error strategy (see also {@link MarkupException}):</b></p>
 * <ul>
 *   <li>Recoverable structural problems — unclosed tag, closing tag without
 *       matching open tag, mismatched end-tag name, attribute without value,
 *       duplicate attribute, stray {@code &#125;} in text, illegal binding
 *       expression — are accumulated as {@link ParseError} entries and parsing
 *       continues, so one run reports every issue.</li>
 *   <li>Unrecoverable lexer conditions (unterminated comment/string, EOF
 *       inside a tag or binding) propagate as {@link MarkupException}.</li>
 * </ul>
 *
 * <p>The parser is intentionally "dumb": it never interprets attribute
 * prefixes ({@code bind:}, {@code on:}) or control attributes ({@code if},
 * {@code for-each}); it only builds the raw tree. Text content keeps every
 * TEXT/BINDING/element child in source order, including whitespace-only text
 * runs, so later phases can decide what to trim.</p>
 */
public final class Parser {

    /** Token stream being consumed. */
    private List<Token> tokens;

    /** Cursor into {@link #tokens}. */
    private int index;

    /** Accumulated recoverable diagnostics. */
    private final List<ParseError> errors = new ArrayList<>();

    /**
     * Tag names of the elements currently being parsed, innermost last. Used
     * only to distinguish a stray close tag (name owned by an enclosing
     * level) from a genuine mismatch during error recovery.
     */
    private final List<String> openStack = new ArrayList<>();

    /**
     * Parses a complete document.
     *
     * @param source the {@code .glyph} markup text (null → empty document)
     * @return the parse result with the AST and all recoverable errors
     * @throws MarkupException on unrecoverable lexical problems
     */
    public ParseResult parse(String source) {
        tokens = new Tokenizer(source).tokenize();
        index = 0;
        errors.clear();
        openStack.clear();
        lastCloseLine = -1;
        lastCloseColumn = -1;
        Document doc = new Document(peek().line(), peek().column());
        // The sentinel "" is owned by no real tag, so every end tag at
        // document level is either matched or reported explicitly.
        openStack.add("");
        try {
            parseContentInto(doc::addChild, null);
        } finally {
            openStack.remove(openStack.size() - 1);
        }
        return new ParseResult(doc, errors);
    }

    // ------------------------------------------------------------------
    // Content
    // ------------------------------------------------------------------

    /**
     * Parses nodes until the matching close tag of {@code openName} (or EOF
     * at document level) and feeds them to {@code sink}.
     *
     * @param sink      consumer for produced nodes
     * @param openName  element whose close tag terminates the loop, or null
     *                  at document level
     */
    private void parseContentInto(java.util.function.Consumer<AstNode> sink,
                                  String openName) {
        while (true) {
            Token t = peek();
            switch (t.kind()) {
                case EOF -> {
                    if (openName != null) {
                        error("unclosed tag <" + openName + ">", t);
                    }
                    return;
                }
                case OPEN_CLOSE_TAG -> {
                    Token nameTok = peek(1);
                    if (openName == null) {
                        error("closing tag '" + closeTagName()
                                + "' has no open element", t);
                        skipEndTag();
                        continue;
                    }
                    if (!isMatchingCloseTag(openName)) {
                        boolean ownedOuter = nameTok.kind() == TokenKind.TAG_NAME
                                && !openStack.contains(nameTok.value());
                        if (ownedOuter) {
                            // The name belongs to an enclosing level (or the
                            // document): treat it as a stray close here,
                            // report it and keep parsing this element instead
                            // of cascading mismatches up the stack.
                            error("closing tag '</" + nameTok.value()
                                    + "' has no open element inside <"
                                    + openName + ">", t);
                            skipEndTag();
                            continue;
                        }
                        // Unknown at every outer level: mismatch against the
                        // expected end tag; let the caller handle it.
                        error("mismatched closing tag, expected </" + openName
                                + ">, found </" + closeTagName() + ">", t);
                        return;
                    }
                    consumeCloseTagFor(openName);
                    return;
                }
                case OPEN_TAG -> sink.accept(parseElement());
                case TEXT -> {
                    // Whitespace-only runs between tags are formatting, not
                    // content: drop them so the tree keeps meaningful children
                    // in source order.
                    if (!t.value().isBlank()) {
                        sink.accept(new TextNode(t.value(), t.line(), t.column()));
                    }
                    advance();
                }
                case BINDING_OPEN -> {
                    BindingNode binding = parseBinding();
                    sink.accept(binding);
                    splitTextAfterBinding(sink, binding);
                }
                case ERROR -> {
                    error(t.value(), t);
                    advance();
                }
                default -> {
                    error("unexpected token " + t.kind(), t);
                    advance();
                }
            }
        }
    }

    /**
     * Consumes a complete end tag ({@code </name>}) from the cursor, reporting
     * (but recovering from) missing names or the final {@code >}.
     */
    private void skipEndTag() {
        advance();                       // '</'
        if (peek().kind() == TokenKind.TAG_NAME) {
            advance();
        }
        if (peek().kind() == TokenKind.CLOSE_TAG) {
            advance();
        }
    }

    /**
     * Looks ahead without consuming to decide whether the closing tag at the
     * cursor carries the expected name.
     *
     * @param openName the element whose end tag we are waiting for
     * @return true when the stream position is {@code </openName>}
     */
    private boolean isMatchingCloseTag(String openName) {
        Token name = peek(1);
        return name.kind() == TokenKind.TAG_NAME && name.value().equals(openName);
    }

    /**
     * Consumes {@code </name>} and records its position for the enclosing
     * element (read back by {@link #parseElement()} through
     * {@link #lastCloseLine}). The recorded position anchors on the end-tag
     * <em>name</em>, which is what diagnostics should point at.
     *
     * @param openName the expected tag name (already validated by the caller)
     */
    private void consumeCloseTagFor(String openName) {
        advance();                       // '</'
        Token name = peek();
        if (name.kind() == TokenKind.TAG_NAME) {
            advance();
        } else {
            error("missing tag name in closing tag", name);
        }
        if (peek().kind() == TokenKind.CLOSE_TAG) {
            advance();
        } else if (peek().kind() != TokenKind.EOF) {
            error("expected '>' to close end tag", peek());
            advance();
        }
        // Anchor the recorded position at the tag name, not the "</".
        lastCloseLine = name.line();
        lastCloseColumn = name.column();
    }

    /** Position of the most recently consumed close tag (set by parseElement). */
    private int lastCloseLine = -1;
    private int lastCloseColumn = -1;

    /** Position of the '}' consumed by the most recent parseBinding() call. */
    private int bindingCloseLine = -1;
    private int bindingCloseColumn = -1;

    /**
     * Parses one element: start tag, attributes, children and matching end
     * tag (or the self-closing {@code />} form).
     *
     * @return the constructed element
     */
    private Element parseElement() {
        Token lt = peek();
        int elLine = lt.line();
        int elCol = lt.column();
        advance();                       // '<'
        Token nameTok = expect(TokenKind.TAG_NAME, "expected tag name after '<'");
        if (nameTok == null) {
            // Could not recover a name: skip junk until the next boundary.
            skipToTagBoundary();
            return new Element("unknown", true, elLine, elCol);
        }
        Element element = new Element(nameTok.value(), false, elLine, elCol);
        int selfCloseLine = -1;
        int selfCloseColumn = -1;

        // Attributes until '>' or '/>'.
        List<String> seenNames = new ArrayList<>();
        boolean selfClosing = false;
        while (true) {
            Token t = peek();
            switch (t.kind()) {
                case CLOSE_TAG -> advance();
                case CLOSE_SELF_TAG -> {
                    selfCloseLine = t.line();
                    selfCloseColumn = t.column();
                    advance();
                    selfClosing = true;
                }
                case EOF -> {
                    error("unclosed tag <" + element.getTagName() + ">", t);
                    return element;
                }
                case ATTR_NAME -> {
                    readAttribute(element, seenNames);
                    continue;
                }
                case ERROR -> {
                    error(t.value(), t);
                    advance();
                    continue;
                }
                default -> {
                    error("unexpected token " + t.kind() + " inside tag <"
                            + element.getTagName() + ">", t);
                    advance();
                    continue;
                }
            }
            break; // CLOSE_TAG or CLOSE_SELF_TAG consumed
        }

        if (selfClosing) {
            element.setSelfClosing(true);
            // Anchor the close position at the "/>" that ended the start tag.
            element.setClosePosition(selfCloseLine, selfCloseColumn);
            return element;
        }

        lastCloseLine = -1;
        lastCloseColumn = -1;
        openStack.add(element.getTagName());
        try {
            parseContentInto(node -> element.addChild(node), element.getTagName());
        } finally {
            openStack.remove(openStack.size() - 1);
        }
        if (lastCloseLine > 0) {
            element.setClosePosition(lastCloseLine, lastCloseColumn);
        }
        return element;
    }

    /**
     * Reads one attribute (name, optional {@code =} value) and appends it to
     * the element, reporting missing values and duplicates.
     */
    private void readAttribute(Element element, List<String> seenNames) {
        Token nameTok = peek();
        String name = nameTok.value();
        if (seenNames.contains(name)) {
            error("duplicate attribute '" + name + "' on element <"
                    + element.getTagName() + ">", nameTok);
        } else {
            seenNames.add(name);
        }
        advance();
        String value = null;
        boolean quoted = false;
        // An attribute without '=' is a valid presence-only flag (e.g. the
        // bare "else" directive); only "name=" with nothing after it is an
        // actual missing-value error.
        if (peek().kind() == TokenKind.EQUALS) {
            Token eq = peek();
            advance();
            Token v = peek();
            if (v.kind() == TokenKind.STRING) {
                value = v.value();
                quoted = true;
                advance();
            } else if (v.kind() == TokenKind.BARE_VALUE) {
                value = v.value();
                advance();
            } else {
                // Anchor at the offending token (where the value should be),
                // not at the '=' sign itself.
                error("attribute '" + name + "' is missing a value", v);
            }
        } else if (peek().kind() == TokenKind.ERROR
                && peek().value().startsWith("attribute value missing")) {
            // The tokenizer detected "name=" immediately followed by '>' or
            // '/>' and flagged the exact '=' position; forward it here so the
            // message names the attribute.
            Token eq = peek();
            error("attribute '" + name + "' is missing a value", eq);
            advance();
        }
        element.addAttribute(new Attribute(name, value, quoted,
                nameTok.line(), nameTok.column()));
    }

    /**
     * Parses a {@code &#123;path&#125;} interpolation already split by the lexer.
     * Validates the shape: IDENT (DOT IDENT)* followed by BINDING_CLOSE.
     *
     * @return the binding node
     */
    private BindingNode parseBinding() {
        Token open = peek();
        advance();                       // '{'
        List<String> segments = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        boolean expectIdent = true;      // path grammar: IDENT (DOT IDENT)*
        while (true) {
            Token t = peek();
            if (t.kind() == TokenKind.IDENT) {
                if (!expectIdent) {
                    error("expected '.' between path segments", t);
                }
                segments.add(t.value());
                raw.append(t.value());
                expectIdent = false;     // next must be DOT or CLOSE
                advance();
            } else if (t.kind() == TokenKind.DOT) {
                if (expectIdent) {
                    error("'.' must separate two identifiers in binding path", t);
                }
                raw.append('.');
                expectIdent = true;      // a dot must be followed by an IDENT
                advance();
            } else if (t.kind() == TokenKind.BINDING_CLOSE) {
                advance();
                bindingCloseLine = t.line();
                bindingCloseColumn = t.column();
                break;
            } else if (t.kind() == TokenKind.ERROR) {
                error(t.value(), t);
                advance();
            } else if (t.kind() == TokenKind.EOF) {
                error("unterminated binding", t);
                break;
            } else {
                error("unexpected token " + t.kind() + " in binding path", t);
                advance();
            }
        }
        if (segments.isEmpty()) {
            error("empty binding path; expected {identifier.property}", open);
            segments.add("");
        }
        BindingNode node = new BindingNode(segments, raw.toString(), open.line(), open.column());
        node.setClosePosition(bindingCloseLine, bindingCloseColumn);
        return node;
    }

    /**
     * Splits a TEXT token that starts before the just-parsed {@code &#123;...&#125;}
     * and continues after its closing brace. The tokenizer folds the whole
     * literal run (with escapes resolved) into one token anchored at the run
     * start; this method turns the portion after '}' into its own TextNode so
     * callers see precise positions:
     * {@code <Label>Hola {user.name}!</Label>} yields Text("Hola ", col 8),
     * Binding(col 13), Text("!", col 27).
     *
     * @param sink    consumer for produced nodes
     * @param binding the binding just emitted (its position + raw path define
     *                where the remainder of the text run starts)
     */
    private void splitTextAfterBinding(
            java.util.function.Consumer<AstNode> sink, BindingNode binding) {
        Token t = peek();
        if (t.kind() != TokenKind.TEXT) {
            return;
        }
        // The closing-brace position recorded by parseBinding() gives the
        // exact column right after '}'; when it is unavailable (error
        // recovery paths), leave the text run untouched.
        int afterBrace = -1;
        if (binding.getCloseColumn() > 0 && binding.getCloseLine() == t.line()) {
            afterBrace = binding.getCloseColumn() + 1;
        } else if (binding.getCloseColumn() > 0 && binding.getCloseLine() < t.line()) {
            // Multi-line run: the remainder starts at column 1 of its line.
            afterBrace = 1;
        }
        if (afterBrace <= 0) {
            return;
        }
        int offset = t.column() >= afterBrace ? t.column() - afterBrace : 0;
        if (offset > 0 && offset < t.value().length()) {
            advance();
            String tail = t.value().substring(offset);
            sink.accept(new TextNode(tail, t.line(), afterBrace));
            // Keep any remaining prefix-less content flowing normally: the
            // consumed token is fully handled here.
        }
    }

    // ------------------------------------------------------------------
    // Token cursor helpers
    // ------------------------------------------------------------------

    private Token peek() {
        return tokens.get(index);
    }

    private Token peek(int ahead) {
        int i = Math.min(index + ahead, tokens.size() - 1);
        return tokens.get(i);
    }

    private Token advance() {
        Token t = tokens.get(index);
        if (index < tokens.size() - 1) {
            index++;
        }
        return t;
    }

    private boolean isAtEnd() {
        return peek().kind() == TokenKind.EOF;
    }

    /**
     * Consumes the next token if it has the expected kind.
     *
     * @param kind    expected kind
     * @param message diagnostic when the token differs
     * @return the matched token, or null (error recorded) when it does not match
     */
    private Token expect(TokenKind kind, String message) {
        Token t = peek();
        if (t.kind() == kind) {
            advance();
            return t;
        }
        error(message + ", found " + t.kind(), t);
        return null;
    }

    /** Skips tokens until the start of the next tag or EOF (panic mode). */
    private void skipToTagBoundary() {
        while (!isAtEnd() && peek().kind() != TokenKind.OPEN_TAG
                && peek().kind() != TokenKind.OPEN_CLOSE_TAG) {
            advance();
        }
    }

    /**
     * Reads (without consuming) the tag name that follows the {@code </} at
     * the cursor, for diagnostic messages.
     *
     * @return the end-tag name, or "?" when no TAG_NAME token follows
     */
    private String closeTagName() {
        Token name = peek(1);
        return name.kind() == TokenKind.TAG_NAME ? name.value() : "?";
    }

    /**
     * Records a recoverable diagnostic.
     *
     * @param message description
     * @param at      token whose position anchors the diagnostic
     */
    private void error(String message, Token at) {
        errors.add(new ParseError(message, at.line(), at.column()));
    }
}
