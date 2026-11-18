package com.mevolthome.upsmonitor;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

final class VoltageGaugeView extends View {
    private final int accentColor;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pivotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float targetVoltage;
    private float needleAngle = 180f;
    private float glowIntensity = 0.75f;
    private boolean hasVoltage;
    private boolean animationsEnabled;
    private ValueAnimator needleAnimator;
    private ValueAnimator glowAnimator;

    public VoltageGaugeView(Context context) {
        this(context, null, Color.rgb(88, 187, 255));
    }

    public VoltageGaugeView(Context context, AttributeSet attributes) {
        this(context, attributes, Color.rgb(88, 187, 255));
    }

    VoltageGaugeView(Context context, int accentColor) {
        this(context, null, accentColor);
    }

    private VoltageGaugeView(Context context, AttributeSet attributes, int accentColor) {
        super(context, attributes);
        this.accentColor = accentColor;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.rgb(42, 63, 81));
        trackPaint.setStrokeWidth(dp(8));

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        activePaint.setStyle(Paint.Style.STROKE);
        activePaint.setStrokeCap(Paint.Cap.ROUND);
        activePaint.setColor(accentColor);
        activePaint.setStrokeWidth(dp(5));

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setColor(Color.rgb(120, 146, 169));
        tickPaint.setStrokeWidth(dp(1));

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
        needlePaint.setColor(accentColor);
        needlePaint.setStrokeWidth(dp(2.5f));

        pivotPaint.setStyle(Paint.Style.FILL);
        pivotPaint.setColor(Color.rgb(230, 248, 255));
    }

    void setVoltage(double voltage) {
        targetVoltage = (float) voltage;
        float targetAngle = VoltageGaugeMath.angleForVoltage(targetVoltage);
        setContentDescription(String.format(Locale.getDefault(),
                "Напряжение %.1f вольт", voltage));

        if (!hasVoltage || !animationsEnabled) {
            cancelNeedleAnimator();
            needleAngle = targetAngle;
            hasVoltage = true;
            invalidate();
            return;
        }

        cancelNeedleAnimator();
        needleAnimator = ValueAnimator.ofFloat(needleAngle, targetAngle);
        needleAnimator.setDuration(850);
        needleAnimator.setInterpolator(new DecelerateInterpolator(1.4f));
        needleAnimator.addUpdateListener(animator -> {
            needleAngle = (float) animator.getAnimatedValue();
            invalidate();
        });
        needleAnimator.start();
    }

    void startAnimation() {
        if (!isAttachedToWindow() || animationsEnabled) {
            return;
        }
        animationsEnabled = true;
        glowAnimator = ValueAnimator.ofFloat(0.72f, 1f);
        glowAnimator.setDuration(2800);
        glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.addUpdateListener(animator -> {
            glowIntensity = (float) animator.getAnimatedValue();
            invalidate();
        });
        glowAnimator.start();
    }

    void stopAnimation() {
        animationsEnabled = false;
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
        cancelNeedleAnimator();
        if (hasVoltage) {
            needleAngle = VoltageGaugeMath.angleForVoltage(targetVoltage);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2f;
        float baseline = getHeight() - dp(7);
        float radius = Math.min(getWidth() / 2f - dp(15), baseline - dp(12));
        if (radius <= 0f) {
            return;
        }

        arcRect.set(centerX - radius, baseline - radius,
                centerX + radius, baseline + radius);
        canvas.drawArc(arcRect, 180f, 180f, false, trackPaint);

        float sweep = Math.max(0f, Math.min(180f, needleAngle - 180f));
        if (sweep > 0f) {
            int glowAlpha = Math.round(33f * glowIntensity);
            glowPaint.setColor((glowAlpha << 24) | (accentColor & 0x00ffffff));
            glowPaint.setStrokeWidth(dp(15));
            canvas.drawArc(arcRect, 180f, sweep, false, glowPaint);
            activePaint.setAlpha(Math.round(190f + 65f * glowIntensity));
            canvas.drawArc(arcRect, 180f, sweep, false, activePaint);
            activePaint.setAlpha(255);
        }

        for (int tick = 0; tick <= 10; tick++) {
            double angle = Math.toRadians(180f + tick * 18f);
            float outerRadius = radius - dp(2);
            float innerRadius = radius - dp(tick % 5 == 0 ? 10 : 6);
            float startX = centerX + (float) Math.cos(angle) * innerRadius;
            float startY = baseline + (float) Math.sin(angle) * innerRadius;
            float endX = centerX + (float) Math.cos(angle) * outerRadius;
            float endY = baseline + (float) Math.sin(angle) * outerRadius;
            canvas.drawLine(startX, startY, endX, endY, tickPaint);
        }

        double needleRadians = Math.toRadians(needleAngle);
        float needleLength = radius - dp(15);
        float needleX = centerX + (float) Math.cos(needleRadians) * needleLength;
        float needleY = baseline + (float) Math.sin(needleRadians) * needleLength;
        canvas.drawLine(centerX, baseline, needleX, needleY, needlePaint);
        canvas.drawCircle(centerX, baseline, dp(4.5f), pivotPaint);
        pivotPaint.setColor(accentColor);
        canvas.drawCircle(centerX, baseline, dp(2), pivotPaint);
        pivotPaint.setColor(Color.rgb(230, 248, 255));
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void cancelNeedleAnimator() {
        if (needleAnimator != null) {
            needleAnimator.cancel();
            needleAnimator = null;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
