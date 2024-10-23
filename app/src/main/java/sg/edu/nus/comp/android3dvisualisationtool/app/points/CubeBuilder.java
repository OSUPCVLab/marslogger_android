package sg.edu.nus.comp.android3dvisualisationtool.app.points;

import java.util.ArrayList;
import java.util.List;

public class CubeBuilder {

    // Function to generate the corner points of a cube with given size and intensity set to 255
    public static List<Point> cubeCorners(float size) {
        List<Point> lstPoint = new ArrayList<>();

        // 8 corner points of a cube (x, y, z combinations)
        float[][] corners = {
                {0, 0, 0},
                {0, 0, size},
                {0, size, 0},
                {0, size, size},
                {size, 0, 0},
                {size, 0, size},
                {size, size, 0},
                {size, size, size}
        };

        // Set a constant intensity (stored in curvature) of 255 for each point
        float intensity = 255;

        // Create Point objects for each corner and add to the list
        for (float[] corner : corners) {
            Point point = new Point(corner[0], corner[1], corner[2], intensity);
            lstPoint.add(point);
        }

        return lstPoint;
    }
}
