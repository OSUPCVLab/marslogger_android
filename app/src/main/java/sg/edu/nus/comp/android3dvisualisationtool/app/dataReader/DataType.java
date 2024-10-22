package sg.edu.nus.comp.android3dvisualisationtool.app.dataReader;

/**
 * Created by tang on 10/6/14.
 * supported data type
 * XYZ: x, y, z coordinates
 * XYZC: x, y, z coordinates and curvature
 * XYZRGB: x, y, z coordinates and RGB value
 * XYZNORMAL: x, y, z coordinates and normal vector
 * XYZCNORMAL: x, y, z coordinates, curvature and normal vector
 */
public enum DataType {
    XYZ, XYZC, XYZRGB, XYZNORMAL, XYZCNORMAL
}
