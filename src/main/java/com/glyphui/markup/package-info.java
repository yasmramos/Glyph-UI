/**
 * Hand-written lexer and recursive-descent parser for {@code .glyph}
 * declarative UI markup.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>{@link com.glyphui.markup.Tokenizer} — a three-mode character scanner
 *       (CONTENT / TAG / BINDING) that produces
 *       {@link com.glyphui.markup.Token}s with 1-based line/column positions.
 *       It understands text runs, {@code &#123;path&#125;} interpolations,
 *       {@code &#123;&#123;}/{@code &#125;&#125;} literal-brace escapes,
 *       {@code &lt;!-- ... --&gt;} comments, tags with attributes and
 *       self-closing tags.</li>
 *   <li>{@link com.glyphui.markup.Parser} — consumes the token stream and
 *       builds a raw AST: {@link com.glyphui.markup.Document} root with
 *       {@link com.glyphui.markup.Element}, {@link com.glyphui.markup.TextNode}
 *       and {@link com.glyphui.markup.BindingNode} children in source order.</li>
 * </ol>
 *
 * <h2>Error strategy (documented choice)</h2>
 * The two stages use different, complementary mechanisms:
 * <ul>
 *   <li><b>Tokenizer:</b> structurally unrecoverable conditions (unterminated
 *       comment or string, EOF inside a tag or binding) throw
 *       {@link com.glyphui.markup.MarkupException} carrying the 1-based
 *       line/column of the offending construct. Recoverable lexical problems
 *       (a stray {@code &#125;} in text, an illegal character inside a
 *       binding such as {@code &amp;} or {@code !}) are emitted as
 *       {@link com.glyphui.markup.TokenKind#ERROR} tokens so scanning can
 *       continue.</li>
 *   <li><b>Parser:</b> never throws for bad markup. Every recoverable syntax
 *       error (unclosed tag, unmatched close tag, valueless attribute,
 *       duplicate attribute, stray closing brace, invalid binding path,
 *       unexpected token) is accumulated as a
 *       {@link com.glyphui.markup.ParseError} in the returned
 *       {@link com.glyphui.markup.ParseResult}, alongside the best-effort
 *       partial document. Callers check {@code isSuccess()}.</li>
 * </ul>
 *
 * <h2>Scope boundary</h2>
 * This package produces the <i>raw</i> tree only. Directives such as
 * {@code bind:*}, {@code on:*}, {@code if} / {@code elif} / {@code else} and
 * {@code for-each} are kept verbatim as plain attributes
 * ({@link com.glyphui.markup.Attribute}); interpreting them belongs to the
 * validator/binder layer.
 *
 * <h2>Constraints</h2>
 * No reflection, no parsing libraries and no regular expressions: every
 * scanning decision is an explicit character comparison in a plain loop.
 */
package com.glyphui.markup;
