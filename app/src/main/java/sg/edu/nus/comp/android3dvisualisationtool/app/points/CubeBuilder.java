package sg.edu.nus.comp.android3dvisualisationtool.app.points;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CubeBuilder {

    // Define the dimensions of the cube
    private static final float CUBE_WIDTH = 8.0f;
    private static final float CUBE_HEIGHT = 8.0f;
    private static final float CUBE_DEPTH = 3.0f;

    // Number of points to generate
    private static final int NUM_POINTS = 300;

    // Function to generate random points
    public static List<Point> generateRandomPointsInCube() {
        List<Point> points = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < NUM_POINTS; i++) {
            // Generate random x, y, z coordinates within the cube dimensions
            float x = random.nextFloat() * CUBE_WIDTH;    // Random x in range [0, 8]
            float y = random.nextFloat() * CUBE_HEIGHT;   // Random y in range [0, 8]
            float z = random.nextFloat() * CUBE_DEPTH;    // Random z in range [0, 3]

            // Set intensity (or any other value you'd like to associate, e.g., curvature)
            float intensity = 255.0f; // You can replace this with random values if needed

            // Create a point object
            Point point = new Point(x, y, z, intensity);

            // Add the point to the list
            points.add(point);
        }

        return points;
    }
}