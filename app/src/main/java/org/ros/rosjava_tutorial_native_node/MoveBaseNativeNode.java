package org.ros.rosjava_tutorial_native_node;

import org.apache.commons.logging.Log;
import org.ros.node.NativeNodeMain;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;

/**
 * Class to implement a movebase native node.
 **/
public class MoveBaseNativeNode extends NativeNodeMain {
    private static final String libName = "movebase_jni";
    public static final String nodeName = "move_base";
    private Log mLog;

    public MoveBaseNativeNode() {
        super(libName);
    }

    public MoveBaseNativeNode(String[] remappingArguments) {
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

    public native void setCancelGoals();

    @Override
    public void onStart(ConnectedNode connectedNode) {
        mLog = connectedNode.getLog();
        super.onStart(connectedNode);
    }

    @Override
    public void onError(Node node, Throwable throwable) {
        if (super.executeReturnCode != 0 && mLog != null) {
            mLog.error("MoveBaseNativeNode error code: " + Integer.toString(super.executeReturnCode), throwable);
        }
    }
}
