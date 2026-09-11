package org.ollide.rosandroid;

import android.util.Log;

import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;

import java.util.ArrayList;

import edu.osu.pcv.marslogger.benchmark.PipelinePerformanceLogger;
import geometry_msgs.Point;
import nav_msgs.Odometry;
import nav_msgs.Path;
import sensor_msgs.PointCloud2;

public class RosListenerNode extends AbstractNodeMain {

    public static String TAG = "ros_listener";
    public static final String nodeName = "ros_listener_node";
    private Subscriber<Odometry> odom_subscriber;
    private Subscriber<PointCloud2> livox_pc_subscriber;
    private Subscriber<PointCloud2> pandar_pc_subscriber;
    private Subscriber<PointCloud2> lio_pc_subscriber;
    private int pc_count = 0;
    private boolean recording = false;
    private volatile PipelinePerformanceLogger performanceLogger;
    ArrayList<LocationUpdateListener> odom_listeners = new ArrayList<LocationUpdateListener>();
    ArrayList<FrameNumberListener> livox_pc_listeners = new ArrayList<FrameNumberListener>();
    ArrayList<WorldPCListener> world_pc_listeners = new ArrayList<WorldPCListener>();
    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(nodeName);
    }

    public void setOnLocationUpdateListener(LocationUpdateListener listener) {
        // Store the listener object
        this.odom_listeners.add(listener);
    }

    public void setOnFrameNumberListener(FrameNumberListener listener) {
        this.livox_pc_listeners.add(listener);
    }

    public void setOnWorldPCListener(WorldPCListener listener) {
        this.world_pc_listeners.add(listener);
    }

    public void setRecording(boolean record) {
        if (record) {
            pc_count = 0;
        }
        recording = record;
    }

    public void setPerformanceLogger(PipelinePerformanceLogger performanceLogger) {
        this.performanceLogger = performanceLogger;
    }

    private void recordRawFrame(PointCloud2 msg) {
        PipelinePerformanceLogger logger = performanceLogger;
        if (logger != null && logger.isActive()) {
            long frameId = ((long) msg.getHeader().getSeq()) & 0xffffffffL;
            long sensorTimestampNs = msg.getHeader().getStamp().totalNsecs();
            int pointCount = msg.getHeight() * msg.getWidth();
            logger.onRawFrameReceived(frameId, sensorTimestampNs, pointCount);
        }
    }

    @Override
    public void onStart(ConnectedNode node) {
//        final Log log = node.getLog();
        odom_subscriber = node.newSubscriber("/Odometry", "nav_msgs/Odometry");
        livox_pc_subscriber = node.newSubscriber("/livox/lidar", "sensor_msgs/PointCloud2");
        pandar_pc_subscriber = node.newSubscriber("/pandar", "sensor_msgs/PointCloud2");
        lio_pc_subscriber = node.newSubscriber("/cloud_registered", "sensor_msgs/PointCloud2");

        odom_subscriber.addMessageListener(new MessageListener<Odometry>() {
            @Override
            public void onNewMessage(Odometry msg) {
                Point point = msg.getPose().getPose().getPosition();
                for (LocationUpdateListener listener : odom_listeners) {
                    listener.onLocationUpdate(point.getX(), point.getY(), point.getZ());
                }
            }
        });

        livox_pc_subscriber.addMessageListener(new MessageListener<PointCloud2>() {
           @Override
           public void onNewMessage(PointCloud2 msg) {
               recordRawFrame(msg);
               if (!recording)
                   return;
               pc_count++;
               if (pc_count % 20 == 0) {
                   for (FrameNumberListener listener : livox_pc_listeners) {
                       listener.onFrameNumber(pc_count);
                   }
               }
           }
        });

        pandar_pc_subscriber.addMessageListener(new MessageListener<PointCloud2>() {
            @Override
            public void onNewMessage(PointCloud2 msg) {
                recordRawFrame(msg);
                if (!recording)
                    return;
                pc_count++;
                if (pc_count % 20 == 0) {
                    for (FrameNumberListener listener : livox_pc_listeners) {
                        listener.onFrameNumber(pc_count);
                    }
                }
            }
        });

        lio_pc_subscriber.addMessageListener(new MessageListener<PointCloud2>() {
            @Override
            public void onNewMessage(PointCloud2 pointCloud2) {
                for (WorldPCListener listener : world_pc_listeners) {
                    listener.onWorldPC(pointCloud2);
                }
            }
        });
    }
    
    public void shutdown() {
        odom_subscriber.shutdown();
        livox_pc_subscriber.shutdown();
        pandar_pc_subscriber.shutdown();
        lio_pc_subscriber.shutdown();
    }
}
