MarsLogger Android

The instructions on building MarsLogger Android is at [here](https://github.com/OSUPCVLab/mobile-ar-sensor-logger/wiki/Installation-Android).

# Build from source
Open the project in Android Studio, build the debug version and install it on your devices.

The app has been built successfully with Android Studio 2020.

If you want to install the release version, you need to 
[create an upload key and keystore](https://developer.android.com/studio/publish/app-signing#generate-key) in the first place.
Then to sign your app with the key, put your confidentials in mars_logger_android/local.properties like below.
```
ndk.dir=/home/jhuai/Android/Sdk/ndk-bundle
sdk.dir=/home/jhuai/Android/Sdk
keyAlias=YOURKEYALIAS
keyPassword=YOURKEYPASSWORD
storeFile=/path/to/your/keystore.jks
storePassword=YOURSTOREPASSWORD
```

Now you should be able to build and install the release version on your devices.


# Roadmap

- [ ] Use ARCore API to record camera and IMU data as well as visual inertial odometry.

- [ ] On Redmi K60 Pro, the focus distance keeps varying after tap to focus, which is unexpected.
For Samsung S22+, the focus distance is fixed after tap to focus as expected.

