
#include <android/log.h>

#include <chrono>
#include <sstream>
#include <ros/ros.h>
#include <std_msgs/String.h>
#include <geometry_msgs/PoseWithCovarianceStamped.h>
#include <nav_msgs/Odometry.h>

#include "fastlio_jni.h"
#include "fast_lio/fastlio/laserMapping.hpp"
#include "fast_csm_icp/gsm_wrap.h"

inline void log(const char *msg, ...) {
    va_list args;
    va_start(args, msg);
    __android_log_vprint(ANDROID_LOG_INFO, "Native_FastLio", msg, args);
    va_end(args);
}

inline std::string stdStringFromjString(JNIEnv *env, jstring java_string) {
    const char *tmp = env->GetStringUTFChars(java_string, NULL);
    std::string out(tmp);
    env->ReleaseStringUTFChars(java_string, tmp);
    return out;
}

ros::Publisher global_pose_publisher;
geometry_msgs::PoseStamped map_T_lidar;
tf::Transform map_T_odom;

void laserOdometryCallback(const nav_msgs::Odometry::ConstPtr &laserOdometry) {
    tf::Transform odom_T_basefootprint(tf::Quaternion(laserOdometry->pose.pose.orientation.x, laserOdometry->pose.pose.orientation.y,
                laserOdometry->pose.pose.orientation.z, laserOdometry->pose.pose.orientation.w),
                tf::Vector3(laserOdometry->pose.pose.position.x, laserOdometry->pose.pose.position.y, laserOdometry->pose.pose.position.z));
    tf::Transform map_T_basefootprint = map_T_odom * odom_T_basefootprint;

    geometry_msgs::PoseWithCovarianceStamped p;
    p.header.frame_id = "map";
    p.header.stamp = ros::Time::now();
    p.pose.pose.position.x = map_T_basefootprint.getOrigin().getX();
    p.pose.pose.position.y = map_T_basefootprint.getOrigin().getY();
    p.pose.pose.position.z = map_T_basefootprint.getOrigin().getZ();
    p.pose.pose.orientation.x = map_T_basefootprint.getRotation().getX();
    p.pose.pose.orientation.y = map_T_basefootprint.getRotation().getY();
    p.pose.pose.orientation.z = map_T_basefootprint.getRotation().getZ();
    p.pose.pose.orientation.w = map_T_basefootprint.getRotation().getW();
    p.pose.covariance = laserOdometry->pose.covariance;
    global_pose_publisher.publish(p);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    log("FastLio library has been loaded");
    // Return the JNI version
    return JNI_VERSION_1_6;
}

inline double time_inc_ms(std::chrono::high_resolution_clock::time_point &t_end,
                std::chrono::high_resolution_clock::time_point &t_begin)
{
  return std::chrono::duration_cast<std::chrono::duration<double>>(t_end - t_begin).count() * 1000;
}

inline void check_systemclock() {
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
  log("system clock time %ld, ros time %ld, diff %d", current_time, ros_time, diff);
}

/*
 * Class:     org_ros_rosjava_tutorial_native_node_FastLioNativeNode
 * Method:    execute
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_FastLioNativeNode_execute(
    JNIEnv *env, jobject obj, jstring rosMasterUri, jstring rosHostname, jstring rosNodeName,
    jobjectArray remappingArguments) {
  log("Native fastlio node started.");
  std::string master("__master:=" + stdStringFromjString(env, rosMasterUri));
  std::string hostname("__ip:=" + stdStringFromjString(env, rosHostname));
  std::string node_name(stdStringFromjString(env, rosNodeName));

  log(master.c_str());
  log(hostname.c_str());
  std::string nnmsg = "fastlio native nodename " + node_name;
  log(nnmsg.c_str());
  // Parse remapping arguments
  jsize len = env->GetArrayLength(remappingArguments);

  std::string ni = "fastlio_cpp";

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
  std::string pcdmap_path((char *) env->GetStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, 0), NULL));
  ros::init(argc, &argv[0], node_name.c_str());

  // Release JNI UTF characters
  for (int i = 0; i < len; i++) {
      env->ReleaseStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, i),
                                 refs[i]);
  }
  delete []refs;
  delete []argv;

  ros::NodeHandle nh;
  ros::Rate rate(30);
  // comment out global localization for it's so slow.
  auto              match_begin_csm       = std::chrono::high_resolution_clock::now();
  int accum_frames = 2;
  std::shared_ptr<GSMWrap> gsm(new GSMWrap(nh, accum_frames));
  std::string fn_path = pcdmap_path.substr(0, pcdmap_path.find_last_of('/')) + "/";
  std::string pcdbasename = "map_0.1.pcd";
  gsm->LoadMap(fn_path, pcdbasename);

  while (ros::ok()) {
    if (gsm->loc_status()) {
      map_T_lidar = gsm->init_pose();
      log("Global scan matcher returned initial position %f %f %f", map_T_lidar.pose.position.x, map_T_lidar.pose.position.y, map_T_lidar.pose.position.z);
      break;
    }
    ros::spinOnce();
    rate.sleep();
  }
  gsm.reset(); // remove the gsm node.
  auto            match_end_csm = std::chrono::high_resolution_clock::now();
  double delta_ms = time_inc_ms(match_end_csm, match_begin_csm);
  log("Global scan matcher took %f ms", delta_ms);
  std::vector<double> position{map_T_lidar.pose.position.x, map_T_lidar.pose.position.y, map_T_lidar.pose.position.z};
  nh.setParam("/mapping/init_world_t_lidar", position);
  std::vector<double> qxyzw{map_T_lidar.pose.orientation.x, map_T_lidar.pose.orientation.y, map_T_lidar.pose.orientation.z, map_T_lidar.pose.orientation.w};
  nh.setParam("/mapping/init_world_qxyzw_lidar", qxyzw);
  nh.setParam("/pcdmap", pcdmap_path);

  bool locmode = false;
  nh.param<bool>("locmode", locmode, false);

  fastlio::LaserMapping node(nh);
  global_pose_publisher = nh.advertise<geometry_msgs::PoseWithCovarianceStamped>("/amcl_pose", 2, true);
  ros::Subscriber sub_lidar_odom = nh.subscribe("/Odometry", 1, &laserOdometryCallback);
  bool status = ros::ok();
  tf::TransformBroadcaster tf_broadcaster;
  while (status) {
      ros::spinOnce();
      if (locmode) {
        map_T_odom = tf::Transform(tf::Quaternion(0, 0, 0, 1), tf::Vector3(0, 0, 0));
      } else {
        map_T_odom = tf::Transform(tf::Quaternion(map_T_lidar.pose.orientation.x, map_T_lidar.pose.orientation.y,
            map_T_lidar.pose.orientation.z, map_T_lidar.pose.orientation.w),
            tf::Vector3(map_T_lidar.pose.position.x, map_T_lidar.pose.position.y, map_T_lidar.pose.position.z));
      }
      // since we use the same map_T_odom, we postdate it to avoid global plan to controller tf transform extrapolation.
      tf::StampedTransform map_T_odom_stamped(map_T_odom, ros::Time::now() + ros::Duration(0.5), "map", "odom");
      tf_broadcaster.sendTransform(map_T_odom_stamped);
      node.spinOnce();
      status = ros::ok();
      rate.sleep();
  }
  node.saveAndClose();

  log("Exiting from fastlio JNI call.");
  return 0;
}

/*
 * Class:     org_ros_rosjava_tutorial_native_node_FastLioNativeNode
 * Method:    shutdown
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_FastLioNativeNode_shutdown
  (JNIEnv *, jobject) {
    log("Shutting down fastlio native node.");
    ros::shutdown();
    return 0;
}

