package com.glyphui.markup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Tokenizer}: one nested group per scanner mode plus
 * escape handling, position tracking and error recovery.
 */
class TokenizerTest {

    /** Convenience: tokenize and return the stream without the trailing EOF. */
    private static List<Token> toks(String src) {
        List<Token> all = new Tokenizer(src).tokenize();
        return all.subList(0, all.size() - 1);
    }

    private static List<TokenKind> kinds(String src) {
        return toks(src).stream().map(Token::kind).toList();
    }

    @Nested
    @DisplayName("CONTENT mode")
    class ContentMode {

        @Test
        @DisplayName("plain text becomes a single TEXT token")
        void plainText() {
            List<Token> t = toks("Hello world");
            assertEquals(1, t.size());
            assertEquals(TokenKind.TEXT, t.get(0).kind());
            assertEquals("Hello world", t.get(0).value());
            assertEquals(1, t.get(0).line());
            assertEquals(1, t.get(0).column());
        }

        @Test
        @DisplayName("'<' opens a tag, '</' opens a closing tag")
        void tagOpeners() {
            assertEquals(List.of(TokenKind.OPEN_TAG, TokenKind.TAG_NAME,
                            TokenKind.CLOSE_TAG),
                    kinds("<Box>"));
            assertEquals(List.of(TokenKind.OPEN_TAG, TokenKind.TAG_NAME,
                            TokenKind.CLOSE_TAG,
                            TokenKind.OPEN_CLOSE_TAG, TokenKind.TAG_NAME,
                            TokenKind.CLOSE_TAG),
                    kinds("<Box></Box>"));
        }

        @Test
        @DisplayName("text around a tag yields TEXT tokens on both sides")
        void textAroundTags() {
            List<Token> t = toks("a<B>c</B>d");
            assertEquals(List.of(TokenKind.TEXT, TokenKind.OPEN_TAG,
                            TokenKind.TAG_NAME, TokenKind.CLOSE_TAG,
                            TokenKind.TEXT, TokenKind.OPEN_CLOSE_TAG,
                            TokenKind.TAG_NAME, TokenKind.CLOSE_TAG,
                            TokenKind.TEXT),
                    t.stream().map(Token::kind).toList());
            assertEquals("a", t.get(0).value());
            assertEquals("c", t.get(4).value());
            assertEquals("d", t.get(8).value());
        }

        @Test
        @DisplayName("comments <!-- ... --> are skipped entirely")
        void commentsSkipped() {
            List<Token> t = toks("a<!-- note -->b");
            assertEquals(List.of(TokenKind.TEXT, TokenKind.TEXT),
                    t.stream().map(Token::kind).toList());
            assertEquals("a", t.get(0).value());
            assertEquals("b", t.get(1).value());
        }

        @Test
        @DisplayName("multiline comment keeps line counting correct")
        void multilineComment() {
            List<Token> t = toks("<!--\nline2\n-->x");
            assertEquals(1, t.size());
            assertEquals(TokenKind.TEXT, t.get(0).kind());
            assertEquals("x", t.get(0).value());
            assertEquals(3, t.get(0).line());
            // "-->" occupies columns 1..3 of line 3, so 'x' starts at column 4.
            assertEquals(4, t.get(0).column());
        }

        @Test
        @DisplayName("text before a multiline comment keeps its own position")
        void textBeforeMultilineComment() {
            List<Token> t = toks("ab<!--\nx\n-->y");
            assertEquals(List.of(TokenKind.TEXT, TokenKind.TEXT),
                    t.stream().map(Token::kind).toList());
            assertEquals("ab", t.get(0).value());
            assertEquals(1, t.get(0).line());
            assertEquals(1, t.get(0).column());
            assertEquals("y", t.get(1).value());
            assertEquals(3, t.get(1).line());
        }

        @Test
        @DisplayName("unterminated comment throws MarkupException with position")
        void unterminatedComment() {
            MarkupException e = assertThrows(MarkupException.class,
                    () -> new Tokenizer("<!-- oops").tokenize());
            assertEquals(1, e.getLine());
            assertEquals(1, e.getColumn());
            assertTrue(e.getMessage().contains("unterminated comment"), e.getMessage());
        }

        @Test
        @DisplayName("stray '}' yields a positioned recoverable ERROR token")
        void strayBrace() {
            List<Token> t = toks("ab}cd");
            assertEquals(List.of(TokenKind.TEXT, TokenKind.ERROR, TokenKind.TEXT),
                    t.stream().map(Token::kind).toList());
            assertEquals(3, t.get(1).column(), "the '}' sits at column 3");
            assertEquals("cd", t.get(2).value());
            assertEquals(4, t.get(2).column());
        }
    }

    @Nested
    @DisplayName("Brace escapes")
    class Escapes {

        @Test
        @DisplayName("'{{' folds to a literal '{' inside TEXT")
        void openEscape() {
            List<Token> t = toks("cost: {{7}} units");
            // "{{7}}" -> "{" + binding? No: "{{" folds, then "7}}" is text
            // ending with a folded '}'. Expect TEXT pieces split by folding.
            StringBuilder joined = new StringBuilder();
            for (Token tok : t) {
                if (tok.kind() == TokenKind.TEXT) {
                    joined.append(tok.value());
                } else {
                    throw new AssertionError("unexpected token " + tok);
                }
            }
            assertEquals("cost: {7} units", joined.toString());
        }

        @Test
        @DisplayName("escaped run reports the position of its first character")
        void escapePositions() {
            // "a{{b" folds into ONE run: TEXT("a{b") starting at column 1.
            List<Token> t = toks("a{{b");
            assertEquals(1, t.size(), "escapes never split a text run");
            assertEquals("a{b", t.get(0).value());
            assertEquals(1, t.get(0).column(), "run anchors on its first literal char");
        }

        @Test
        @DisplayName("'}}' folds to a literal '}' inside one run")
        void closeEscape() {
            List<Token> t = toks("x}}y");
            assertEquals(1, t.size(), "escapes never split a text run");
            assertEquals("x}y", t.get(0).value());
            assertEquals(1, t.get(0).column());
        }
    }

    @Nested
    @DisplayName("TAG mode")
    class TagMode {

        @Test
        @DisplayName("name, attributes, '=' and quoted strings")
        void attributesAndStrings() {
            List<Token> t = toks("<Button id=\"ok\" layout=flex disabled>");
            assertEquals(List.of(TokenKind.OPEN_TAG, TokenKind.TAG_NAME,
                            TokenKind.ATTR_NAME, TokenKind.EQUALS, TokenKind.STRING,
                            TokenKind.ATTR_NAME, TokenKind.EQUALS, TokenKind.BARE_VALUE,
                            TokenKind.ATTR_NAME, TokenKind.CLOSE_TAG),
                    t.stream().map(Token::kind).toList());
            assertEquals("ok", t.get(4).value(), "string value excludes quotes");
            assertEquals("flex", t.get(7).value());
        }

        @Test
        @DisplayName("prefixed names keep ':' and '-' in one ATTR_NAME token")
        void prefixedAttributeNames() {
            List<Token> t = toks("<Box bind:text=\"m\" for-each=\"items\">");
            assertEquals("bind:text", t.get(2).value());
            assertEquals("for-each", t.get(5).value());
        }

        @Test
        @DisplayName("'>' closes a tag, '/>' closes self-closing")
        void closers() {
            assertEquals(List.of(TokenKind.OPEN_TAG, TokenKind.TAG_NAME,
                            TokenKind.CLOSE_SELF_TAG),
                    kinds("<Button/>"));
            Token last = toks("<Button/>").get(2);
            assertEquals("/>", last.value());
        }

        @Test
        @DisplayName("string escapes \\\" and \\\\ resolve inside STRING")
        void stringEscapes() {
            List<Token> t = toks("<Button label=\"say \\\"hi\\\" now\">");
            assertEquals("say \"hi\" now", t.get(4).value());
        }

        @Test
        @DisplayName("EOF inside a tag throws MarkupException pointing at '<'")
        void eofInsideTag() {
            MarkupException e = assertThrows(MarkupException.class,
                    () -> new Tokenizer("<Button id=\"x\"").tokenize());
            assertEquals(1, e.getLine());
            assertEquals(1, e.getColumn());
            assertTrue(e.getMessage().contains("inside tag"), e.getMessage());
        }

        @Test
        @DisplayName("unterminated string throws with the quote position")
        void unterminatedString() {
            MarkupException e = assertThrows(MarkupException.class,
                    () -> new Tokenizer("<Button label=\"oops>").tokenize());
            assertEquals(15, e.getColumn());
            assertTrue(e.getMessage().contains("unterminated string"), e.getMessage());
        }
    }

    @Nested
    @DisplayName("BINDING mode")
    class BindingMode {

        @Test
        @DisplayName("valid path '{user.name}' lexes as IDENT DOT IDENT CLOSE")
        void validPath() {
            List<Token> t = toks("{user.name}");
            assertEquals(List.of(TokenKind.BINDING_OPEN, TokenKind.IDENT,
                            TokenKind.DOT, TokenKind.IDENT,
                            TokenKind.BINDING_CLOSE),
                    t.stream().map(Token::kind).toList());
            assertEquals("user", t.get(1).value());
            assertEquals(2, t.get(1).column(), "'{' occupies column 1");
            assertEquals("name", t.get(3).value());
            assertEquals(7, t.get(3).column(), "'.' is col 6, 'name' starts at col 7");
        }

        @Test
        @DisplayName("illegal operator in '{a && b}' errors with line/column")
        void illegalOperator() {
            List<Token> t = toks("{a && b}");
            long errors = t.stream().filter(x -> x.kind() == TokenKind.ERROR).count();
            assertEquals(1, errors);
            Token err = t.stream().filter(x -> x.kind() == TokenKind.ERROR)
                    .findFirst().orElseThrow();
            assertEquals(4, err.column(), "'&' sits at column 4 ('{a ' is 3 chars)");
            assertEquals(1, err.line());
            assertTrue(err.value().contains("binding path"), err.value());
            // Recovery: scanning continues to the closing brace.
            assertEquals(TokenKind.BINDING_CLOSE,
                    t.get(t.size() - 1).kind());
        }

        @Test
        @DisplayName("illegal char in '{a!b}' errors with line/column")
        void illegalBang() {
            List<Token> t = toks("{a!b}");
            Token err = t.stream().filter(x -> x.kind() == TokenKind.ERROR)
                    .findFirst().orElseThrow();
            assertEquals(3, err.column());
            assertTrue(err.value().contains("'!'"), err.value());
        }

        @Test
        @DisplayName("numeric literal '{42}' is rejected")
        void numericLiteralRejected() {
            List<Token> t = toks("{42}");
            assertTrue(t.stream().anyMatch(x -> x.kind() == TokenKind.ERROR));
        }

        @Test
        @DisplayName("call expression '{f(x)}' is rejected")
        void callRejected() {
            List<Token> t = toks("{f(x)}");
            assertTrue(t.stream().anyMatch(x -> x.kind() == TokenKind.ERROR));
        }

        @Test
        @DisplayName("ternary '{a ? b : c}' is rejected")
        void ternaryRejected() {
            List<Token> t = toks("{a ? b : c}");
            assertTrue(t.stream().anyMatch(x -> x.kind() == TokenKind.ERROR));
        }

        @Test
        @DisplayName("EOF inside a binding throws MarkupException")
        void eofInsideBinding() {
            MarkupException e = assertThrows(MarkupException.class,
                    () -> new Tokenizer("{user").tokenize());
            assertTrue(e.getMessage().contains("inside binding"), e.getMessage());
        }
    }

    @Nested
    @DisplayName("Positions and stream shape")
    class Positions {

        @Test
        @DisplayName("line/column are 1-based across newlines (incl. CRLF)")
        void multiLinePositions() {
            List<Token> t = toks("<Box>\r\n  <Label/>\r\n</Box>");
            // OPEN_TAG '<' at 1:1
            assertEquals(1, t.get(0).line());
            assertEquals(1, t.get(0).column());
            // inner OPEN_TAG at line 2, column 3
            Token inner = t.stream()
                    .filter(x -> x.kind() == TokenKind.OPEN_TAG)
                    .toList().get(1);
            assertEquals(2, inner.line());
            assertEquals(3, inner.column());
            // closing tag at line 3, column 1
            Token close = t.stream()
                    .filter(x -> x.kind() == TokenKind.OPEN_CLOSE_TAG)
                    .findFirst().orElseThrow();
            assertEquals(3, close.line());
            assertEquals(1, close.column());
        }

        @Test
        @DisplayName("stream always ends with exactly one EOF token")
        void eofTerminator() {
            List<Token> all = new Tokenizer("").tokenize();
            assertEquals(1, all.size());
            assertEquals(TokenKind.EOF, all.get(0).kind());

            all = new Tokenizer("text").tokenize();
            assertEquals(TokenKind.EOF, all.get(all.size() - 1).kind());
            assertEquals(1, all.stream().filter(x -> x.kind() == TokenKind.EOF).count());
        }

        @Test
        @DisplayName("interpolation inside text switches modes cleanly")
        void mixedContentModes() {
            List<Token> t = toks("Hi {user.name}!");
            assertEquals(List.of(TokenKind.TEXT, TokenKind.BINDING_OPEN,
                            TokenKind.IDENT, TokenKind.DOT, TokenKind.IDENT,
                            TokenKind.BINDING_CLOSE, TokenKind.TEXT),
                    t.stream().map(Token::kind).toList());
            assertEquals("!", t.get(6).value());
            assertEquals(15, t.get(6).column());
        }
    }
}
