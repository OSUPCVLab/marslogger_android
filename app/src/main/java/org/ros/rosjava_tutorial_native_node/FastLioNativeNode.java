package org.ros.rosjava_tutorial_native_node;

import org.apache.commons.logging.Log;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.NativeNodeMain;
import org.ros.node.Node;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class FastLioNativeNode extends NativeNodeMain {
    private static final String libName = "fastlio_jni";
    public static final String nodeName = "fastlio";
    private Log mLog;
    private final Object lifecycleLock = new Object();
    private final CountDownLatch executionFinished = new CountDownLatch(1);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    public FastLioNativeNode() {
        super(libName);
    }

    public FastLioNativeNode(String[] remappingArguments) {
        super(libName, remappingArguments);
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(nodeName);
    }

    @Override
    protected int execute(String rosMasterUri, String rosHostname, String rosNodeName,
                          String[] remappingArguments) {
        try {
            return executeNative(rosMasterUri, rosHostname, rosNodeName, remappingArguments);
        } finally {
            executionFinished.countDown();
        }
    }

    private native int executeNative(String rosMasterUri, String rosHostname, String rosNodeName,
                                     String[] remappingArguments);

    @Override
    public int shutdown() {
        synchronized (lifecycleLock) {
            if (shutdownRequested.compareAndSet(false, true)) {
                return shutdownNative();
            }
            return SUCCESS;
        }
    }

    private native int shutdownNative();

    private native void prepareNative();

    /** Blocks until JNI has saved output and destroyed all native FAST-LIO state. */
    public void awaitTermination() throws InterruptedException {
        executionFinished.await();
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        mLog = connectedNode.getLog();
        synchronized (lifecycleLock) {
            prepareNative();
            // Preserve a stop request that raced with ROSJava's asynchronous onStart.
            if (shutdownRequested.get()) {
                shutdownNative();
            }
            super.onStart(connectedNode);
        }
    }

    @Override
    public void onError(Node node, Throwable throwable) {
        if (super.executeReturnCode != 0 && mLog != null) {
            mLog.error("FastLioNativeNode error code: " + Integer.toString(super.executeReturnCode), throwable);
        }
    }
}
