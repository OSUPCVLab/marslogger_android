
#include <android/log.h>

#include <chrono>
#include <sstream>
#include <ros/ros.h>
#include <std_msgs/String.h>
#include <geometry_msgs/PoseWithCovarianceStamped.h>
#include <geometry_msgs/PoseStamped.h>
#include <nav_msgs/Odometry.h>
#include <tf/transform_broadcaster.h>

#include "fasterlio_jni.h"
#include "faster_lio/laser_mapping_wrap.h"
#include "faster_lio/utils.h"

inline void log(const char *msg, ...) {
    va_list args;
    va_start(args, msg);
    __android_log_vprint(ANDROID_LOG_INFO, "Native_FasterLio", msg, args);
    va_end(args);
}

inline std::string stdStringFromjString(JNIEnv *env, jstring java_string) {
    const char *tmp = env->GetStringUTFChars(java_string, NULL);
    std::string out(tmp);
    env->ReleaseStringUTFChars(java_string, tmp);
    return out;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    log("FasterLio library has been loaded");
    // Return the JNI version
    return JNI_VERSION_1_6;
}

inline double time_inc_ms(std::chrono::high_resolution_clock::time_point &t_end,
                std::chrono::high_resolution_clock::time_point &t_begin)
{
  return std::chrono::duration_cast<std::chrono::duration<double>>(t_end - t_begin).count() * 1000;
}

// Warn: call this function after ros::NodeHandle nh;
inline void check_system_clock() {
  uint64_t high_time = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::high_resolution_clock::now().time_since_epoch()).count();
  ros::Time t = ros::Time::now();
  uint64_t current_time = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
  uint64_t ros_time = t.toNSec();
  int diff;
  if (current_time > ros_time) {
    diff = (current_time - ros_time);
  } else {
    diff = ros_time - current_time;
    diff = -diff;
  }
  log("high time %ld, system clock time %ld, ros time %ld, diff %d", high_time, current_time, ros_time, diff);
}

JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_FasterLioNativeNode_execute(
    JNIEnv *env, jobject obj, jstring rosMasterUri, jstring rosHostname, jstring rosNodeName,
    jobjectArray remappingArguments) {
  log("Native fasterlio node started.");
  std::string master("__master:=" + stdStringFromjString(env, rosMasterUri));
  std::string hostname("__ip:=" + stdStringFromjString(env, rosHostname));
  std::string node_name(stdStringFromjString(env, rosNodeName));
  std::string unique_node_name = node_name + "_" + std::to_string(time(nullptr));

  log(master.c_str());
  log(hostname.c_str());
  std::string nnmsg = "fasterlio native nodename " + unique_node_name;
  log(nnmsg.c_str());
  // Parse remapping arguments
  jsize len = env->GetArrayLength(remappingArguments);

  std::string ni = "fasterlio_cpp";

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
  std::string output_dir((char *) env->GetStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, 0), NULL));
  ros::init(argc, &argv[0], unique_node_name.c_str());
  // Release JNI UTF characters
  for (int i = 0; i < len; i++) {
      env->ReleaseStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, i),
                                 refs[i]);
  }
  delete []refs;
  delete []argv;

  ros::NodeHandle nh;
  ros::Rate rate(30);

  // check_system_clock();

  faster_lio::LaserMappingWrap laser_mapping;
  laser_mapping.InitROS(nh);

  bool status = ros::ok();
  if (!status) {
      log("Error: fasterlio ros::ok() false at start! This means the previous fasterlio has not been cleaned thoroughly!");
  }
  while (status) {
      ros::spinOnce();
      laser_mapping.Run();
      status = ros::ok();
      rate.sleep();
  }
  laser_mapping.Finish(output_dir);
  std::string traj_log_file = output_dir + "/faster_lio_traj.txt";
  laser_mapping.Savetrajectory(traj_log_file, laser_mapping.I_p_B(), laser_mapping.I_q_B());
  std::string time_log_file = output_dir + "/faster_lio_times.txt";
  faster_lio::Timer::DumpIntoFile(time_log_file);

  log("Exiting from fasterlio JNI call.");
  return 0;
}


JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_FasterLioNativeNode_shutdown
  (JNIEnv *, jobject) {
    log("Shutting down fasterlio native node.");
    ros::shutdown();
    return 0;
}
