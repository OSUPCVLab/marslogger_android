package org.ollide.rosandroid;

import android.util.Log;

import sensor_msgs.PointCloud2;
import sensor_msgs.PointField;

import sg.edu.nus.comp.android3dvisualisationtool.app.points.Point;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class PCConverter {

    public static List<Point> toPointList(PointCloud2 msg) {
        List<Point> lstPoint = new ArrayList<>();
        int seq = msg.getHeader().getSeq();

        int pointStep = msg.getPointStep();
        int numPoints = msg.getHeight() * msg.getWidth();
        org.jboss.netty.buffer.ChannelBuffer buffer = msg.getData();

        // Get the offsets for x, y, z, and intensity fields
        int xOffset = -1, yOffset = -1, zOffset = -1, intensityOffset = -1;

        for (PointField field : msg.getFields()) {
            switch (field.getName()) {
                case "x":
                    xOffset = field.getOffset();
                    break;
                case "y":
                    yOffset = field.getOffset();
                    break;
                case "z":
                    zOffset = field.getOffset();
                    break;
                case "intensity":
                    intensityOffset = field.getOffset();
                    break;
            }
        }

        // Ensure that the essential fields are present
        if (xOffset == -1 || yOffset == -1 || zOffset == -1 || intensityOffset == -1) {
            throw new RuntimeException("PointCloud2 does not have all required fields: x, y, z, intensity");
        }
        // Iterate through each point
        for (int i = 0; i < numPoints; i++) {
            int pointStart = i * pointStep;

            // Read the x, y, and z coordinates (assuming float type)
            float x = buffer.getFloat(pointStart + xOffset);
            float y = buffer.getFloat(pointStart + yOffset);
            float z = buffer.getFloat(pointStart + zOffset);
            float intensity = buffer.getFloat(pointStart + intensityOffset);

            // Create a Point object with x, y, z, and intensity as curvature
            Point point = new Point(x, y, z, intensity, seq);
            lstPoint.add(point);
        }

        return lstPoint;
    }
}
