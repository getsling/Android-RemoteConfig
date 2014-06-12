# RemoteConfig

Android library for loading a remote JSON config file with locally defined default values into the shared preferences.

## Installation
Download the jar file and put it into your libs folder. [`Remote Config v1`](https://github.com/gangverk/Android-RemoteConfig/releases/download/1.1/remote-config-1.1.jar) .

## Implementation
There are three steps to follow when using RemoteConfig

### Step 1 : Add permission to your manifest file
Add the INTERNET and ACCESS_NETWORK_STATE permission to your manifest file. Inside the manifest tag add `<uses-permission android:name="android.permission.INTERNET" />` and `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" /> `

### Step 2 : Add configure strings to your strings.xml file
You can add the string "rc_config_location" [optional] and the integer "rc_config_update_interval" [required] into your strings file. These are the url to your config file and the update interval of your preferences in milliseconds respectively.

### Step 3 : Initialize the RemoteConfig object
It is highly recommended that you use RemoteConfig as a singleton. To do that you have to override the application class and add android:name=".[MYAPPLICATION]" under the application tag in your manifest. An example of an overridden application class may be found in the example project. [`Application file`](https://github.com/gangverk/Android-RemoteConfig/blob/master/example/src/is/gangverk/example/remoteconfig/RemoteApplication.java)

### Listen to changes
There are two ways to listen for changes. One is using the RemoteConfigListener interface and the other is using the LocalBroadcastManager from the support package and registering for it using the registerForBroadcast method.