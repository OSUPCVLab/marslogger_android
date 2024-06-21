package org.ollide.rosandroid;

import android.util.Log;

import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;

import java.util.ArrayList;

import geometry_msgs.Point;
import nav_msgs.Odometry;
import nav_msgs.Path;
import sensor_msgs.PointCloud2;

public class PathListenerNode extends AbstractNodeMain {

    public static String TAG = "path_listener";
    public static final String nodeName = "path_listener_node";
    private Subscriber<Path> path_subscriber;
    private Subscriber<Odometry> odom_subscriber;
    private Subscriber<PointCloud2> pc_subscriber;
    private int pc_count = 0;
    private boolean recording = false;
    ArrayList<NewsUpdateListener> listeners = new ArrayList<NewsUpdateListener>();
    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(nodeName);
    }

    public void setOnNewsUpdateListener(NewsUpdateListener listener)
    {
        // Store the listener object
        this.listeners.add(listener);
    }

    public void setRecording(boolean record) {
        if (record) {
            pc_count = 0;
        }
        recording = record;
    }

    @Override
    public void onStart(ConnectedNode node) {
//        final Log log = node.getLog();
        path_subscriber = node.newSubscriber("/move_base/DWAPlannerROS/local_plan", "nav_msgs/Path");
        odom_subscriber = node.newSubscriber("/Odometry", "nav_msgs/Odometry");
        pc_subscriber = node.newSubscriber("/livox/lidar", "sensor_msgs/PointCloud2");
        Subscriber<Path> subscriber2 = node.newSubscriber("/move_base/DWAPlannerROS/global_plan", "nav_msgs/Path");
        Subscriber<Path> subscriber3 = node.newSubscriber("/move_base/NavfnROS/plan", "nav_msgs/Path");
//        path_subscriber.addMessageListener(new MessageListener<Path>() {
//            @Override
//            public void onNewMessage(Path message) {
//                Log.d(TAG, "New local plan path is here!");
//            }
//        });
        odom_subscriber.addMessageListener(new MessageListener<Odometry>() {
            @Override
            public void onNewMessage(Odometry msg) {
                Point point = msg.getPose().getPose().getPosition();
                for (NewsUpdateListener listener : listeners) {
                    listener.onNewsUpdate(point.getX(), point.getY(), point.getZ());
                }
            }
        });

        pc_subscriber.addMessageListener(new MessageListener<PointCloud2>() {
           @Override
           public void onNewMessage(PointCloud2 msg) {
               if (!recording)
                   return;
               pc_count++;
               if (pc_count % 20 == 0) {
                   for (NewsUpdateListener listener : listeners) {
                       listener.onNumFrameUpdate(pc_count);
                   }
               }
           }
        });
    }
    public void shutdown() {
        path_subscriber.shutdown();
        odom_subscriber.shutdown();
    }
}
