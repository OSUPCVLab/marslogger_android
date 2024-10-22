package sg.edu.nus.comp.android3dvisualisationtool.app.dataReader;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import sg.edu.nus.comp.android3dvisualisationtool.app.points.Point;

/**
 * Created by tang on 10/6/14.
 * class dataReader
 * read data from pcd file and return the points data
 */
public class DataReader {
    private static List<Point> points = null;
    private static Context context = null;

    public static void setContext(Context context) {
        DataReader.context = context;
    }

    /**
     * open file and read the points 
     */
    public static List<Point> openFile(String filename) {
        points = new ArrayList<Point>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(filename)));
            int numOfPoints = 0;

            //read number of points and skip other unused header entries
            for (int i = 0; i < 12; i++) {
                String[] temp = reader.readLine().split(" ");
                if (temp[0].equals("POINTS")) {
                    numOfPoints = Integer.parseInt(temp[1]);
                } else if (temp[0].equals("DATA")) {
                    break;
                }
            }

            for (int i = 0; i < numOfPoints; i++) {
                String[] coordinates = reader.readLine().split(" ");
                float x = (float) Double.parseDouble(coordinates[0]);
                float y = (float) Double.parseDouble(coordinates[1]);
                float z = (float) Double.parseDouble(coordinates[2]);

                if (coordinates.length == 7) {
                    float curvature = (float) Double.parseDouble(coordinates[3]);
                    float normal_x = (float) Double.parseDouble(coordinates[4]);
                    float normal_y = (float) Double.parseDouble(coordinates[5]);
                    float normal_z = (float) Double.parseDouble(coordinates[6]);
                    points.add(new Point(x, y, z, curvature, normal_x, normal_y, normal_z));
                } else if (coordinates.length == 6) {
                    float normal_x = (float) Double.parseDouble(coordinates[3]);
                    float normal_y = (float) Double.parseDouble(coordinates[4]);
                    float normal_z = (float) Double.parseDouble(coordinates[5]);
                    points.add(new Point(x, y, z, normal_x, normal_y, normal_z));
                } else if (coordinates.length == 4) {
                    float temp = (float) Double.parseDouble(coordinates[3]);
                    if (temp > 1) {
                        int color = (int) temp;
                        points.add(new Point(x, y, z, color));
                    } else {
                        points.add(new Point(x, y, z, temp));
                    }
                } else {
                    points.add(new Point(x, y, z));
                }
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            return points;
        }
    }

}
