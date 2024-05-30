#include <android/log.h>
#include <ros/ros.h>

#include "lsm_jni.h"
#include <laser_scan_matcher/laser_scan_matcher.h>
#include "std_msgs/String.h"
#include <sstream>

using namespace std;

inline void log(const char *msg, ...) {
    va_list args;
    va_start(args, msg);
    __android_log_vprint(ANDROID_LOG_INFO, "Native_Lsm", msg, args);
    va_end(args);
}

inline std::string stdStringFromjString(JNIEnv *env, jstring java_string) {
    const char *tmp = env->GetStringUTFChars(java_string, NULL);
    std::string out(tmp);
    env->ReleaseStringUTFChars(java_string, tmp);
    return out;
}

int loop_count_ = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    log("Laser scan matcher library has been loaded");
    // Return the JNI version
    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_LsmNativeNode_execute(
        JNIEnv *env, jobject obj, jstring rosMasterUri, jstring rosHostname, jstring rosNodeName,
        jobjectArray remappingArguments) {
    log("Native lsm node started.");

    string master("__master:=" + stdStringFromjString(env, rosMasterUri));
    string hostname("__ip:=" + stdStringFromjString(env, rosHostname));
    string node_name(stdStringFromjString(env, rosNodeName));

    log(master.c_str());
    log(hostname.c_str());
    string nnmsg = "lsm native nodename " + node_name;
    log(nnmsg.c_str());
    // Parse remapping arguments
    log("Before getting size");
    jsize len = env->GetArrayLength(remappingArguments);
    log("After reading size");

    std::string ni = "lsm_cpp";

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

    ros::init(argc, &argv[0], node_name.c_str());

    // Release JNI UTF characters
    for (int i = 0; i < len; i++) {
        env->ReleaseStringUTFChars((jstring) env->GetObjectArrayElement(remappingArguments, i),
                                   refs[i]);
    }
    delete []refs;
    delete []argv;

    ros::NodeHandle nh;
    ros::NodeHandle nh_private("~");
    nh.setParam("/stamped_vel", true);
    nh.setParam("/laser_scan_matcher_node/max_iterations", 10);
    nh.setParam("/laser_scan_matcher_node/use_odom", false);
    nh.setParam("/laser_scan_matcher_node/use_vel", false);
    nh.setParam("/laser_scan_matcher_node/use_imu", false);
    nh.setParam("/laser_scan_matcher_node/publish_odometry", true);
    nh.setParam("/laser_scan_matcher_node/base_frame", "base_link");
    nh.setParam("/laser_scan_matcher_node/fixed_frame", "odom");
    nh.setParam("/laser_scan_matcher_node/scan_range_min", 0.3);
    scan_tools::LaserScanMatcher laser_scan_matcher(nh, nh_private);

    ros::Rate loop_rate(20);
    while (ros::ok()) {
        ros::spinOnce();
        loop_rate.sleep();
    }

    log("Exiting from lsm JNI call.");
    return 0;
}

JNIEXPORT jint JNICALL Java_org_ros_rosjava_1tutorial_1native_1node_LsmNativeNode_shutdown
        (JNIEnv *, jobject) {
    log("Shutting down native node.");
    ros::shutdown();
    return 0;
}
