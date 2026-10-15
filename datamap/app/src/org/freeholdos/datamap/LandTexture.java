// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RuntimeShader;

import org.freeholdos.datamap.model.LandData;

/**
 * The land as three equirectangular masks (fill, coast glow, coast line), made once off the main thread, and the
 * shader that turns the globe's rotation into the picture of the land. The paths the globe used to rebuild and
 * rasterise on the CPU in every frame (93 000 points, four draws, median 93 ms on the device) are now one draw of a
 * disk on the GPU, whatever the rotation. Stroke widths are in texels, so a coast grows with the zoom.
 */
final class LandTexture {
    static final int W = 3072;
    static final int H = 1536;
    private static final float SEAM = 179.99f;
    private static final float POLE = 89.99f;

    private LandTexture() {}

    /** The longitude and latitude under a pixel of the disk, found with the rows of the rotation (see Rotation.rows). */
    static final String AGSL = ""
            + "uniform shader fillTex;\n"
            + "uniform shader glowTex;\n"
            + "uniform shader coastTex;\n"
            + "uniform float2 texSize;\n"
            + "uniform float2 centre;\n"
            + "uniform float radius;\n"
            + "uniform float3 rowA;\n"
            + "uniform float3 rowB;\n"
            + "uniform float3 rowC;\n"
            + "layout(color) uniform half4 landC;\n"
            + "layout(color) uniform half4 glowC;\n"
            + "layout(color) uniform half4 coastC;\n"
            + "half4 main(float2 xy) {\n"
            + "  float2 d = (xy - centre) / radius;\n"
            + "  float qy = -d.y;\n"
            + "  float rr = d.x * d.x + qy * qy;\n"
            + "  if (rr >= 1.0) { return half4(0.0); }\n"
            + "  float3 w = rowA * d.x + rowB * qy + rowC * sqrt(1.0 - rr);\n"
            + "  float lat = asin(clamp(w.y, -1.0, 1.0));\n"
            + "  float lon = atan(w.x, w.z);\n"
            + "  float2 uv = float2((lon * 0.15915494 + 0.5) * texSize.x, (0.5 - lat * 0.31830989) * texSize.y);\n"
            + "  half f = fillTex.eval(uv).a;\n"
            + "  half g = glowTex.eval(uv).a * glowC.a;\n"
            + "  half c = coastTex.eval(uv).a * coastC.a;\n"
            + "  half4 col = half4(landC.rgb * landC.a, landC.a) * f;\n"
            + "  col = half4(glowC.rgb * g, g) + col * (1.0 - g);\n"
            + "  col = half4(coastC.rgb * c, c) + col * (1.0 - c);\n"
            + "  return col;\n"
            + "}\n";

    /** @return fill, glow and coast as bitmaps of W by H, or null when they cannot be made (the globe then draws paths) */
    static Bitmap[] render(LandData land) {
        try {
            Path fill = new Path();
            fill.setFillType(Path.FillType.EVEN_ODD);
            Path coast = new Path();
            for (float[][] shape : land.shapes) {
                for (float[] ring : shape) {
                    int n = ring.length / 2;
                    if (n < 3) {
                        continue;
                    }
                    for (int i = 0; i < n; i++) {
                        float x = x(ring[2 * i]);
                        float y = y(ring[2 * i + 1]);
                        if (i == 0) {
                            fill.moveTo(x, y);
                            coast.moveTo(x, y);
                        } else {
                            fill.lineTo(x, y);
                            if (onEdge(ring[2 * i - 2], ring[2 * i - 1], ring[2 * i], ring[2 * i + 1])) {
                                coast.moveTo(x, y);
                            } else {
                                coast.lineTo(x, y);
                            }
                        }
                    }
                    fill.close();
                    if (!onEdge(ring[2 * n - 2], ring[2 * n - 1], ring[0], ring[1])) {
                        coast.lineTo(x(ring[0]), y(ring[1]));
                    }
                }
            }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Bitmap f = mask();
            p.setStyle(Paint.Style.FILL);
            new Canvas(f).drawPath(fill, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            Bitmap g = mask();
            Canvas gc = new Canvas(g);
            p.setStrokeWidth(6f);
            p.setAlpha(Math.round(255 * 0.35f));
            gc.drawPath(coast, p);
            p.setStrokeWidth(3f);
            p.setAlpha(Math.round(255 * 0.7f));
            gc.drawPath(coast, p);
            Bitmap c = mask();
            p.setStrokeWidth(1.2f);
            p.setAlpha(255);
            new Canvas(c).drawPath(coast, p);
            return new Bitmap[] {gpu(f), gpu(g), gpu(c)};
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap mask() {
        return Bitmap.createBitmap(W, H, Bitmap.Config.ALPHA_8);
    }

    /** The copy that lives in graphics memory only; the bitmap in the heap is released. */
    private static Bitmap gpu(Bitmap b) {
        Bitmap h = b.copy(Bitmap.Config.HARDWARE, false);
        if (h == null) {
            return b;
        }
        b.recycle();
        return h;
    }

    private static float x(float lon) {
        return (lon + 180f) / 360f * W;
    }

    private static float y(float lat) {
        return (90f - lat) / 180f * H;
    }

    /** A segment on the antimeridian or along the pole is a cut in the data, not a coast. */
    private static boolean onEdge(float lon0, float lat0, float lon1, float lat1) {
        return (Math.abs(lon0) >= SEAM && Math.abs(lon1) >= SEAM && lon0 * lon1 > 0)
                || (Math.abs(lat0) >= POLE && Math.abs(lat1) >= POLE);
    }
}
