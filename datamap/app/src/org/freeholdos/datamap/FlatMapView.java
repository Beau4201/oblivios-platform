// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.freeholdos.datamap.model.LandData;

/**
 * {@code .flat}: a plain map around the place of a company, the land it stands on filled in the accent colours and a pin
 * at its base, with the country's name next to it. The shape that holds the place is the one that is filled; a country
 * of several parts is filled in the part the company stands on.
 */
final class FlatMapView extends View {
    private final Ds mDs;
    private final LandData mLand;
    private final double mLat;
    private final double mLon;
    private final String mLabel;
    private final int mShape;
    private final Paint mP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mRect = new RectF();

    FlatMapView(Context context, Ds ds, LandData land, double lat, double lon, String label) {
        super(context);
        mDs = ds;
        mLand = land;
        mLat = lat;
        mLon = lon;
        mLabel = label;
        mShape = land == null ? -1 : land.shapeAt(lat, lon);
        setId(R.id.at_flat_map);
        setClipToOutline(true);
        final float r = ds.px(12);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        setBackground(ds.box(0, 12, R.color.at_bd));
        setForeground(ds.box(0, 12, R.color.at_bd));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        mP.setStyle(Paint.Style.FILL);
        mP.setColor(mDs.color(R.color.at_mini_bg));
        canvas.drawRect(0, 0, w, h, mP);
        // the area: the shape the place is in, or 30 by 20 degrees around the place
        double minLon = mLon - 15;
        double maxLon = mLon + 15;
        double minLat = mLat - 10;
        double maxLat = mLat + 10;
        if (mShape >= 0) {
            float[] b = mLand.bounds(mShape);
            minLon = b[0];
            maxLon = b[1];
            minLat = b[2];
            maxLat = b[3];
        }
        double midLat = (minLat + maxLat) / 2;
        double kx = Math.max(0.2, Math.cos(Math.toRadians(midLat)));
        double scale = Math.min(w / ((maxLon - minLon) * kx), h / (maxLat - minLat)) * 0.8;
        double midLon = (minLon + maxLon) / 2;
        float cx = w / 2;
        float cy = h / 2;
        if (mLand != null) {
            mP.setStrokeWidth(mDs.px(0.7f));
            for (int s = 0; s < mLand.shapes.length; s++) {
                boolean hit = s == mShape;
                float[] bb = mLand.bounds(s);
                if (bb[1] < midLon - w / 2 / (kx * scale) || bb[0] > midLon + w / 2 / (kx * scale)
                        || bb[3] < midLat - h / 2 / scale || bb[2] > midLat + h / 2 / scale) {
                    continue;
                }
                mPath.rewind();
                for (float[] ring : mLand.shapes[s]) {
                    for (int i = 0; i < ring.length; i += 2) {
                        float x = cx + (float) ((ring[i] - midLon) * kx * scale);
                        float y = cy - (float) ((ring[i + 1] - midLat) * scale);
                        if (i == 0) {
                            mPath.moveTo(x, y);
                        } else {
                            mPath.lineTo(x, y);
                        }
                    }
                    mPath.close();
                }
                mPath.setFillType(Path.FillType.EVEN_ODD);
                mP.setStyle(Paint.Style.FILL);
                mP.setColor(mDs.color(hit ? R.color.at_mini_fill_hit : R.color.at_mini_fill));
                canvas.drawPath(mPath, mP);
                mP.setStyle(Paint.Style.STROKE);
                mP.setColor(mDs.color(hit ? R.color.at_mini_stroke_hit : R.color.at_mini_stroke));
                mP.setStrokeWidth(mDs.px(hit ? 1f : 0.7f));
                canvas.drawPath(mPath, mP);
            }
        }
        // the pin at the base, with a halo under it
        float px = cx + (float) ((mLon - midLon) * kx * scale);
        float py = cy - (float) ((mLat - midLat) * scale);
        mP.setStyle(Paint.Style.FILL);
        mP.setColor(mDs.color(R.color.at_mini_pin_halo));
        canvas.drawCircle(px, py, mDs.px(13), mP);
        mP.setColor(mDs.color(R.color.at_mini_pin));
        mPath.rewind();
        mPath.moveTo(px, py);
        mRect.set(px - mDs.px(6.5f), py - mDs.px(8) - mDs.px(6.5f), px + mDs.px(6.5f), py - mDs.px(8) + mDs.px(6.5f));
        mPath.arcTo(mRect, 144f, 252f);
        mPath.close();
        canvas.drawPath(mPath, mP);
        mP.setColor(0xFFFFFFFF);
        canvas.drawCircle(px, py - mDs.px(8), mDs.px(2.4f), mP);
        // the country's name beside it, on the side that has room
        mP.setTypeface(Ds.face(Ds.MEDIUM));
        mP.setTextSize(mDs.px(12));
        mP.setColor(mDs.color(R.color.at_mini_label));
        float tw = mP.measureText(mLabel);
        float lx = px + mDs.px(14) + tw > w - mDs.px(6) ? px - mDs.px(14) - tw : px + mDs.px(12);
        canvas.drawText(mLabel, lx, py - mDs.px(8) - (mP.ascent() + mP.descent()) / 2, mP);
    }
}
