package com.mevolthome.upsmonitor;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class NixieBatteryView extends LinearLayout {
    private final TextView[] digits = new TextView[8];
    private final FrameLayout caseView;
    private float glow = .88f;
    private int batteryPercent = -1;
    private int activeCells = -1;
    private boolean animationsEnabled;
    private ValueAnimator glowAnimator;

    NixieBatteryView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setFocusable(false);
        caseView = new FrameLayout(context);
        caseView.setBackground(caseBackground());
        caseView.setElevation(dp(5));
        addView(caseView, new LayoutParams(-1, dp(122)));
        buildBatteryModel(context);
        setBatteryPercent(0);
    }

    private void buildBatteryModel(Context context) {
        View topBevel = new View(context);
        topBevel.setBackground(bevelBackground());
        FrameLayout.LayoutParams bevelParams = new FrameLayout.LayoutParams(
                -1, dp(12), Gravity.TOP);
        bevelParams.leftMargin = dp(10);
        bevelParams.rightMargin = dp(10);
        caseView.addView(topBevel, bevelParams);

        LinearLayout glassWindow = new LinearLayout(context);
        glassWindow.setGravity(Gravity.CENTER);
        glassWindow.setOrientation(HORIZONTAL);
        glassWindow.setPadding(dp(7), dp(5), dp(7), dp(5));
        glassWindow.setBackgroundResource(R.drawable.nixie_tube_glass);
        FrameLayout.LayoutParams glassParams = new FrameLayout.LayoutParams(
                -1, dp(75), Gravity.TOP);
        glassParams.leftMargin = dp(8);
        glassParams.rightMargin = dp(8);
        glassParams.topMargin = dp(15);
        caseView.addView(glassWindow, glassParams);

        for (int index = 0; index < digits.length; index++) {
            FrameLayout cell = new FrameLayout(context);
            cell.setBackgroundResource(R.drawable.nixie_tube_glass);
            LayoutParams cellParams = new LayoutParams(0, -1, 1f);
            cellParams.leftMargin = dp(1);
            cellParams.rightMargin = dp(1);
            glassWindow.addView(cell, cellParams);

            ImageView detail = new ImageView(context);
            detail.setImageResource(R.drawable.nixie_tube_detail);
            detail.setScaleType(ImageView.ScaleType.FIT_XY);
            cell.addView(detail, new FrameLayout.LayoutParams(-1, -1));

            TextView digit = new TextView(context);
            digit.setText("1");
            digit.setTextSize(21);
            digit.setTextColor(Color.rgb(255, 151, 48));
            digit.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            digit.setGravity(Gravity.CENTER);
            digit.setIncludeFontPadding(false);
            digit.setShadowLayer(dp(5), 0, 0, Color.rgb(255, 132, 31));
            cell.addView(digit, new FrameLayout.LayoutParams(-1, -1));
            digits[index] = digit;
        }

        ImageView grain = new ImageView(context);
        grain.setImageResource(R.drawable.nixie_wood_grain);
        grain.setScaleType(ImageView.ScaleType.FIT_XY);
        FrameLayout.LayoutParams grainParams = new FrameLayout.LayoutParams(
                -1, dp(25), Gravity.BOTTOM);
        grainParams.leftMargin = dp(8);
        grainParams.rightMargin = dp(8);
        grainParams.bottomMargin = dp(2);
        caseView.addView(grain, grainParams);

        View bottomRail = new View(context);
        bottomRail.setBackgroundResource(R.drawable.nixie_tube_socket);
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(
                -1, dp(9), Gravity.BOTTOM);
        railParams.leftMargin = dp(8);
        railParams.rightMargin = dp(8);
        caseView.addView(bottomRail, railParams);

        addScrew(context, Gravity.BOTTOM | Gravity.START);
        addScrew(context, Gravity.BOTTOM | Gravity.END);
    }

    private void addScrew(Context context, int gravity) {
        View screw = new View(context);
        GradientDrawable screwBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(212, 187, 148), Color.rgb(73, 57, 40)});
        screwBackground.setShape(GradientDrawable.OVAL);
        screwBackground.setStroke(dp(1), Color.rgb(50, 37, 26));
        screw.setBackground(screwBackground);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(5), dp(5), gravity);
        params.leftMargin = dp(6);
        params.rightMargin = dp(6);
        params.bottomMargin = dp(8);
        caseView.addView(screw, params);
    }

    void setBatteryPercent(double percent) {
        int normalizedPercent = Double.isNaN(percent) || Double.isInfinite(percent) ? 0
                : (int) Math.max(0, Math.min(100, Math.round(percent)));
        int nextActiveCells = NixieDisplayMath.activeBatteryCells(percent);
        if (batteryPercent == normalizedPercent && activeCells == nextActiveCells) {
            return;
        }
        batteryPercent = normalizedPercent;
        activeCells = nextActiveCells;
        setContentDescription(getResources().getString(
                R.string.nixie_battery_accessibility, batteryPercent, activeCells));
        for (int index = 0; index < digits.length; index++) {
            boolean active = index < activeCells;
            digits[index].setTextColor(active
                    ? Color.rgb(255, 151, 48) : Color.argb(90, 246, 142, 58));
            digits[index].setShadowLayer(active ? dp(5) : 0, 0, 0,
                    active ? Color.rgb(255, 132, 31) : Color.TRANSPARENT);
            digits[index].setAlpha(active ? glow : .55f);
        }
    }

    void startAnimation() {
        if (!isAttachedToWindow() || animationsEnabled) {
            return;
        }
        animationsEnabled = true;
        glowAnimator = ValueAnimator.ofFloat(.78f, 1f);
        glowAnimator.setDuration(3000);
        glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.addUpdateListener(animator -> {
            glow = (float) animator.getAnimatedValue();
            updateGlow();
        });
        glowAnimator.start();
    }

    void stopAnimation() {
        animationsEnabled = false;
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
        glow = .88f;
        updateGlow();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void updateGlow() {
        for (TextView digit : digits) {
            if (digit != null) {
                int alpha = Color.alpha(digit.getCurrentTextColor());
                digit.setAlpha(alpha < 150 ? .55f : glow);
            }
        }
    }

    private GradientDrawable caseBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(171, 112, 62),
                        Color.rgb(91, 56, 35),
                        Color.rgb(59, 43, 32),
                        Color.rgb(126, 78, 44),
                        Color.rgb(49, 37, 29)
                });
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(228, 185, 132));
        return background;
    }

    private GradientDrawable bevelBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(197, 141, 81), Color.rgb(108, 68, 40)});
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), Color.argb(190, 238, 193, 136));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
