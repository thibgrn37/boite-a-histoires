package fr.boiteahistoires.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Petits outils pour construire l'interface en code. */
final class Ui {
    static final int CREAM = 0xFFFFF3E0;
    static final int ORANGE = 0xFFFF7043;
    static final int GREEN = 0xFF43A047;
    static final int BLUE = 0xFF1E88E5;
    static final int DARK = 0xFF3E2723;
    static final int GREY = 0xFF8D6E63;
    static final int WHITE = 0xFFFFFFFF;
    static final int[] PALETTE = {0xFFFFB74D, 0xFF4FC3F7, 0xFF81C784, 0xFFF06292, 0xFFBA68C8, 0xFFFFD54F, 0xFF4DB6AC};

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), content, null);
    }

    static int colorFor(String s) {
        return PALETTE[Math.abs(s == null ? 0 : s.hashCode()) % PALETTE.length];
    }

    /** Gros bouton rond avec icône. */
    static ImageView roundButton(Context c, int icon, int color, int sizeDp) {
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        b.setBackground(ripple(circle(color)));
        int pad = dp(c, sizeDp * 0.22f);
        b.setPadding(pad, pad, pad, pad);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setElevation(dp(c, 4));
        b.setClickable(true);
        b.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        b.setLayoutParams(lp);
        return b;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static Button button(Context c, String s, int color) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setBackground(ripple(rounded(color, dp(c, 12))));
        b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        b.setStateListAnimator(null);
        return b;
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lpMargins(Context c, int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** Carré à coins arrondis : couleur + initiale, recouvert par l'image si elle existe. */
    static final class Card extends FrameLayout {
        final ImageView image;
        final TextView letter;
        final ImageView badge;

        Card(Context c) {
            super(c);
            float r = dp(c, 22);
            setBackground(rounded(Color.LTGRAY, r));
            setClipToOutline(true);
            setElevation(dp(c, 3));
            letter = new TextView(c);
            letter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
            letter.setTextColor(WHITE);
            letter.setTypeface(Typeface.DEFAULT_BOLD);
            letter.setGravity(Gravity.CENTER);
            addView(letter, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(image, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            badge = new ImageView(c);
            int bs = dp(c, 44);
            int pad = dp(c, 8);
            badge.setPadding(pad, pad, pad, pad);
            LayoutParams blp = new LayoutParams(bs, bs, Gravity.TOP | Gravity.END);
            blp.setMargins(0, dp(c, 8), dp(c, 8), 0);
            addView(badge, blp);
            badge.setVisibility(GONE);
        }

        void setPlaceholder(String name) {
            ((GradientDrawable) getBackground().mutate()).setColor(colorFor(name));
            letter.setText(name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        }

        void setBadge(int icon, int color) {
            if (icon == 0) { badge.setVisibility(GONE); return; }
            badge.setImageResource(icon);
            badge.setBackground(circle(color));
            badge.setVisibility(VISIBLE);
        }

        void setHighlighted(boolean on) {
            if (on) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(getContext(), 22));
                g.setStroke(dp(getContext(), 7), ORANGE);
                g.setColor(Color.TRANSPARENT);
                setForeground(g);
            } else {
                setForeground(null);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        }
    }
}
