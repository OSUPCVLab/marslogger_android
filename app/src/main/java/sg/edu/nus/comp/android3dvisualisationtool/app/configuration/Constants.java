package sg.edu.nus.comp.android3dvisualisationtool.app.configuration;

/**
 * Created by panlong on 11/6/14.
 * interface Constants
 * Global constants used in Visualisation tool
 */
public interface Constants {

    static final String TITLE = "3D Visualisation Tool";
    static final double DEFAULT_CAMERA_DISTANCE = 35;
    static final double DEFAULT_FIELD_OF_VIEW = 30;
    static final double DEFAULT_MAX_ABS_COORIDINATE = 10;
    static final double DEFAULT_PRECISION = 0.05;
    static final double DEFAULT_MAX_SELECTED_CURVATURE = 1;
    static final double DEFAULT_MIN_SELECTED_CURVATURE = 0;
    static final double DEFAULT_SELECTED_CURVATURE = 0.5;
    static final double DEFAULT_LOOK_AT_POINT_X = 0;
    static final double DEFAULT_LOOK_AT_POINT_Y = 0;
    static final double DEFAULT_CAMERA_NEAR_CLIP = 0.1;
    static final double DEFAULT_CAMERA_FAR_CLIP = 10000;
    static final double DEFAULT_NORMAL_VECTOR_LENGTH = 20;
    static final int DEFAULT_SLIDER_MIN = 0;
    static final int DEFAULT_SLIDER_MAX = 60;
    static final float DEFAULT_SLIDER_VALUE = 30f;
    static final int DEFAULT_MAJOR_TICK_SPACING = 10;
    static final int DEFAULT_MINOR_TICK_SPACING = 5;
    static final int DEFAULT_NUMBER_OF_TICK = 7;
    static final int DEFAULT_NUMBER_OF_TICK_CURVATURE = 6;
    static final int DEFAULT_CAMERA_ANGLE_X = 30;
    static final int DEFAULT_CAMERA_ANGLE_Y = 20;
    static final boolean DEFAULT_IS_AXES_VISIBLE = true;
    static final boolean DEFAULT_IS_SET_TO_ORIGIN = false;
    static final boolean DEFAULT_IS_SELECTING_CURVATURE = false;
    static final boolean DEFAULT_IS_NORMAL_VECTOR_VISIBLE = false;
    static final boolean DEFAULT_POINTS_CONTAINS_NORMAL_VECTOR = false;
    static final int DEFAULT_SLEEP_TIME = 100;
    static final float[] DEFAULT_COLOR = {0.63671875f, 0.76953125f, 0.22265625f, 0.0f};
    static final float[] CURVATURE_COLOR = {1f, 0f, 0f, 0f};
}
