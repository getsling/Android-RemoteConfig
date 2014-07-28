package is.gangverk.remoteconfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;

public class RemoteConfig {
    private static final String LAST_DOWNLOADED_CONFIG_KEY = "lastDownloadedConfig";
    // This is just a dot, since we have regular expression we have to have the backslashes as well
    private static final String DEEP_DICTIONARY_SEPARATOR_REGEX = "\\.";
    private static final String DEEP_DICTIONARY_SEPARATOR = ".";
    private static final String REMOTE_CONFIG_FILE = "rc.json";
    private static final String SP_VERSION_KEY = "rc_version";
    private static final String LOCAL_BROADCAST_INTENT = "remote_config_download_complete";
    private static final String COMPLETE_CONFIG_KEY = "rc_complete_config";
    private URL mConfigLocation;
    private long mUpdateTime;
    private SharedPreferences mPreferences;
    private Context mContext;
    private ArrayList<RemoteConfigListener> mListeners;
    private int mVersion;

    public RemoteConfig() {}

    private volatile static RemoteConfig instance;

    /**
     *  Returns singleton class instance
     */
    public static RemoteConfig getInstance() {
        if (instance == null) {
            synchronized (RemoteConfig.class) {
                if (instance == null) {
                    instance = new RemoteConfig();
                }
            }
        }
        return instance;
    }

    /**
     * Use this method to initialize the remote config. Using this init method you would have to have the
     * string named rc_config_location with the config url as value somewhere in your xml files.
     * 
     * @param context Can be application context
     * @param version For version control. If this isn't increased with new key/value pairs won't ever be added
     */
    public synchronized void init(Context context, int version, boolean useDefault) {
        init(context, version, useDefault, context.getString(context.getResources().getIdentifier("rc_config_location", "string", context.getPackageName())));
    }

    /**
     * Use this method to initialize the remote config with a custom config location.
     * 
     * @param context Can be application context
     * @param version For version control. If this isn't increased with new key/value pairs won't ever be added
     * @param useDefault If true then use the assets/rc.json file as default values
     * @param location The location of the remote config
     */
    @SuppressLint({"CommitPrefEdits"})
    public synchronized void init(Context context, int version, boolean useDefault, String location) {
        mContext = context;
        mVersion = version;
        setConfigImpl(location);
        mUpdateTime = context.getResources().getInteger(context.getResources().getIdentifier("rc_config_update_interval", "integer", context.getPackageName()));
        int oldVersion = mPreferences.getInt(SP_VERSION_KEY, -1);
        if(version>oldVersion) {
            mPreferences.edit().clear().apply();
            if(useDefault) {
                initializeConfigFile();
            }
        }
        checkForUpdate(); // We'll fetch new config on launch
    }

    @SuppressLint("NewApi")
    private void initializeConfigFile() {
        // Start with parsing the assets/rc.json file into JSONObject
        JSONObject remoteConfig = initialFileToJsonObject();
        if(remoteConfig!=null) {
            jsonObjectIntoPreferences(remoteConfig);
        } else {
            throw new RuntimeException("Unable to read rc.json file. Are you sure it exists in the assets folder?");
        }
    }

    private void setConfigImpl(String location) {
        URL locationUrl;
        try {
            locationUrl = new URL(location);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unable to parse config URL");
        }
        mConfigLocation = locationUrl;
        try {
            mPreferences = mContext.getSharedPreferences(URLEncoder.encode(mConfigLocation.toString(), "UTF-8"), Context.MODE_PRIVATE);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public void setConfig(String location) {
        setConfigImpl(location);
        boolean updateNeeded = checkForUpdate();
        if(!updateNeeded) {
            if(mListeners!=null && mListeners.size()>0) {
                for(RemoteConfigListener listener : mListeners) {
                    listener.onConfigComplete();
                }
            }
        }
    }

    public JSONObject getConfig() {
        String completeConfig = getString(COMPLETE_CONFIG_KEY);
        JSONObject completeJSON = null;
        try {
            completeJSON = new JSONObject(completeConfig);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return completeJSON;
    }

    @SuppressLint("CommitPrefEdits")
    private synchronized void jsonObjectIntoPreferences(final JSONObject jsonObject) {
        Editor editor = mPreferences.edit();
        editor.putInt(SP_VERSION_KEY, mVersion);
        editor.putString(COMPLETE_CONFIG_KEY, jsonObject.toString());
        HashMap<String, Object> changedKeys = new HashMap<String, Object>();
        ArrayList<String> allKeys = getAllKeysFromJSONObject(jsonObject, null);
        for(String newKey : allKeys) {

            // If the key is inside an inner JSON dictionary it is defined with
            // a dot like dictionary1.dictionary2. That's why we split the string
            // here
            String[] deepKeys = newKey.split(DEEP_DICTIONARY_SEPARATOR_REGEX);

            JSONObject deepDictionary = jsonObject;

            for(int i=0;i<deepKeys.length-1;i++) {
                if(deepDictionary.has(deepKeys[i])) {
                    try {
                        deepDictionary = deepDictionary.getJSONObject(deepKeys[i]);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            String key = deepKeys[deepKeys.length-1];

            try {
                Object value = deepDictionary.get(key);
                if(value instanceof JSONArray) {
                    String oldValue = mPreferences.getString(newKey, null);
                    String newValue = ((JSONArray)value).toString();
                    if(!newValue.equals(oldValue)){
                        editor.putString(newKey,newValue);
                        changedKeys.put(newKey, newValue);
                    }
                } else if(value instanceof String) {
                    String oldValue = mPreferences.getString(newKey, null);
                    String newValue = (String)value;
                    if(!newValue.equals(oldValue)){
                        editor.putString(newKey,newValue);
                        changedKeys.put(newKey, newValue);
                    }

                } else if(value instanceof Integer) {
                    int oldValue = mPreferences.getInt(newKey, -1);
                    int newValue = ((Integer)value).intValue();
                    if(newValue != oldValue){
                        editor.putInt(newKey,newValue);
                        changedKeys.put(newKey, newValue);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        editor.apply();
        //Let someone know we have a new value
        Iterator<String> it = changedKeys.keySet().iterator();
        if(mListeners!=null && mListeners.size()>0) {
            for(RemoteConfigListener listener : mListeners) {
                while (it.hasNext()) {
                    String key = it.next();
                    listener.onValueUpdated(key, changedKeys.get(key));
                }
                listener.onConfigComplete();
            }
        }
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(LOCAL_BROADCAST_INTENT));
    }

    public void registerForBroadcast(Context context, BroadcastReceiver receiver) {
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, new IntentFilter(LOCAL_BROADCAST_INTENT));
    }

    private JSONObject initialFileToJsonObject() {
        JSONObject remoteConfig = null;
        InputStream is = null;
        StringBuilder total = new StringBuilder();
        try {
            is = mContext.getResources().getAssets().open(REMOTE_CONFIG_FILE);
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = r.readLine()) != null) {
                total.append(line);
            }
            String jsonString = total.toString();
            remoteConfig = new JSONObject(jsonString);
        } catch (Exception e) {} finally {
            if(is!=null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return remoteConfig;
    }

    /**
     * Checks if it is time for update based on the updateTime variable.
     */
    public boolean checkForUpdate() {
        if(RemoteConfig.shouldUpdate(mPreferences, mUpdateTime)) {
            if(Utils.isNetworkConnection(mContext)) {
                // Fetch the config
                new FetchConfigAsyncTask().execute();
                return true;
            }
        }
        return false;
    }


    /**
     * Takes in the map parameter and returns the mapping if available. If the mapping is not available it
     * returns the default value. If the user has never used this before or there has been a long time since
     * last check for updated config, new config will be downloaded.
     * 
     * @param mapping The map parameter to fetch something that should be in the remote config
     * @return Returns the mapping for the parameter from the shared defaults
     */
    public String getString(String mapping) {
        checkForUpdate();
        return mPreferences.getString(mapping, null);
    }

    public int getInt(String mapping) {
        checkForUpdate();
        return mPreferences.getInt(mapping, -1);
    }

    @SuppressLint("NewApi")
    private synchronized static boolean shouldUpdate(SharedPreferences preferences, long updateTime) {
        long lastDownloadedConfig = preferences.getLong(RemoteConfig.LAST_DOWNLOADED_CONFIG_KEY, 0);
        if(lastDownloadedConfig + updateTime < System.currentTimeMillis()) {
            Editor editor = preferences.edit();
            editor.putLong(RemoteConfig.LAST_DOWNLOADED_CONFIG_KEY, System.currentTimeMillis());
            editor.apply();
            return true;
        }
        return false;
    }

    public interface RemoteConfigListener {
        /**
         * This method is called when the config has been downloaded and it's values are being put into shared preferences
         * 
         * @param key The key for the new value in shared preferences
         * @param value The updated value
         */
        public void onValueUpdated(String key, Object value);

        /**
         * This is called after every new value has been put into shared preferences
         */
        public void onConfigComplete();

        /**
         * In case of error, this is called
         * 
         * @param string The error message
         */
        public void onConfigError(String string);
    }

    private ArrayList<String> getAllKeysFromJSONObject(JSONObject jsonObject, String prefix) {
        ArrayList<String> allKeys = new ArrayList<String>();
        Iterator<?> iter = jsonObject.keys();
        while (iter.hasNext()) {
            try {
                String key = (String)iter.next();
                Object value = jsonObject.get(key);
                String newKey = null;
                if(prefix!=null) {
                    newKey = prefix + DEEP_DICTIONARY_SEPARATOR + key;
                } else {
                    newKey = key;
                }
                if(value instanceof JSONObject) {
                    allKeys.addAll(getAllKeysFromJSONObject(((JSONObject)jsonObject.get(key)), newKey));
                } else {
                    allKeys.add(newKey);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return allKeys;
    }

    /**
     * Adds a listener to the remote config that can react to new values being downloaded
     * 
     * @param listener The listener to listen for new config values
     */
    public void addRemoteConfigListener(RemoteConfigListener listener) {
        if(mListeners==null)
            mListeners = new ArrayList<RemoteConfig.RemoteConfigListener>();
        mListeners.add(listener);
    }

    /**
     * Removes a listener
     * 
     * @param listener The listener to remove
     */
    public void removeRemoteConfigListener(RemoteConfigListener listener) {
        if(mListeners!=null) {
            mListeners.remove(listener);
        }
    }

    private class FetchConfigAsyncTask extends AsyncTask<Void, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(Void... params) {
            return Utils.readJSONFeed(mConfigLocation.toString(), null);
        }

        @Override
        protected void onPostExecute(JSONObject config) {
            if(config!=null) {
                jsonObjectIntoPreferences(config);
            } else {
                if(mListeners!=null) {
                    for (int i = 0; i < mListeners.size(); i++) {
                        mListeners.get(i).onConfigError("Unable to read remote config");
                    }
                }
            }
        }
    }
}

