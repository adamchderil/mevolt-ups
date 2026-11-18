package com.mevolthome.upsmonitor;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

final class LightningOverlayView extends View {
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ambientGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boltGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path boltPath = new Path();
    private ValueAnimator animator;
    private RadialGradient ambientGradient;
    private float animationProgress;
    private boolean animationEnabled;

    LightningOverlayView(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        backgroundPaint.setColor(Color.rgb(7, 16, 28));
        gridPaint.setColor(Color.argb(9, 99, 196, 231));
        gridPaint.setStrokeWidth(dp(1));

        boltPath.moveTo(dp(11), dp(1));
        boltPath.lineTo(dp(3), dp(15));
        boltPath.lineTo(dp(9), dp(15));
        boltPath.lineTo(dp(6), dp(29));
        boltPath.lineTo(dp(19), dp(11));
        boltPath.lineTo(dp(13), dp(11));
        boltPath.close();
    }

    void startAnimation() {
        if (!isAttachedToWindow() || animationEnabled) {
            return;
        }
        animationEnabled = true;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(18000);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(valueAnimator -> {
            animationProgress = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    void stopAnimation() {
        animationEnabled = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        drawAmbientGlow(canvas);
        drawGrid(canvas);
        drawBolt(canvas, 0.16f, 0f, 7f, -10f, 0.72f, Color.rgb(105, 234, 255));
        drawBolt(canvas, 0.42f, 2.8f, 8f, 8f, 0.58f, Color.rgb(83, 207, 255));
        drawBolt(canvas, 0.69f, 5.2f, 9f, -7f, 0.63f, Color.rgb(106, 255, 219));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        ambientGradient = new RadialGradient(
                width * 0.52f,
                height * 0.2f,
                Math.max(width, height) * 0.72f,
                new int[]{Color.argb(42, 12, 75, 105), Color.TRANSPARENT},
                null,
                Shader.TileMode.CLAMP);
        ambientGlowPaint.setShader(ambientGradient);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void drawAmbientGlow(Canvas canvas) {
        if (ambientGradient != null) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), ambientGlowPaint);
        }
    }

    private void drawGrid(Canvas canvas) {
        float spacing = dp(24);
        for (float x = 0; x < getWidth(); x += spacing) {
            canvas.drawLine(x, 0, x, getHeight() * 0.86f, gridPaint);
        }
        for (float y = 0; y < getHeight() * 0.86f; y += spacing) {
            canvas.drawLine(0, y, getWidth(), y, gridPaint);
        }
    }

    private void drawBolt(Canvas canvas, float verticalPosition, float phaseOffset,
                          float duration, float rotation, float scale, int color) {
        float elapsed = animationProgress * 18f + phaseOffset;
        float phase = (elapsed % duration) / duration;
        float alpha;
        if (phase < 0.08f) {
            alpha = phase / 0.08f;
        } else if (phase > 0.84f) {
            alpha = (1f - phase) / 0.16f;
        } else {
            alpha = 1f;
        }
        if (alpha <= 0f) {
            return;
        }

        float x = -dp(28) + (getWidth() + dp(56)) * phase;
        float y = getHeight() * verticalPosition - dp(32) * phase;
        float drawingScale = scale;
        int alphaValue = Math.round(42f * alpha);
        boltGlowPaint.setStyle(Paint.Style.STROKE);
        boltGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        boltGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        boltGlowPaint.setStrokeWidth(dp(5));
        boltGlowPaint.setColor((alphaValue << 24) | (color & 0x00ffffff));
        boltPaint.setStyle(Paint.Style.FILL);
        boltPaint.setColor((Math.round(30f * alpha) << 24)
                | (color & 0x00ffffff));

        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.scale(drawingScale, drawingScale);
        canvas.drawPath(boltPath, boltGlowPaint);
        canvas.drawPath(boltPath, boltPaint);
        canvas.restore();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
