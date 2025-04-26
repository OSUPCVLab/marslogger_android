#include <android/log.h>
#include <ros/ros.h>

#include "pandar_general_jni.h"
#include <iostream>
#include <chrono>
#include <vector>
#include <csignal>
#include <thread>
#include <sstream>
#include <std_msgs/String.h>

#include "hesai_lidar/pandarGeneral_sdk/hesai_lidar_client_wrap.h"

HesaiLidarClientWrap *hesai_client_ptr = nullptr;

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

JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_PandarGeneralNativeNode_execute(
      JNIEnv *env, jobject obj, jstring rosMasterUri, jstring rosHostname, jstring rosNodeName,
      jobjectArray remappingArguments) {
  log("Native pandar general ros node started.");

  std::string master("__master:=" + stdStringFromjString(env, rosMasterUri));
  std::string hostname("__ip:=" + stdStringFromjString(env, rosHostname));
  std::string node_name(stdStringFromjString(env, rosNodeName));

  log(master.c_str());
  log(hostname.c_str());
  std::string nnmsg = "pandar general ros native nodename " + node_name;
  log(nnmsg.c_str());
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
  for (int i = 0; i < len; i++) {
      refs[i] = (char *) env->GetStringUTFChars(
              (jstring) env->GetObjectArrayElement(remappingArguments, i), NULL);
      argv[argc] = refs[i];
      argc++;
  }
  std::string server_ip((char *) env->GetStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, 0), NULL));
  std::string lidar_correction_file((char *) env->GetStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, 1), NULL));
  std::string pandar_time_type((char *) env->GetStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, 2), NULL));

  ros::init(argc, &argv[0], node_name.c_str());

  // Release JNI UTF characters
  for (int i = 0; i < len; i++) {
      env->ReleaseStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, i),
                                 refs[i]);
  }
  delete []refs;
  delete []argv;

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
  while (ros::ok()) {
      ros::spinOnce();
      loop_rate.sleep();
  }

  pandarClientWrap.Stop();
  log("Exiting from pandar general ros node JNI call.");
  hesai_client_ptr = nullptr;
  return 0;
}

JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_PandarGeneralNativeNode_shutdown
  (JNIEnv *, jobject) {
  log("Shutting down pandar general ros native node.");
  ros::shutdown();
  return 0;
}
