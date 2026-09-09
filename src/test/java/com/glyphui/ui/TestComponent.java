package com.glyphui.ui;

import com.glyphui.graphics.Canvas;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.KeyEvent;

/**
 * A lightweight test component that doesn't require Skija initialization.
 * Used for unit testing without native dependencies.
 */
public class TestComponent extends Component {
    
    private boolean renderCalled = false;
    private boolean mouseEventHandled = false;
    private boolean keyEventHandled = false;
    
    public TestComponent(int x, int y, int width, int height) {
        super(x, y, width, height);
    }
    
    @Override
    public void render(Canvas canvas) {
        renderCalled = true;
    }
    
    @Override
    public void onMouseEvent(MouseEvent event) {
        mouseEventHandled = true;
    }
    
    @Override
    public void onKeyEvent(KeyEvent event) {
        keyEventHandled = true;
    }
    
    public boolean isRenderCalled() {
        return renderCalled;
    }
    
    public boolean isMouseEventHandled() {
        return mouseEventHandled;
    }
    
    public boolean isKeyEventHandled() {
        return keyEventHandled;
    }
    
    public void resetFlags() {
        renderCalled = false;
        mouseEventHandled = false;
        keyEventHandled = false;
    }
}
