# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/o-to-the-l/Files/android-sdk-macosx/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# =======================================================================
#  GENERAL SETTINGS
# =======================================================================

# obfuscation will break reflection and
# make these rules much more complex
-dontobfuscate

# =======================================================================
#  ROS CORE CLASSES
# =======================================================================

# Keep all classes in the org.ros packages
-keep class org.ros.** { *; }
-keepnames class org.ros.** { *; }
-dontwarn org.ros.**

# =======================================================================
#  ROS MESSAGES AND JAVA MESSAGE CLASSES
# =======================================================================

# These classes (not part of the org.ros namespace) are dynamically loaded by ROS at runtime.
-keep class rosgraph_msgs.** { *; }
-keep class sensor_msgs.** { *; }
-keep class std_msgs.** { *; }
-keep class tf2_msgs.** { *; }

# Keep ROS message definitions
-keep class org.ros.message.** { *; }
-keep class org.ros.rosjava_messages.** { *; }

# Keep all classes under common ROS message packages
-keep class geometry_msgs.** { *; }
-keep class nav_msgs.** { *; }
-keep class actionlib_msgs.** { *; }
-keep class visualization_msgs.** { *; }
-keep class trajectory_msgs.** { *; }

# =======================================================================
#  APACHE COMMONS LOGGING
# =======================================================================

# Commons Logging loads its factory and configured Android-compatible backend
# reflectively. Keep those implementations, but do not retain optional adapters
# (such as Log4JLogger) whose logging frameworks are not packaged in this app.
-keep interface org.apache.commons.logging.Log { *; }
-keep class org.apache.commons.logging.LogFactory { *; }
-keep class org.apache.commons.logging.impl.LogFactoryImpl { *; }
-keep class org.apache.commons.logging.impl.Jdk14Logger { *; }
-keepattributes *Annotation*
-dontwarn org.apache.commons.logging.**

# =======================================================================
#  APACHE COMMONS HTTPCLIENT
# =======================================================================

# Keep all classes in the Apache Commons HttpClient library.
-keep class org.apache.commons.httpclient.** { *; }
-keep interface org.apache.commons.httpclient.** { *; }
-keep class org.apache.commons.httpclient.cookie.** { *; }
-keepclassmembers class org.apache.commons.httpclient.** { *; }
-dontwarn org.apache.commons.httpclient.**

# =======================================================================
#  APACHE XML-RPC & XML PARSING
# =======================================================================

# Keep Apache XML-RPC classes
-keep class org.apache.xmlrpc.** { *; }
-dontwarn org.apache.xmlrpc.**

# Keep XML parsing classes (SAX, DOM, and javax parsers)
-keep class javax.xml.parsers.** { *; }
-keep class org.xml.sax.** { *; }
-keep class org.w3c.dom.** { *; }
-dontwarn javax.xml.**
-dontwarn org.xml.sax.**
-dontwarn org.w3c.dom.**

# =======================================================================
#  GENERAL EXCEPTIONS & MISCELLANEOUS
# =======================================================================

# Keep public static fields - necessary for reflection-based use cases.
-keepclassmembers class * {
    public static <fields>;
}

# Suppress warnings from various other libraries.
-dontwarn org.apache.**
-dontwarn org.jboss.netty.**
-dontwarn com.google.common.**
-dontwarn org.xbill.**
