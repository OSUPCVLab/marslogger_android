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


# TODOs

* Let the user select the physical camera behind a logical camera for recording.

* Record multiple cameras.

* Correct warnings listed in /mobile-ar-sensor-logger/android-mars-logger/app/build/reports/lint-results.html which is produced by 
calling "./gradlew check" from the project dir

* Add [git hooks](https://github.com/harliedharma/android-git-hooks) for the Android project. 

* Long press to unlock focal length and exposure duration.
