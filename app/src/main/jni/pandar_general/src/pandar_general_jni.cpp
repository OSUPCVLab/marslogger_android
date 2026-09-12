#include <android/log.h>
#include <ros/ros.h>

#include <atomic>
#include <mutex>
#include "pandar_general_jni.h"
#include <iostream>
#include <chrono>
#include <vector>
#include <csignal>
#include <thread>
#include <sstream>
#include <std_msgs/String.h>

#include "hesai_lidar/pandarGeneral_sdk/hesai_lidar_client_wrap.h"

namespace {
std::atomic<bool> shutdown_requested{false};
std::mutex execution_mutex;
HesaiLidarClientWrap *hesai_client_ptr = nullptr;
}

inline void log(const char *msg, ...) {
    va_list args;
    va_start(args, msg);
    __android_log_vprint(ANDROID_LOG_INFO, "Native_Pandar_General_Node", msg, args);
    va_end(args);
}

inline std::string stdStringFromjString(JNIEnv *env, jstring java_string) {
    const char *tmp = env->GetStringUTFChars(java_string, NULL);
    std::string out(tmp);
    env->ReleaseStringUTFChars(java_string, tmp);
    return out;
}

void recordCallback(const std_msgs::String::ConstPtr& msg) {
    if (hesai_client_ptr == nullptr) {
        return;
    }
    if (msg->data.find("start") == 0) {  // Check if the message starts with "start"
        ROS_INFO("Start recording...");
        size_t colon_pos = msg->data.find(':');
        if (colon_pos != std::string::npos) {
            std::string fn = msg->data.substr(colon_pos + 1);  // Extract filename after colon
            hesai_client_ptr->StartRecording(fn);
        } else {
            ROS_WARN("Invalid command format, missing ':' to separate filename.");
        }
    } else if (msg->data == "stop") {
        ROS_INFO("Stop recording...");
        hesai_client_ptr->StopRecording();
    } else {
        ROS_INFO("Unknown command: %s", msg->data.c_str());
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    log("Pandar general library has been loaded");
    // Return the JNI version
    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_PandarGeneralNativeNode_executeNative(
      JNIEnv *env, jobject obj, jstring rosMasterUri, jstring rosHostname, jstring rosNodeName,
      jobjectArray remappingArguments) {
  std::unique_lock<std::mutex> execution_lock(execution_mutex);
  log("Native pandar general ros node started.");

  std::string master("__master:=" + stdStringFromjString(env, rosMasterUri));
  std::string hostname("__ip:=" + stdStringFromjString(env, rosHostname));
  std::string node_name(stdStringFromjString(env, rosNodeName));

  log("%s", master.c_str());
  log("%s", hostname.c_str());
  std::string nnmsg = "pandar general ros native nodename " + node_name;
  log("%s", nnmsg.c_str());
  // Parse remapping arguments
  jsize len = env->GetArrayLength(remappingArguments);
  std::string ni = "pandar_general_node_cpp";

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
  jstring server_arg = static_cast<jstring>(env->GetObjectArrayElement(remappingArguments, 0));
  jstring correction_arg = static_cast<jstring>(env->GetObjectArrayElement(remappingArguments, 1));
  jstring time_arg = static_cast<jstring>(env->GetObjectArrayElement(remappingArguments, 2));
  std::string server_ip = stdStringFromjString(env, server_arg);
  std::string lidar_correction_file = stdStringFromjString(env, correction_arg);
  std::string pandar_time_type = stdStringFromjString(env, time_arg);
  env->DeleteLocalRef(server_arg);
  env->DeleteLocalRef(correction_arg);
  env->DeleteLocalRef(time_arg);

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
    log("Pandar stop was requested before initialization completed.");
    return 0;
  }

  std::string lidar_type = "PandarXT-32";
  std::string frame_id = "PandarXT-32";
  std::string timestamp_type = "";
  if (pandar_time_type != "sensor") {
    timestamp_type = pandar_time_type;
  }
  log("Pandar general jni timestamp type: %s\n", timestamp_type.c_str());

  ros::NodeHandle nh("~");
  // Set parameters. this will override values loaded from the YAML file
  nh.setParam("server_ip", server_ip);
  nh.setParam("lidar_correction_file", lidar_correction_file);
  nh.setParam("lidar_type", lidar_type);
  nh.setParam("frame_id", frame_id);
  nh.setParam("timestamp_type", timestamp_type);

  ros::NodeHandle node; // Public NodeHandle for topics, services, etc.
  HesaiLidarClientWrap pandarClientWrap(node, nh);
  hesai_client_ptr = &pandarClientWrap;
  ros::Subscriber sub = node.subscribe("record_control", 10, recordCallback);

  // for debug purposes, we use ros::spinOnce(). Otherwise,ros::spin() without while loop is enough.
  ros::Rate loop_rate(30);
  while (ros::ok() && !shutdown_requested.load()) {
      ros::spinOnce();
      loop_rate.sleep();
  }

  hesai_client_ptr = nullptr;
  sub.shutdown();
  pandarClientWrap.Stop();
  log("Exiting from pandar general ros node JNI call.");
  return 0;
}

JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_PandarGeneralNativeNode_shutdownNative
  (JNIEnv *, jobject) {
  log("Shutting down pandar general ros native node.");
  shutdown_requested.store(true);
  if (ros::isStarted()) {
    ros::shutdown();
  }
  return 0;
}

JNIEXPORT void JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_PandarGeneralNativeNode_prepareNative
  (JNIEnv *, jobject) {
  shutdown_requested.store(false);
}
