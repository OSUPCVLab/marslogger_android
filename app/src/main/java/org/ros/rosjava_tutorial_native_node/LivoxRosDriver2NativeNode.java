package org.ros.rosjava_tutorial_native_node;

import org.apache.commons.logging.Log;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.NativeNodeMain;
import org.ros.node.Node;

public class LivoxRosDriver2NativeNode extends NativeNodeMain {
    private static final String libName = "livox_ros_driver2_jni";
    public static final String nodeName = "livox_ros_driver2";
    private Log mLog;
    public LivoxRosDriver2NativeNode() {
        super(libName);
    }

    public LivoxRosDriver2NativeNode(String[] remappingArguments) {
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
            mLog.error("LivoxRosDriver2NativeNode error code: " + Integer.toString(super.executeReturnCode), throwable);
        }
    }

    public static String createLidarConfig(String hostip, String lidarip) {
        String config =
"{\n" +
"  \"lidar_summary_info\" : {\n" +
"    \"lidar_type\": 8\n" +
"  },\n" +
"  \"MID360\": {\n" +
"    \"lidar_net_info\" : {\n" +
"      \"cmd_data_port\": 56100,\n" +
"      \"push_msg_port\": 56200,\n" +
"      \"point_data_port\": 56300,\n" +
"      \"imu_data_port\": 56400,\n" +
"      \"log_data_port\": 56500\n" +
"    },\n" +
"    \"host_net_info\" : {\n" +
"      \"cmd_data_ip\" : \"" + hostip + "\",\n" +
"      \"cmd_data_port\": 56101,\n" +
"      \"push_msg_ip\": \"" + hostip + "\",\n" +
"      \"push_msg_port\": 56201,\n" +
"      \"point_data_ip\": \"" + hostip + "\",\n" +
"      \"point_data_port\": 56301,\n" +
"      \"imu_data_ip\" : \"" + hostip + "\",\n" +
"      \"imu_data_port\": 56401,\n" +
"      \"log_data_ip\" : \"\",\n" +
"      \"log_data_port\": 56501\n" +
"    }\n" +
"  },\n" +
"  \"lidar_configs\" : [\n" +
"    {\n" +
"      \"ip\" : \"" + lidarip + "\",\n" +
"      \"pcl_data_type\" : 1,\n" +
"      \"pattern_mode\" : 0,\n" +
"      \"extrinsic_parameter\" : {\n" +
"        \"roll\": 0.0,\n" +
"        \"pitch\": 0.0,\n" +
"        \"yaw\": 0.0,\n" +
"        \"x\": 0,\n" +
"        \"y\": 0,\n" +
"        \"z\": 0\n" +
"      }\n" +
"    }\n" +
"  ]\n" +
"}\n";
        return config;
    }
}
