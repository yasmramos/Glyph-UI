package com.glyphui.events;

/**
 * Toolkit-owned key-code constants.
 *
 * <p>Historically the widget layer compared raw GLFW numeric codes (e.g.
 * {@code 259} for Backspace) hard-coded inside {@link com.glyphui.ui.TextField}.
 * With the backend abstraction ({@code WindowBackend}) each native backend
 * maps its own physical key identifiers onto these stable toolkit codes
 * before dispatching a {@link KeyEvent}, so widgets never depend on LWJGL
 * or JWM constants again.</p>
 *
 * <p><strong>Wire compatibility:</strong> the numeric values of the special
 * keys intentionally match the legacy GLFW codes that existing tests and
 * serialized events already use (Enter = 257, Backspace = 259, ...), and
 * printable ASCII keys carry their uppercase code point ('A' = 65). The A-Z
 * mapping is identical in GLFW (which uses US-ASCII key codes) and JWM
 * (whose {@code Key.DIGIT_A..DIGIT_Z} ordinals coincide with ASCII), so both
 * backends can forward these values without translation.</p>
 */
public final class GlyphKeys {

    private GlyphKeys() {
    } // utility class

    // --- Editing / navigation keys (legacy-compatible numeric values) ----
    public static final int ENTER = 257;
    public static final int ESCAPE = 256;
    public static final int BACKSPACE = 259;
    public static final int DELETE = 261;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int HOME = 268;
    public static final int END = 269;
    public static final int TAB = 258;
    public static final int SPACE = 32;

    // --- Printable letters: US-ASCII uppercase code points ---------------
    public static final int A = 'A';
    public static final int B = 'B';
    public static final int C = 'C';
    public static final int D = 'D';
    public static final int E = 'E';
    public static final int F = 'F';
    public static final int G = 'G';
    public static final int H = 'H';
    public static final int I = 'I';
    public static final int J = 'J';
    public static final int K = 'K';
    public static final int L = 'L';
    public static final int M = 'M';
    public static final int N = 'N';
    public static final int O = 'O';
    public static final int P = 'P';
    public static final int Q = 'Q';
    public static final int R = 'R';
    public static final int S = 'S';
    public static final int T = 'T';
    public static final int U = 'U';
    public static final int V = 'V';
    public static final int W = 'W';
    public static final int X = 'X';
    public static final int Y = 'Y';
    public static final int Z = 'Z';
}
