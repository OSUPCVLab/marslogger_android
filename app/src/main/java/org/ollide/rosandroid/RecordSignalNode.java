package org.ollide.rosandroid;

import org.ros.concurrent.CancellableLoop;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;

import timber.log.Timber;

public class RecordSignalNode extends AbstractNodeMain {
    private static final String TAG = RecordSignalNode.class.getSimpleName();

    private static long maxFrequency = 10;
    private static long minElapse = 1000 / maxFrequency;
    private final String topic_name = "record_control";

    public static final String nodeName = "record_signal_node";
    private volatile String bag_name = "";
    private volatile boolean start_recording = false;
    private volatile boolean stop_recording = false;
    private volatile boolean is_recording = false;

    public RecordSignalNode() {
    }

    @Override
    public void onStart(final ConnectedNode connectedNode) {
        final Publisher<std_msgs.String> publisher = connectedNode.newPublisher(this.topic_name, std_msgs.String._TYPE);
        connectedNode.executeCancellableLoop(new CancellableLoop() {
            @Override
            protected void loop() throws InterruptedException {
                if (start_recording && !is_recording) {
                    std_msgs.String startMessage = publisher.newMessage();
                    startMessage.setData("start:" + bag_name);
                    publisher.publish(startMessage);
                    start_recording = false;
                    is_recording = true;
                    Timber.d("Started recording to %s", bag_name);
                }
                if (stop_recording && is_recording) {
                    std_msgs.String stopMessage = publisher.newMessage();
                    stopMessage.setData("stop");
                    publisher.publish(stopMessage);
                    stop_recording = false;
                    is_recording = false;
                    Timber.d("Stopped recording");
                }

                Thread.sleep(minElapse); // 100 ms = 10 Hz
            }
        });
    }

    public void startRecording(String bagname) {
        bag_name = bagname;
        stop_recording = false;
        start_recording = true;
    }

    public void stopRecording() {
        start_recording = false;
        stop_recording = true;
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("ros_android_sensors/record_signal_node");
    }
}
