package com.kisisel.magaza;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Kodla cizilen cizgi ikonlar (24x24 tuval). */
final class Icons extends Drawable {
    static final int HOME = 0, SEARCH = 1, UPDATE = 2, LIBRARY = 3, SETTINGS = 4, BACK = 5,
            SHARE = 6, SHIELD = 7, GLOBE = 8, MAIL = 9, LOCK = 10, CHEVRON = 11, PHONE = 12,
            CLOCK = 13, CLOSE = 14, CHECK = 15, DOWNLOAD = 16, REFRESH = 17, INFO = 18, TRASH = 19,
            GRID = 20, WARNING = 21, MORE = 22, HISTORY = 23, STAR = 24, OPEN = 25;

    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private boolean filled;

    Icons(int type, int color) {
        this.type = type;
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    Icons filled(boolean f) {
        filled = f;
        invalidateSelf();
        return this;
    }

    void setColor(int color) {
        p.setColor(color);
        invalidateSelf();
    }

    @Override public int getIntrinsicWidth() { return -1; }
    @Override public int getIntrinsicHeight() { return -1; }

    private void stroke() { p.setStyle(Paint.Style.STROKE); }
    private void fill() { p.setStyle(Paint.Style.FILL); }

    @Override
    public void draw(Canvas c) {
        float w = getBounds().width(), h = getBounds().height();
        float s = Math.min(w, h) / 24f;
        c.save();
        c.translate(getBounds().left + (w - 24 * s) / 2, getBounds().top + (h - 24 * s) / 2);
        c.scale(s, s);
        p.setStrokeWidth(2f);
        stroke();
        path.reset();
        switch (type) {
            case HOME:
                path.moveTo(3.5f, 10.5f); path.lineTo(12, 3.5f); path.lineTo(20.5f, 10.5f);
                path.lineTo(20.5f, 20); path.lineTo(14.5f, 20); path.lineTo(14.5f, 14.5f);
                path.lineTo(9.5f, 14.5f); path.lineTo(9.5f, 20); path.lineTo(3.5f, 20); path.close();
                if (filled) fill();
                c.drawPath(path, p);
                break;
            case SEARCH:
                c.drawCircle(10.5f, 10.5f, 6.5f, p);
                c.drawLine(15.5f, 15.5f, 20.5f, 20.5f, p);
                break;
            case UPDATE:
            case REFRESH:
                r.set(4.5f, 4.5f, 19.5f, 19.5f);
                c.drawArc(r, -60, 300, false, p);
                fill();
                path.moveTo(15.2f, 2.2f); path.lineTo(20.4f, 5.4f); path.lineTo(15.4f, 9.2f); path.close();
                c.drawPath(path, p);
                if (type == UPDATE) {
                    stroke();
                    c.drawLine(12, 8.5f, 12, 15, p);
                    path.reset();
                    path.moveTo(9, 12.5f); path.lineTo(12, 15.5f); path.lineTo(15, 12.5f);
                    c.drawPath(path, p);
                }
                break;
            case LIBRARY:
            case GRID:
                if (filled) fill();
                r.set(4, 4, 10.5f, 10.5f); c.drawRoundRect(r, 1.5f, 1.5f, p);
                r.set(13.5f, 4, 20, 10.5f); c.drawRoundRect(r, 1.5f, 1.5f, p);
                r.set(4, 13.5f, 10.5f, 20); c.drawRoundRect(r, 1.5f, 1.5f, p);
                r.set(13.5f, 13.5f, 20, 20); c.drawRoundRect(r, 1.5f, 1.5f, p);
                break;
            case SETTINGS:
                c.drawLine(4, 7, 20, 7, p);
                c.drawLine(4, 17, 20, 17, p);
                fill();
                c.drawCircle(9, 7, 3f, p);
                c.drawCircle(15, 17, 3f, p);
                break;
            case BACK:
                path.moveTo(20, 12); path.lineTo(4.5f, 12);
                path.moveTo(11, 5); path.lineTo(4, 12); path.lineTo(11, 19);
                c.drawPath(path, p);
                break;
            case SHARE:
                c.drawLine(7, 12, 17, 6, p);
                c.drawLine(7, 12, 17, 18, p);
                fill();
                c.drawCircle(6, 12, 3, p);
                c.drawCircle(18, 5.5f, 3, p);
                c.drawCircle(18, 18.5f, 3, p);
                break;
            case SHIELD:
                path.moveTo(12, 3); path.lineTo(19.5f, 6); path.lineTo(19.5f, 11.5f);
                path.cubicTo(19.5f, 16, 16, 19.5f, 12, 21); path.cubicTo(8, 19.5f, 4.5f, 16, 4.5f, 11.5f);
                path.lineTo(4.5f, 6); path.close();
                c.drawPath(path, p);
                path.reset();
                path.moveTo(8.5f, 12); path.lineTo(11, 14.5f); path.lineTo(15.5f, 9.5f);
                c.drawPath(path, p);
                break;
            case GLOBE:
                c.drawCircle(12, 12, 8.5f, p);
                r.set(8, 3.5f, 16, 20.5f); c.drawOval(r, p);
                c.drawLine(3.5f, 12, 20.5f, 12, p);
                break;
            case MAIL:
                r.set(3.5f, 5.5f, 20.5f, 18.5f); c.drawRoundRect(r, 2, 2, p);
                path.moveTo(4, 7); path.lineTo(12, 13); path.lineTo(20, 7);
                c.drawPath(path, p);
                break;
            case LOCK:
                r.set(5, 10.5f, 19, 20.5f); c.drawRoundRect(r, 2, 2, p);
                r.set(8, 3.5f, 16, 14); c.drawArc(r, 180, 180, false, p);
                c.drawLine(8, 8.75f, 8, 10.5f, p);
                c.drawLine(16, 8.75f, 16, 10.5f, p);
                break;
            case CHEVRON:
                path.moveTo(9, 5); path.lineTo(16, 12); path.lineTo(9, 19);
                c.drawPath(path, p);
                break;
            case PHONE:
                r.set(6.5f, 2.5f, 17.5f, 21.5f); c.drawRoundRect(r, 2.5f, 2.5f, p);
                c.drawLine(10.5f, 18.5f, 13.5f, 18.5f, p);
                break;
            case CLOCK:
            case HISTORY:
                c.drawCircle(12, 12, 8.5f, p);
                path.moveTo(12, 7); path.lineTo(12, 12); path.lineTo(15.5f, 14);
                c.drawPath(path, p);
                break;
            case CLOSE:
                c.drawLine(6, 6, 18, 18, p);
                c.drawLine(18, 6, 6, 18, p);
                break;
            case CHECK:
                path.moveTo(5, 12.5f); path.lineTo(10, 17.5f); path.lineTo(19, 7.5f);
                c.drawPath(path, p);
                break;
            case DOWNLOAD:
                c.drawLine(12, 4, 12, 15, p);
                path.moveTo(7.5f, 10.5f); path.lineTo(12, 15); path.lineTo(16.5f, 10.5f);
                path.moveTo(5, 19.5f); path.lineTo(19, 19.5f);
                c.drawPath(path, p);
                break;
            case INFO:
                c.drawCircle(12, 12, 8.5f, p);
                c.drawLine(12, 11, 12, 16.5f, p);
                fill();
                c.drawCircle(12, 7.8f, 1.3f, p);
                break;
            case TRASH:
                c.drawLine(4.5f, 6.5f, 19.5f, 6.5f, p);
                path.moveTo(6.5f, 6.5f); path.lineTo(7.5f, 20); path.lineTo(16.5f, 20); path.lineTo(17.5f, 6.5f);
                path.moveTo(9.5f, 6.5f); path.lineTo(9.5f, 3.5f); path.lineTo(14.5f, 3.5f); path.lineTo(14.5f, 6.5f);
                c.drawPath(path, p);
                break;
            case WARNING:
                path.moveTo(12, 3.5f); path.lineTo(21, 19.5f); path.lineTo(3, 19.5f); path.close();
                c.drawPath(path, p);
                c.drawLine(12, 9.5f, 12, 14, p);
                fill();
                c.drawCircle(12, 16.8f, 1.2f, p);
                break;
            case MORE:
                fill();
                c.drawCircle(12, 5, 2, p);
                c.drawCircle(12, 12, 2, p);
                c.drawCircle(12, 19, 2, p);
                break;
            case STAR:
                if (filled) p.setStyle(Paint.Style.FILL_AND_STROKE);
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + i * 36);
                    float rad = i % 2 == 0 ? 9.5f : 4.2f;
                    float x = 12 + (float) (Math.cos(a) * rad), y = 12.8f + (float) (Math.sin(a) * rad);
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close();
                c.drawPath(path, p);
                break;
            case OPEN:
                path.moveTo(13, 4); path.lineTo(20, 4); path.lineTo(20, 11);
                path.moveTo(20, 4); path.lineTo(11, 13);
                path.moveTo(17, 14); path.lineTo(17, 20); path.lineTo(4, 20); path.lineTo(4, 7); path.lineTo(10, 7);
                c.drawPath(path, p);
                break;
            default:
        }
        c.restore();
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
