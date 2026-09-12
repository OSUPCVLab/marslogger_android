#include <android/log.h>
#include <ros/ros.h>

#include <atomic>
#include <mutex>
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

namespace {
std::atomic<bool> shutdown_requested{false};
std::mutex execution_mutex;
livox_ros::DriverNode *livox_node_ptr = nullptr;
}

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

void recordCallback(const std_msgs::String::ConstPtr& msg) {
    if (livox_node_ptr == nullptr) {
        return;
    }
    if (msg->data.find("start") == 0) {  // Check if the message starts with "start"
        ROS_INFO("Start recording...");
        size_t colon_pos = msg->data.find(':');
        if (colon_pos != std::string::npos) {
            std::string fn = msg->data.substr(colon_pos + 1);  // Extract filename after colon
            livox_node_ptr->StartRecording(fn);
        } else {
            ROS_WARN("Invalid command format, missing ':' to separate filename.");
        }
    } else if (msg->data == "stop") {
        ROS_INFO("Stop recording...");
        livox_node_ptr->StopRecording();
    } else {
        ROS_INFO("Unknown command: %s", msg->data.c_str());
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    log("Livox ROS Driver2 library has been loaded");
    // Return the JNI version
    return JNI_VERSION_1_6;
}

/*
 * Class:     org_ros_rosjava_tutorial_native_node_LivoxRosDriver2NativeNode
 * Method:    executeNative
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_LivoxRosDriver2NativeNode_executeNative(
      JNIEnv *env, jobject obj, jstring rosMasterUri, jstring rosHostname, jstring rosNodeName,
      jobjectArray remappingArguments) {
  std::unique_lock<std::mutex> execution_lock(execution_mutex);
  log("Native livox ros driver2 node started.");

  std::string master("__master:=" + stdStringFromjString(env, rosMasterUri));
  std::string hostname("__ip:=" + stdStringFromjString(env, rosHostname));
  std::string node_name(stdStringFromjString(env, rosNodeName));

  log("%s", master.c_str());
  log("%s", hostname.c_str());
  std::string nnmsg = "livox ros driver2 native nodename " + node_name;
  log("%s", nnmsg.c_str());
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
  jstring *java_refs = new jstring[len];
  for (int i = 0; i < len; i++) {
      java_refs[i] = static_cast<jstring>(env->GetObjectArrayElement(remappingArguments, i));
      refs[i] = (char *) env->GetStringUTFChars(java_refs[i], NULL);
      argv[argc] = refs[i];
      argc++;
  }
  jstring config_arg = static_cast<jstring>(env->GetObjectArrayElement(remappingArguments, 0));
  std::string user_config_path = stdStringFromjString(env, config_arg);
  env->DeleteLocalRef(config_arg);
  const std::string unique_node_name = node_name + "_" + std::to_string(
      std::chrono::steady_clock::now().time_since_epoch().count());
  ros::init(argc, &argv[0], unique_node_name.c_str());

  // Release JNI UTF characters
  for (int i = 0; i < len; i++) {
      env->ReleaseStringUTFChars(java_refs[i], refs[i]);
      env->DeleteLocalRef(java_refs[i]);
  }
  delete []java_refs;
  delete []refs;
  delete []argv;

  if (shutdown_requested.load()) {
    ros::shutdown();
    log("Livox stop was requested before initialization completed.");
    return 0;
  }

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

  livox_node_ptr = &livox_node;
  ros::Subscriber sub = livox_node.subscribe("record_control", 10, recordCallback);

  // for debug purposes, we use ros::spinOnce(). Otherwise,ros::spin() without while loop is enough.
  ros::Rate loop_rate(50);
  while (ros::ok() && !shutdown_requested.load()) {
      ros::spinOnce();
      loop_rate.sleep();
  }

  log("Exiting from livox ros driver2 JNI call.");
  sub.shutdown();
  livox_node_ptr = nullptr;
  return 0;
}

/*
 * Class:     org_ros_rosjava_tutorial_native_node_LivoxRosDriver2NativeNode
 * Method:    shutdownNative
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_LivoxRosDriver2NativeNode_shutdownNative
  (JNIEnv *, jobject) {
  log("Shutting down livox ros driver2 native node.");
  shutdown_requested.store(true);
  if (ros::isStarted()) {
    ros::shutdown();
  }
  return 0;
}

JNIEXPORT void JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_LivoxRosDriver2NativeNode_prepareNative
  (JNIEnv *, jobject) {
  shutdown_requested.store(false);
}
