package fr.boiteahistoires.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Petite pile dessinée + pourcentage, pour savoir quand brancher le téléphone. */
final class BatteryView extends View {
    private int level = -1;
    private boolean charging;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    BatteryView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Ui.dp(c, 2.5f));
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(Ui.dp(c, 15));
    }

    void set(int level, boolean charging) {
        if (level == this.level && charging == this.charging) return;
        this.level = level;
        this.charging = charging;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (level < 0) return;
        float d = getResources().getDisplayMetrics().density;
        int color = level <= 15 ? 0xFFE53935 : (level <= 35 ? 0xFFFF9800 : 0xFF43A047);
        float h = getHeight(), bw = 32 * d, bh = 17 * d, top = (h - bh) / 2;
        stroke.setColor(Ui.SUB);
        r.set(stroke.getStrokeWidth(), top, bw, top + bh);
        c.drawRoundRect(r, 4 * d, 4 * d, stroke);
        fill.setColor(Ui.SUB);
        c.drawRect(bw + 1 * d, top + bh * 0.3f, bw + 4 * d, top + bh * 0.7f, fill);
        fill.setColor(color);
        float inset = 3.5f * d;
        float w = (bw - 2 * inset) * Math.max(0.06f, level / 100f);
        r.set(inset + stroke.getStrokeWidth() / 2, top + inset, inset + stroke.getStrokeWidth() / 2 + w, top + bh - inset);
        c.drawRoundRect(r, 2 * d, 2 * d, fill);
        if (charging) {
            Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
            bolt.setTextSize(bh * 0.95f);
            bolt.setTextAlign(Paint.Align.CENTER);
            bolt.setColor(0xFF000000);
            Paint.FontMetrics bm = bolt.getFontMetrics();
            c.drawText("⚡", bw / 2 + stroke.getStrokeWidth() / 2, top + bh / 2 - (bm.ascent + bm.descent) / 2, bolt);
        }
        String s = level + " %";
        text.setColor(level <= 15 && !charging ? 0xFFE53935 : Ui.INK);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(s, bw + 8 * d, h / 2 - (fm.ascent + fm.descent) / 2, text);
    }
}
