package sg.edu.nus.comp.android3dvisualisationtool.app.axis;

import sg.edu.nus.comp.android3dvisualisationtool.app.configuration.Constants;

/**
 * Created by panlong on 17/6/14.
 */
public class Axes implements Constants {
    private static Axis xAxis = null;
    private static Axis yAxis = null;
    private static Axis zAxis = null;

    private static float mLength, mWidth;

    /**
     * only reinitiate axes if it has been changed
     */
    public static void draw(float[] mvpMatrix, float length, float width) {
        if (xAxis == null || mLength != length || mWidth != width) {
            xAxis = new Axis(length, width, width, new float[]{1f, 0f, 0f, 1f});
        }
        if (yAxis == null || mLength != length || mWidth != width) {
            yAxis = new Axis(width, length, width, new float[]{0f, 1f, 0f, 1f});
        }
        if (zAxis == null || mLength != length || mWidth != width) {
            zAxis = new Axis(width, width, length, new float[]{0f, 0f, 1f, 1f});

        }

        mLength = length;
        mWidth = width;

        xAxis.draw(mvpMatrix);
        yAxis.draw(mvpMatrix);
        zAxis.draw(mvpMatrix);
    }
}
