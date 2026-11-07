package io.github.yasmramos.glyphui.ui;

/**
 * Marks a popup that floats above the page without taking input from it.
 *
 * <p>Regular popups (menus, dialogs) are interactive: {@link PopupLayer}
 * routes pointer and key events to them first and swallows anything that
 * lands on their chrome. A <em>passive</em> overlay is decoration instead —
 * a {@link Tooltip} or a {@link Toast} — so the layer renders it on top but
 * deliberately ignores it when:</p>
 *
 * <ul>
 *   <li>hit-testing hover, so the widget underneath keeps its hover state;</li>
 *   <li>deciding whether an open popup owns the keyboard, so typing still
 *       reaches the focused page widget;</li>
 *   <li>swallowing clicks, so a toast never blocks the control it covers;</li>
 *   <li>handling ESC, so a tooltip is not dismissed as if it were a menu.</li>
 * </ul>
 *
 * <p>Overlays that <em>do</em> accept a click (a toast's close button, for
 * instance) remain reachable: the layer still routes events through the tree,
 * and a passive overlay only declines the event it does not consume, exactly
 * like an ordinary transparent panel.</p>
 */
public interface PassiveOverlay {
}
