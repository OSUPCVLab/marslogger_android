package edu.osu.pcv.marslogger;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by sudamasayuki on 2018/03/14.
 */

public class SampleGLView extends GLSurfaceView {

    public SampleGLView(Context context) {
        this(context, null);
    }

    public SampleGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private TouchListener touchListener;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int actionMasked = event.getActionMasked();
        if (actionMasked != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (touchListener != null) {
            touchListener.onTouch(event);
        }
        return false;
    }

    public interface TouchListener {
        void onTouch(MotionEvent event);
    }

    public void setTouchListener(TouchListener touchListener) {
        this.touchListener = touchListener;
    }
}

