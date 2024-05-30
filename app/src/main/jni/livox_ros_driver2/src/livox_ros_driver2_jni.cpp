#include <android/log.h>
#include <ros/ros.h>

#include "livox_ros_driver2_jni.h"
#include <iostream>
#include <chrono>
#include <vector>
#include <csignal>
#include <thread>
#include <sstream>
#include <std_msgs/String.h>
#define BUILDING_ROS1
#include "livox_ros_driver2/livox_ros_driver2.h"
#include "livox_ros_driver2/driver_node.h"
#include "livox_ros_driver2/lddc_top.h"
#include "livox_ros_driver2/comm/comm.h"

using namespace livox_ros;

inline void log(const char *msg, ...) {
    va_list args;
    va_start(args, msg);
    __android_log_vprint(ANDROID_LOG_INFO, "Native_Livox_Ros_Driver2", msg, args);
    va_end(args);
}

inline std::string stdStringFromjString(JNIEnv *env, jstring java_string) {
    const char *tmp = env->GetStringUTFChars(java_string, NULL);
    std::string out(tmp);
    env->ReleaseStringUTFChars(java_string, tmp);
    return out;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    log("Livox ROS Driver2 library has been loaded");
    // Return the JNI version
    return JNI_VERSION_1_6;
}

/*
 * Class:     org_ros_rosjava_tutorial_native_node_LivoxRosDriver2NativeNode
 * Method:    execute
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_LivoxRosDriver2NativeNode_execute(
      JNIEnv *env, jobject obj, jstring rosMasterUri, jstring rosHostname, jstring rosNodeName,
      jobjectArray remappingArguments) {
  log("Native livox ros driver2 node started.");

  std::string master("__master:=" + stdStringFromjString(env, rosMasterUri));
  std::string hostname("__ip:=" + stdStringFromjString(env, rosHostname));
  std::string node_name(stdStringFromjString(env, rosNodeName));

  log(master.c_str());
  log(hostname.c_str());
  std::string nnmsg = "livox ros driver2 native nodename " + node_name;
  log(nnmsg.c_str());
  // Parse remapping arguments
  jsize len = env->GetArrayLength(remappingArguments);

  std::string ni = "livox_ros_driver2_cpp";

  int argc = 0;
  const int static_params = 4;
  char **argv = new char *[static_params + len];
  argv[argc++] = const_cast<char *>(ni.c_str());
  argv[argc++] = const_cast<char *>(master.c_str());
  argv[argc++] = const_cast<char *>(hostname.c_str());

  //Lookout: ros::init modifies argv, so the references to JVM allocated strings must be kept in some other place to avoid "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr deadbaad"
  // when trying to free the wrong reference ( see https://github.com/ros/ros_comm/blob/indigo-devel/clients/roscpp/src/libros/init.cpp#L483 )
  char **refs = new char *[len];
  for (int i = 0; i < len; i++) {
      refs[i] = (char *) env->GetStringUTFChars(
              (jstring) env->GetObjectArrayElement(remappingArguments, i), NULL);
      argv[argc] = refs[i];
      argc++;
  }
  std::string user_config_path((char *) env->GetStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, 0), NULL));
  ros::init(argc, &argv[0], node_name.c_str());

  // Release JNI UTF characters
  for (int i = 0; i < len; i++) {
      env->ReleaseStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, i),
                                 refs[i]);
  }
  delete []refs;
  delete []argv;

  livox_ros::DriverNode livox_node;
  DRIVER_INFO(livox_node, "Livox Ros Driver2 Version: %s", LIVOX_ROS_DRIVER2_VERSION_STRING);

  /** Init default system parameter */
  int xfer_format = kPointCloud2Msg;
  int multi_topic = 0;
  int data_src = kSourceRawLidar;
  double publish_freq  = 10.0; /* Hz */
  int output_type      = kOutputToRos;
  std::string frame_id = "livox_frame";
  bool lidar_bag = true;
  bool imu_bag   = false;

  livox_node.GetNode().getParam("xfer_format", xfer_format);
  livox_node.GetNode().getParam("multi_topic", multi_topic);
  livox_node.GetNode().getParam("data_src", data_src);
  livox_node.GetNode().getParam("publish_freq", publish_freq);
  livox_node.GetNode().getParam("output_data_type", output_type);
  livox_node.GetNode().getParam("frame_id", frame_id);
  livox_node.GetNode().getParam("enable_lidar_bag", lidar_bag);
  livox_node.GetNode().getParam("enable_imu_bag", imu_bag);

  printf("data source:%u.\n", data_src);

  if (publish_freq > 100.0) {
    publish_freq = 100.0;
  } else if (publish_freq < 0.5) {
    publish_freq = 0.5;
  } else {
    publish_freq = publish_freq;
  }

  livox_node.setFuture();

  livox_node.setLddc(xfer_format, multi_topic, data_src, output_type,
                        publish_freq, frame_id, lidar_bag, imu_bag);

  if (data_src == kSourceRawLidar) {
    DRIVER_INFO(livox_node, "Data Source is raw lidar.");

    DRIVER_INFO(livox_node, "Config file : %s", user_config_path.c_str());

    livox_node.registerLds(publish_freq, user_config_path);
  } else {
    DRIVER_ERROR(livox_node, "Invalid data src (%d), please check the launch file", data_src);
  }

  livox_node.pointclouddata_poll_thread_ = std::make_shared<std::thread>(&DriverNode::PointCloudDataPollThread, &livox_node);
  livox_node.imudata_poll_thread_ = std::make_shared<std::thread>(&DriverNode::ImuDataPollThread, &livox_node);

  // for debug purposes, we use ros::spinOnce(). Otherwise,ros::spin() without while loop is enough.
  ros::Rate loop_rate(50);
  while (ros::ok()) {
      ros::spinOnce();
      loop_rate.sleep();
  }

  log("Exiting from livox ros driver2 JNI call.");
  return 0;
}

/*
 * Class:     org_ros_rosjava_tutorial_native_node_LivoxRosDriver2NativeNode
 * Method:    shutdown
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_LivoxRosDriver2NativeNode_shutdown
  (JNIEnv *, jobject) {
  log("Shutting down livox ros driver2 native node.");
  ros::shutdown();
  return 0;
}

