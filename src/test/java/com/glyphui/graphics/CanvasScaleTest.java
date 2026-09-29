package com.glyphui.graphics;

import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link Canvas} wrapper's HiDPI support: {@code scale()}
 * must modify the canvas CTM (verified through the local-to-device matrix)
 * and save/restore must roll it back, exactly as the per-frame content-scale
 * transform applied by {@code Application.render()} relies on.
 *
 * <p>Uses a pure raster Skija surface, so no GLFW window is required.</p>
 */
public class CanvasScaleTest {

    @Test
    public void testScaleAppliesToNativeCanvasMatrix() {
        try (Surface surface = Surface.makeRaster(ImageInfo.makeN32Premul(1600, 1200))) {
            Canvas wrapper = new Canvas(surface.getCanvas(), surface, 800, 600);

            // Identity before scaling
            float[] identity = wrapper.getMatrixArray();
            assertEquals(1.0f, identity[0], 1e-4f);
            assertEquals(1.0f, identity[4], 1e-4f);

            int save = wrapper.save();
            wrapper.scale(2.0f, 2.0f);

            // HiDPI: logical coordinates now map to physical pixels at 2x
            float[] scaled = wrapper.getMatrixArray();
            assertEquals(2.0f, scaled[0], 1e-4f, "CTM scaleX must equal content scale");
            assertEquals(2.0f, scaled[4], 1e-4f, "CTM scaleY must equal content scale");

            wrapper.restoreToCount(save);

            // save/restore wrapping (as done in Application.render()) removes
            // the content-scale transform again
            float[] restored = wrapper.getMatrixArray();
            assertEquals(1.0f, restored[0], 1e-4f);
            assertEquals(1.0f, restored[4], 1e-4f);
        }
    }

    @Test
    public void testScaleMapsLogicalPointToDevicePixel() {
        try (Surface surface = Surface.makeRaster(ImageInfo.makeN32Premul(1600, 1200))) {
            Canvas wrapper = new Canvas(surface.getCanvas(), surface, 800, 600);

            int save = wrapper.save();
            wrapper.scale(2.0f, 2.0f);

            // sanity: the physical surface covers the full render target
            assertEquals(1600, surface.getWidth());
            assertEquals(1200, surface.getHeight());

            // The device-space position of logical point (10, 10) under the
            // CTM must be physical pixel (20, 20).
            // Matrix33.getMat() is row-major: [a,b,c, d,e,f, g,h,i] where
            // x' = a*x + b*y + c and y' = d*x + e*y + f.
            float[] m = wrapper.getMatrixArray();
            float deviceX = m[0] * 10 + m[1] * 10 + m[2];
            float deviceY = m[3] * 10 + m[4] * 10 + m[5];
            assertEquals(20.0f, deviceX, 1e-3f);
            assertEquals(20.0f, deviceY, 1e-3f);
            wrapper.restoreToCount(save);
        }
    }

    @Test
    public void testWrapperReportsLogicalSizeWhileSurfaceIsPhysical() {
        // The GPU/raster surface is allocated at PHYSICAL size while the
        // wrapper reports LOGICAL size (Application contract).
        try (Surface surface = Surface.makeRaster(ImageInfo.makeN32Premul(1600, 1200))) {
            Canvas wrapper = new Canvas(surface.getCanvas(), surface, 800, 600);

            assertEquals(800, wrapper.getWidth());
            assertEquals(600, wrapper.getHeight());
            assertEquals(1600, surface.getWidth());
            assertEquals(1200, surface.getHeight());

            // resize() updates only the logical reporting
            wrapper.resize(1024, 768);
            assertEquals(1024, wrapper.getWidth());
            assertEquals(768, wrapper.getHeight());
            assertEquals(1600, surface.getWidth(), "physical surface unaffected by logical resize");
        }
    }

    @Test
    public void testTranslateAndScaleComposeCorrectly() {
        try (Surface surface = Surface.makeRaster(ImageInfo.makeN32Premul(1600, 1200))) {
            Canvas wrapper = new Canvas(surface.getCanvas(), surface, 800, 600);

            int save = wrapper.save();
            // HiDPI scale followed by Panel-local translate (render path order:
            // Application applies the content-scale transform first, then panels
            // translate within it). The translation is therefore expressed in
            // logical units and scaled into device space.
            wrapper.scale(2.0f, 2.0f);
            wrapper.translate(50, 50);

            float[] m = wrapper.getMatrixArray();
            assertEquals(2.0f, m[0], 1e-4f);
            assertEquals(2.0f, m[4], 1e-4f);
            // Row-major layout: translations live in m[2] (tx) and m[5] (ty)
            assertEquals(100.0f, m[2], 1e-3f, "translation must also be scaled into device space");
            assertEquals(100.0f, m[5], 1e-3f);

            wrapper.restoreToCount(save);

            // Reverse composition order: translate applied before scale stays
            // in device units (Skia concatenates CTM ops left-to-right).
            int save2 = wrapper.save();
            wrapper.translate(50, 50);
            wrapper.scale(2.0f, 2.0f);
            float[] m2 = wrapper.getMatrixArray();
            assertEquals(50.0f, m2[2], 1e-3f, "translate-before-scale is not scaled");
            wrapper.restoreToCount(save2);
            Paint paint = new Paint();
            paint.close();
        }
    }
}
