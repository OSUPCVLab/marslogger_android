package org.ros.rosjava_tutorial_native_node;

import org.apache.commons.logging.Log;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.NativeNodeMain;
import org.ros.node.Node;

/**
 * Class to implement the laser scan logger native node.
 **/
public class LaserLoggerNativeNode extends NativeNodeMain {
    private static final String libName = "laser_logger_jni";
    public static final String nodeName = "laser_logger";
    private Log mLog;
    public LaserLoggerNativeNode() {
        super(libName);
    }

    public LaserLoggerNativeNode(String[] remappingArguments) {
        super(libName, remappingArguments);
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(nodeName);
    }

    @Override
    protected native int execute(String rosMasterUri, String rosHostname, String rosNodeName, String[] remappingArguments);

    @Override
    public native int shutdown();

    @Override
    public void onStart(ConnectedNode connectedNode) {
        mLog = connectedNode.getLog();
        super.onStart(connectedNode);
    }

    @Override
    public void onError(Node node, Throwable throwable) {
        if (super.executeReturnCode != 0 && mLog != null) {
            mLog.error("LaserLoggerNativeNode error code: " + Integer.toString(super.executeReturnCode), throwable);
        }
    }
}
