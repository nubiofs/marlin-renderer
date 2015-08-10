/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package test;

import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.marlin.pisces.MarlinRenderingEngine;
import sun.java2d.pipe.RenderingEngine;

/**
 * Test program that demonstrates a bug in OpenJDK 1.7.0.60 (and
 * probably in all other OpenJDK versions, too).
 *
 * @see #main(String[])
 *
 * @author Rami Hanninen
 */
public class OpenJDKFillBug {

    /**
     * Test program that demonstrates a bug in OpenJDK 1.7.0.60 (and
     * probably in all other OpenJDK versions, too). To see the bug, simply run
     * the 'main' program with OpenJDK. The bug makes the 'g2d.fill'
     * method fail with the following exception:
     *
     * <PRE>
     * Exception in thread "main" java.awt.image.RasterFormatException: (x + width) is outside raster
     * at sun.awt.image.IntegerInterleavedRaster.createWritableChild(IntegerInterleavedRaster.java:467)
     * at sun.awt.image.IntegerInterleavedRaster.createChild(IntegerInterleavedRaster.java:514)
     * at sun.java2d.pipe.GeneralCompositePipe.renderPathTile(GeneralCompositePipe.java:106)
     * at sun.java2d.pipe.AAShapePipe.renderTiles(AAShapePipe.java:201)
     * at sun.java2d.pipe.AAShapePipe.renderPath(AAShapePipe.java:159)
     * at sun.java2d.pipe.AAShapePipe.fill(AAShapePipe.java:68)
     * at sun.java2d.pipe.PixelToParallelogramConverter.fill(PixelToParallelogramConverter.java:164)
     * at sun.java2d.pipe.ValidatePipe.fill(ValidatePipe.java:160)
     * at sun.java2d.SunGraphics2D.fill(SunGraphics2D.java:2466)
     * at OpenJDKFillBug.main(OpenJDKFillBug.java:55)
     * </PRE>
     *
     * The bug is OpenJDK specific. This program runs with Oracle JDK
     * just fine (it still does not do anything sensible, but it does
     * not fail with a RasterFormatException, either).
     *
     * <P>
     *
     * The bug is related to sun.java2d.pisces.PiscesCache constructor
     * that accepts '(int minx,int miny,int maxx,int maxy)' arguments:
     * the internal 'bboxX1' and 'bboxY1' are set to values one greater
     * than given maximum X and Y values. Those maximum values are then
     * later used in AAShapePipe' class 'renderTiles' method, where a
     * Y/X loop eventually calls 'GeneralCompositePipe' class
     * 'renderPathTile' method. In that method, the operation will
     * eventually call 'IntegerInterleavedRaster' class
     * 'createWritableChild' method with arguments:
     *
     * <UL>
     * <LI>x=800
     * <LI>y=0
     * <LI>width=2 (this value is too high: should be 1)
     * <LI>height=32
     * <LI>x0=0
     * <LI>y0=0
     * <LI>bandList[]=null
     * </UL>
     *
     * This calls for a sub-raster with bounds that fall outside the
     * original raster, and therefore the 'createWritableChild' method
     * correctly throws 'RasterFormatException'.
     *
     * <P>
     *
     * The bug is closely related to the use of a custom Composite
     * implementation, which are quite rare. The application where this
     * bug was first detected implements a high-quality PDF rendering
     * engine that needs custom Composite operations to properly
     * implement PDF advanced color blending and masking operators.
     */

    public static void main(String args[]) {

        System.out.println("Testing renderer = " + RenderingEngine.getInstance().getClass().getName());

        BufferedImage bi = new BufferedImage(801, 1202, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        GeneralPath gp = new GeneralPath();
        AffineTransform m = new AffineTransform(2.483489907915543,
                0.0,
                0.0,
                -2.4844977263331955,
                0.0,
                1202.0);
        Composite c = new CustomComposite();

        gp.moveTo(-4.511, -14.349);
        gp.lineTo(327.489, -14.349);
        gp.lineTo(327.489, 494.15);
        gp.lineTo(-4.511, 494.15);
        gp.closePath();

        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST,
                Integer.valueOf(140));
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING,
                RenderingHints.VALUE_DITHER_ENABLE);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_NORMALIZE);
        g2d.setPaint(Color.red);
        g2d.setComposite(c);
        g2d.setTransform(m);
        g2d.fill(gp);

        try {
            final File file = new File("OpenJDKFillBugTest.png");

            System.out.println("Writing file: " + file.getAbsolutePath());;
            ImageIO.write(bi, "PNG", file);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            g2d.dispose();
        }
    }

  // === CustomComposite ===
    /**
     * Dummy custom Composite implementation.
     */
    public static class CustomComposite implements Composite {

        @Override
        public CompositeContext createContext(ColorModel srcColorModel,
                                              ColorModel dstColorModel,
                                              RenderingHints hints) {
            return new CustomCompositeContext();
        }

    // === CustomCompositeContext ===
        /**
         * Dummy custom CompositeContext implementation.
         */
        public static class CustomCompositeContext implements CompositeContext {

            @Override
            public void dispose() {
                // NOP
            }

            @Override
            public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
                // NOP
            }
        }
    }
}