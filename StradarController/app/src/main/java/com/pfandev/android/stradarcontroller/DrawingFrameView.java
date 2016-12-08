package com.pfandev.android.stradarcontroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawingFrameView extends View {

    private List<Point> mPoints = new ArrayList<>();
    private Paint mPaint;
    private Paint mPaintWhite;
    private Paint mPaintBlue;
    private boolean clear;

    public DrawingFrameView(Context context) {
        super(context);
    }

    public DrawingFrameView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPaint = new Paint();
        mPaint.setColor(Color.BLACK);

        mPaintWhite = new Paint();
        mPaintWhite.setColor(Color.WHITE);

        mPaintBlue = new Paint();
        mPaintBlue.setColor(Color.BLUE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isClear()) {
            canvas.drawColor(Color.WHITE);
            mPoints.clear();
            clear = false;
        } else {
            if (mPoints.size() > 0) {
                canvas.drawCircle((getWidth() / 2), (getHeight() - 10), 410, mPaintBlue);
                canvas.drawCircle((getWidth() / 2), (getHeight() - 10), 408, mPaintWhite);
                canvas.drawCircle((getWidth() / 2), (getHeight() - 10), 310, mPaintBlue);
                canvas.drawCircle((getWidth() / 2), (getHeight() - 10), 308, mPaintWhite);
                canvas.drawCircle((getWidth() / 2), (getHeight() - 10), 210, mPaintBlue);
                canvas.drawCircle((getWidth() / 2), (getHeight() - 10), 208, mPaintWhite);
                canvas.drawCircle((getWidth() / 2), (getHeight() - 10), 110, mPaintBlue);
                canvas.drawCircle((getWidth() / 2), (getHeight() - 10), 108, mPaintWhite);
                canvas.drawText("1 meter", (getWidth() / 2), (getHeight() - 125),
                        mPaint);
                canvas.drawText("2 meters", (getWidth() / 2), (getHeight() - 225),
                        mPaint);
                canvas.drawText("3 meters", (getWidth() / 2), (getHeight() - 325),
                        mPaint);
                canvas.drawText("4 meters", (getWidth() / 2), (getHeight() - 425),
                        mPaint);
            }
            for (int index = 0; index < mPoints.size(); index++) {
                if (mPoints.get(index).x != 0 || mPoints.get(index).y != 0) {
                    canvas.drawCircle(((getWidth() / 2) + mPoints.get(index).x),
                            (getHeight() - mPoints.get(index).y - 10), 3, mPaint);
                    if (index > 0) {
                        if (mPoints.get(index - 1).x != 0 || mPoints.get(index - 1).y != 0) {
                            canvas.drawLine(((getWidth() / 2) + mPoints.get(index).x),
                                    (getHeight() - mPoints.get(index).y - 10),
                                    ((getWidth() / 2) + mPoints.get(index - 1).x),
                                    (getHeight() - mPoints.get(index - 1).y - 10), mPaint);
                        }
                    }
                }
            }
        }
    }

    public List<Point> getPoints() {
        return mPoints;
    }

    public void setPoints(List<Point> points) {
        mPoints = points;
        invalidate();
    }

    public boolean isClear() {
        return clear;
    }

    public void setClear(boolean clear) {
        this.clear = clear;
        invalidate();
    }
}
