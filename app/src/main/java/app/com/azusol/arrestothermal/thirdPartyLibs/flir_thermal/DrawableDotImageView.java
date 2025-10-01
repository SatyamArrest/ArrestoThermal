package app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.flir.thermalsdk.image.Point;

import java.util.ArrayList;

import app.com.azusol.arrestothermal.ui.dialogs.PointCreatorDialog;


public class DrawableDotImageView extends AppCompatImageView implements View.OnTouchListener {
    public final ArrayList<Dot> dots = new ArrayList<>();
    public Paint strokePaint, tvPaint, stkPaint;
    public Dot touchedDot;
    public int spotRadius = 30;
    boolean isAllSpotChange;

    public DrawableDotImageView(@NonNull Context context) {
        super(context);
        setup();
    }

    public DrawableDotImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public DrawableDotImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    float xRatio;
    //    float yRatio;
    PointCreatorDialog pointCreatorDialog;

    public void setBitmapWidth(int width, ArrayList<Point> intialPoint, PointCreatorDialog pointCreatorDialog) {
        this.pointCreatorDialog = pointCreatorDialog;
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                xRatio = (float) width / getWidth();
                if (intialPoint != null && intialPoint.size() > 0) {
                    for (Point point : intialPoint) {
                        dots.add(new Dot(point.x / xRatio, point.y / xRatio, point.x, point.y));
                    }
                }
//                    yRatio = (float) bmp.getHeight() / getHeight();
            }
        });
    }

    private void setup() {
        setOnTouchListener(this);
        strokePaint = new Paint();
        strokePaint.setColor(Color.BLACK);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(5);

        float scale = getResources().getDisplayMetrics().density;
        tvPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tvPaint.setColor(Color.WHITE);
        tvPaint.setTextSize((int) (14 * scale));
//        tvPaint.setShadowLayer(5.0f,1.7f,1.5f,Color.BLACK);
        stkPaint = new Paint();
        stkPaint.setStyle(Paint.Style.STROKE);
        stkPaint.setStrokeWidth(1);
        stkPaint.setTextSize((int) (14 * scale));
        stkPaint.setColor(Color.BLACK);
    }

    public void setRadius(int radius) {
        isAllSpotChange = true;
        spotRadius = radius;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawEvent(canvas, false);
    }

    Canvas lastCanvas;

    public void drawEvent(Canvas canvas, boolean isbitmap) {
        Log.e("drawEvent", "run ");
        for (int i = 0; i < dots.size(); i++) {
            String dotName = "T" + (i + 1);
            Dot dot = dots.get(i);
            dot.setDotName(dotName);
            if (isAllSpotChange) {
                dot.radius = spotRadius;
            }
            float x, y;
            if (isbitmap) {
                x = dot.getBitmapX();
                y = dot.getBitmapY();
            } else {
                lastCanvas = canvas;
                x = dot.getX();
                y = dot.getY();
            }

            if (canvas.getWidth() < (x + dot.radius)) {
                x = canvas.getWidth() - dot.radius - 2;
            } else if (x - dot.radius < 1) {
                x = 1;
            }
            if (canvas.getHeight() < (y + dot.radius)) {
                y = canvas.getHeight() - dot.radius - 2;
            } else if (y - dot.radius < 1) {
                y = 1;
            }
            canvas.drawText(dotName, x + 5, y - dot.radius - 5, tvPaint);
            canvas.drawText(dotName, x + 5, y - dot.radius - 5, stkPaint);
            canvas.drawRect(x - dot.radius, y - dot.radius, x + dot.radius, y + dot.radius, strokePaint);
//            canvas.drawLine(x, y - dot.radius, x, y + dot.radius, strokePaint);
//            canvas.drawLine(x - dot.radius, y, x + dot.radius, y, strokePaint);
        }
        isAllSpotChange = false;
    }

    boolean goneFlag = false;

    //Put this into the class
    final Handler handler = new Handler();
    Runnable mLongPressed = new Runnable() {
        public void run() {
            goneFlag = true;
            //Code for long click
            Log.e("mLongPressed", " detected");
            dots.remove(touchedDot);
            touchedDot = null;
            invalidate();
//            touchedDot.
        }
    };

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                for (Dot dot : dots) {
                    if (dot.isInside(event.getX(), event.getY())) {
                        touchedDot = dot;
                        pointCreatorDialog.onDotSelect(dot);
                    }
                }
                handler.postDelayed(mLongPressed, 1000);
                break;
            case MotionEvent.ACTION_MOVE:
                handler.removeCallbacks(mLongPressed);
                if (touchedDot != null) {
                    if (!touchedDot.getDotName().equals("T1") && !touchedDot.getDotName().equals("T2")) {
                        if (isInside(v, event)) {
                            touchedDot.x = event.getX();
                            touchedDot.y = event.getY();
                            touchedDot.bitmapX = event.getX() * xRatio;
                            touchedDot.bitmapY = event.getY() * xRatio;
                            invalidate();
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(mLongPressed);
                if (!goneFlag) {
                    if (touchedDot != null) {
                        touchedDot = null;
                    } else {
//                    float[] points = getPointOfTouchedCordinate((ImageView) v, event);
                        if (isInside(v, event)) {
                            if (dots.size() > 7) {
                                Toast.makeText(getContext(), "You can't create more thermal spot!", Toast.LENGTH_LONG).show();
                            } else {
                                dots.add(new Dot(event.getX(), event.getY(), event.getX() * xRatio, event.getY() * xRatio));
                                invalidate();
                                Log.w("Dot created ", "calculated X: " + event.getX() * xRatio + "  Y: " + event.getY() * xRatio);
                                Log.w("ImageView", "Dot created X: " + event.getX() + " Y: " + event.getY());
                            }
                        }
                    }
                } else {
                    goneFlag = false;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(mLongPressed);
                break;
            default:
                break;
        }
        return true;
    }


    private boolean isInside(View v, MotionEvent e) {
        return !(e.getX() - 40 < 0 || e.getY() - 40 < 0
                || e.getX() + 40 > v.getMeasuredWidth()
                || e.getY() + 40 > v.getMeasuredHeight());
    }


//    final float[] getPointOfTouchedCordinate(ImageView view, MotionEvent e) {
//        final int index = e.getActionIndex();
//        final float[] coords = new float[]{e.getX(index), e.getY(index)};
//        Matrix m = new Matrix();
//        view.getImageMatrix().invert(m);
//        m.postTranslate(view.getScrollX(), view.getScrollY());
//        m.mapPoints(coords);
//        return coords;
//    }

}