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
import android.view.animation.DecelerateInterpolator;

final class NixieMeterView extends LinearLayout {
    private final String label;
    private final TextView sourceView;
    private final TextView valueView;
    private final TextView[] digitViews = new TextView[3];
    private String displayedDigits;
    private float displayedVoltage;
    private float targetVoltage;
    private boolean hasVoltage;
    private boolean animationsEnabled;
    private ValueAnimator valueAnimator;
    private ValueAnimator glowAnimator;

    NixieMeterView(Context context) {
        this(context, "НАПРЯЖЕНИЕ", "", Color.rgb(255, 151, 48));
    }

    NixieMeterView(Context context, String label, String source, int accentColor) {
        super(context);
        this.label = label;
        setOrientation(VERTICAL);
        setPadding(dp(9), dp(9), dp(9), dp(7));
        setBackground(cardBackground());
        setMinimumHeight(dp(224));
        setFocusable(false);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(HORIZONTAL);
        TextView title = text(label, 10, Color.rgb(215, 205, 190), true);
        header.addView(title, new LayoutParams(0, -2, 1f));
        sourceView = text(source, 9, Color.rgb(255, 190, 112), true);
        sourceView.setGravity(Gravity.END);
        header.addView(sourceView, new LayoutParams(-2, -2));
        addView(header, new LayoutParams(-1, -2));

        FrameLayout stage = new FrameLayout(context);
        LayoutParams stageParams = new LayoutParams(-1, dp(153));
        stageParams.topMargin = dp(2);
        addView(stage, stageParams);

        FrameLayout woodPlatform = new FrameLayout(context);
        woodPlatform.setBackground(woodBackground());
        woodPlatform.setElevation(dp(4));
        FrameLayout.LayoutParams woodParams = new FrameLayout.LayoutParams(
                -1, dp(31), Gravity.BOTTOM);
        woodParams.leftMargin = dp(2);
        woodParams.rightMargin = dp(2);
        woodParams.bottomMargin = dp(2);
        stage.addView(woodPlatform, woodParams);
        ImageView grain = new ImageView(context);
        grain.setImageResource(R.drawable.nixie_wood_grain);
        grain.setScaleType(ImageView.ScaleType.FIT_XY);
        woodPlatform.addView(grain, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout tubes = new LinearLayout(context);
        tubes.setGravity(Gravity.CENTER);
        tubes.setOrientation(HORIZONTAL);
        FrameLayout.LayoutParams tubeRowParams = new FrameLayout.LayoutParams(
                -1, dp(130), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tubeRowParams.bottomMargin = dp(19);
        tubeRowParams.leftMargin = dp(5);
        tubeRowParams.rightMargin = dp(5);
        stage.addView(tubes, tubeRowParams);

        for (int index = 0; index < digitViews.length; index++) {
            FrameLayout tube = createTube(context, accentColor, index);
            LayoutParams tubeParams = new LayoutParams(0, -1, 1f);
            tubeParams.leftMargin = dp(2);
            tubeParams.rightMargin = dp(2);
            tubes.addView(tube, tubeParams);
        }

        LinearLayout valueRow = new LinearLayout(context);
        valueRow.setGravity(Gravity.CENTER);
        valueRow.setOrientation(HORIZONTAL);
        valueView = text("— — — В", 21, Color.rgb(245, 237, 224), true);
        valueView.setShadowLayer(dp(5), 0, 0,
                Color.argb(75, Color.red(accentColor), Color.green(accentColor),
                        Color.blue(accentColor)));
        valueRow.addView(valueView);
        LayoutParams valueParams = new LayoutParams(-1, -2);
        valueParams.topMargin = dp(1);
        addView(valueRow, valueParams);

        TextView caption = text(label.equals("ВХОДНОЕ")
                ? "ВХОД ОТ ЭЛЕКТРОСЕТИ" : "СТАБИЛЬНЫЙ ВЫХОД ИБП",
                8, Color.rgb(177, 158, 133), false);
        caption.setGravity(Gravity.CENTER);
        LayoutParams captionParams = new LayoutParams(-1, -2);
        captionParams.topMargin = dp(1);
        addView(caption, captionParams);

        setContentDescription(label + ": показание недоступно");
    }

    private FrameLayout createTube(Context context, int accentColor, int index) {
        FrameLayout tube = new FrameLayout(context);
        tube.setBackgroundResource(R.drawable.nixie_tube_glass);
        tube.setElevation(dp(3));
        ImageView detail = new ImageView(context);
        detail.setImageResource(R.drawable.nixie_tube_detail);
        detail.setScaleType(ImageView.ScaleType.FIT_XY);
        tube.addView(detail, new FrameLayout.LayoutParams(-1, -1));

        TextView digit = text("-", 46, accentColor, false);
        digit.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        digit.setGravity(Gravity.CENTER);
        digit.setShadowLayer(dp(8), 0, 0, accentColor);
        digit.setIncludeFontPadding(false);
        FrameLayout.LayoutParams digitParams = new FrameLayout.LayoutParams(
                -1, dp(72), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        digitParams.topMargin = dp(20);
        tube.addView(digit, digitParams);
        digitViews[index] = digit;

        View socket = new View(context);
        socket.setBackgroundResource(R.drawable.nixie_tube_socket);
        FrameLayout.LayoutParams socketParams = new FrameLayout.LayoutParams(
                -1, dp(16), Gravity.BOTTOM);
        socketParams.leftMargin = dp(3);
        socketParams.rightMargin = dp(3);
        tube.addView(socket, socketParams);
        View pin = new View(context);
        pin.setBackground(roundRect(Color.rgb(177, 151, 110), dp(2),
                Color.rgb(216, 195, 160), dp(1)));
        FrameLayout.LayoutParams pinParams = new FrameLayout.LayoutParams(
                dp(4), dp(7), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tube.addView(pin, pinParams);
        return tube;
    }

    void setVoltage(double voltage) {
        if (Double.isNaN(voltage) || Double.isInfinite(voltage)) {
            hasVoltage = false;
            setContentDescription(getResources().getString(
                    R.string.nixie_voltage_missing, label));
            updateDigits("---");
            return;
        }

        float target = (float) Math.max(0, Math.min(999, voltage));
        targetVoltage = target;
        setContentDescription(getResources().getString(
                R.string.nixie_voltage_accessibility, label, voltage));
        if (!hasVoltage || !animationsEnabled || displayedVoltage == target) {
            cancelValueAnimator();
            displayedVoltage = target;
            hasVoltage = true;
            updateValue();
            return;
        }

        cancelValueAnimator();
        valueAnimator = ValueAnimator.ofFloat(displayedVoltage, target);
        valueAnimator.setDuration(650);
        valueAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        valueAnimator.addUpdateListener(animator -> {
            displayedVoltage = (float) animator.getAnimatedValue();
            updateValue();
        });
        valueAnimator.start();
    }

    void setSource(String value, int color) {
        sourceView.setText(value);
        sourceView.setTextColor(color);
    }

    void startAnimation() {
        if (!isAttachedToWindow() || animationsEnabled) {
            return;
        }
        animationsEnabled = true;
        glowAnimator = ValueAnimator.ofFloat(.82f, 1f);
        glowAnimator.setDuration(3200);
        glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.addUpdateListener(animator -> {
            float glow = (float) animator.getAnimatedValue();
            for (TextView digit : digitViews) {
                digit.setAlpha(glow);
            }
        });
        glowAnimator.start();
    }

    void stopAnimation() {
        animationsEnabled = false;
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
        cancelValueAnimator();
        if (hasVoltage) {
            displayedVoltage = targetVoltage;
            updateValue();
        }
        for (TextView digit : digitViews) {
            digit.setAlpha(1f);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void updateValue() {
        updateDigits(NixieDisplayMath.voltageDigits(displayedVoltage));
    }

    private void updateDigits(String digits) {
        if (digits.equals(displayedDigits)) {
            return;
        }
        for (int index = 0; index < digitViews.length; index++) {
            digitViews[index].setText(String.valueOf(digits.charAt(index)));
        }
        displayedDigits = digits;
        valueView.setText(getResources().getString(R.string.nixie_voltage_value, digits));
    }

    private void cancelValueAnimator() {
        if (valueAnimator != null) {
            valueAnimator.cancel();
            valueAnimator = null;
        }
    }

    private GradientDrawable cardBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(39, 33, 27), Color.rgb(20, 20, 20)});
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), Color.rgb(90, 68, 47));
        return background;
    }

    private GradientDrawable woodBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(172, 113, 64), Color.rgb(91, 56, 35),
                        Color.rgb(127, 77, 43), Color.rgb(55, 39, 29)});
        background.setCornerRadius(dp(5));
        background.setStroke(dp(1), Color.rgb(213, 164, 107));
        return background;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        background.setStroke(strokeWidth, strokeColor);
        return background;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
