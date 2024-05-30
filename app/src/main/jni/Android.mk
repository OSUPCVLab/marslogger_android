MY_LOCAL_PATH := $(call my-dir)

LOCAL_PATH=$(MY_LOCAL_PATH)
include $(CLEAR_VARS)
LOCAL_PATH      :=$(MY_LOCAL_PATH)/laser_scan_matcher
LOCAL_MODULE    := lsm_jni
LOCAL_CFLAGS    := -std=c++11 -pthread -fPIC -fexceptions -frtti # -g -O0 -DDEBUG
LOCAL_CPPFLAGS  := -isystem
LOCAL_CPP_FEATURES := exceptions
LOCAL_SRC_FILES := $(LOCAL_PATH)/src/lsm_jni.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_LDFLAGS := -Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libgnustl_shared.so
LOCAL_LDLIBS := -landroid -llog
LOCAL_STATIC_LIBRARIES := roscpp_android_ndk
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_PATH      :=$(MY_LOCAL_PATH)/movebase
LOCAL_MODULE    := movebase_jni
LOCAL_CFLAGS    := -std=c++11 -pthread -fPIC -fexceptions -frtti # -g -O0 -DDEBUG
LOCAL_CPPFLAGS  := -isystem
LOCAL_CPP_FEATURES := exceptions
LOCAL_SRC_FILES := $(LOCAL_PATH)/src/movebase_jni.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_LDFLAGS := -Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libgnustl_shared.so
# How do we know which library is a plugin? see the export plugin section in package.xml for the package.
# The library name is specified in the xxx-plugin.xml.
# Per http://wiki.ros.org/android_ndk/Tutorials/UsingPluginlib, we add LOCAL_WHOLE_STATIC_LIBRARIES
# for plugins. However, it does not prevent the class unloaded exception. E.g.,
# "Exception: According to the loaded plugin descriptions the class navfn/NavfnROS with base class
# type nav_core::BaseGlobalPlanner does not exist. Declared types are"
# LOCAL_WHOLE_STATIC_LIBRARIES := liblayers libdwa_local_planner libclear_costmap_recovery libglobal_planner \
#  libnavfn libcarrot_planner libmove_slow_and_clear libtrajectory_planner_ros librotate_recovery
#LOCAL_LDLIBS := $(call link-whole-archive-flags, $(call map,module-get-built, $(LOCAL_WHOLE_STATIC_LIBRARIES)))
LOCAL_LDLIBS := -landroid -llog
LOCAL_STATIC_LIBRARIES := roscpp_android_ndk
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_PATH      :=$(MY_LOCAL_PATH)/amcl
LOCAL_MODULE    := amcl_jni
LOCAL_CFLAGS    := -std=c++11 -pthread -fPIC -fexceptions -frtti # -g -O0 -DDEBUG
LOCAL_CPPFLAGS  := -isystem
LOCAL_CPP_FEATURES := exceptions
LOCAL_SRC_FILES := $(LOCAL_PATH)/src/amcl_jni.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_LDFLAGS := -Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libgnustl_shared.so
LOCAL_LDLIBS := -landroid -llog
LOCAL_STATIC_LIBRARIES := roscpp_android_ndk
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_PATH      :=$(MY_LOCAL_PATH)/fastlio
LOCAL_MODULE    := fastlio_jni
LOCAL_CFLAGS    := -std=c++11 -pthread -fPIC -fexceptions -frtti -fopenmp # -g -O0 -DDEBUG
LOCAL_CPPFLAGS  := -isystem
LOCAL_CPP_FEATURES := exceptions
LOCAL_SRC_FILES := $(LOCAL_PATH)/src/fastlio_jni.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_LDFLAGS := -Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libgnustl_shared.so
LOCAL_LDLIBS := -landroid -llog -lomp
LOCAL_STATIC_LIBRARIES := roscpp_android_ndk
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_PATH      :=$(MY_LOCAL_PATH)/livox_ros_driver2
LOCAL_MODULE    := livox_ros_driver2_jni
LOCAL_CFLAGS    := -std=c++11 -pthread -fPIC -fexceptions -frtti # -g -O0 -DDEBUG
LOCAL_CPPFLAGS  := -isystem -std=c++14
LOCAL_CPP_FEATURES := exceptions
LOCAL_SRC_FILES := $(LOCAL_PATH)/src/livox_ros_driver2_jni.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include
LOCAL_LDFLAGS := -Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libgnustl_shared.so
LOCAL_LDLIBS := -landroid -llog
LOCAL_STATIC_LIBRARIES := roscpp_android_ndk
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_PATH      :=$(MY_LOCAL_PATH)/laser_logger
LOCAL_MODULE    := laser_logger_jni
LOCAL_CFLAGS    := -std=c++11 -pthread -fPIC -fexceptions -frtti
LOCAL_CPPFLAGS  := -isystem
LOCAL_CPP_FEATURES := exceptions
LOCAL_SRC_FILES := $(LOCAL_PATH)/laser_logger_jni.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)
LOCAL_LDFLAGS := -Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libgnustl_shared.so
LOCAL_LDLIBS := -landroid -llog
LOCAL_STATIC_LIBRARIES := roscpp_android_ndk
include $(BUILD_SHARED_LIBRARY)


# This file should contain the import path for roscpp_android_ndk buildscript.
# For example:
# $(call import-add-path, /home/user/ros-android-ndk/roscpp_android/output)
# The local file shouldn't be commited to the repository.
include $(MY_LOCAL_PATH)/local-properties.mk
$(call import-module, roscpp_android_ndk)
