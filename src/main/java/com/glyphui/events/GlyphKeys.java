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

    // --- Digits: US-ASCII code points -------------------------------------
    public static final int D0 = '0';
    public static final int D1 = '1';
    public static final int D2 = '2';
    public static final int D3 = '3';
    public static final int D4 = '4';
    public static final int D5 = '5';
    public static final int D6 = '6';
    public static final int D7 = '7';
    public static final int D8 = '8';
    public static final int D9 = '9';

    // --- Punctuation (legacy GLFW wire values) ----------------------------
    public static final int MINUS = 45;          // '-'
    public static final int EQUAL = 61;          // '='
    public static final int SLASH = 47;          // '/'
    public static final int BACKSLASH = 92;      // '\'
    public static final int SEMICOLON = 59;      // ';'
    public static final int APOSTROPHE = 39;
    public static final int COMMA = 44;          // ','
    public static final int PERIOD = 46;         // '.'
    public static final int GRAVE = 96;          // '`'
    public static final int LEFT_BRACKET = 91;   // '['
    public static final int RIGHT_BRACKET = 93;  // ']'

    // --- Modifier keys (legacy GLFW wire values) --------------------------
    public static final int LEFT_SHIFT = 340;
    public static final int LEFT_CONTROL = 341;
    public static final int LEFT_ALT = 342;
    public static final int LEFT_SUPER = 343;
    public static final int CAPS_LOCK = 280;

    // --- Function keys (legacy GLFW wire values: F1=290..F12=301) ---------
    public static final int F1 = 290;
    public static final int F2 = 291;
    public static final int F3 = 292;
    public static final int F4 = 293;
    public static final int F5 = 294;
    public static final int F6 = 295;
    public static final int F7 = 296;
    public static final int F8 = 297;
    public static final int F9 = 298;
    public static final int F10 = 299;
    public static final int F11 = 300;
    public static final int F12 = 301;
}
