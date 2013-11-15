package is.gangverk.remoteconfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

public class RemoteConfig {
	private static final String LAST_DOWNLOADED_CONFIG_KEY = "lastDownloadedConfig";
	private static final String TAG = "RemoteConfig";
	private static final String PREFERENCE_NAME = "RemoteConfig";
	// This is just a dot, since we have regular expression we have to have the backslashes as well
	private static final String DEEP_DICTIONARY_SEPARATOR_REGEX = "\\."; 
	private static final String DEEP_DICTIONARY_SEPARATOR = "."; 
	private static final String REMOTE_CONFIG_INITIALIZER = "sp_has_initialized_rc"; 
	private static final String REMOTE_CONFIG_FILE = "rc.json";
	private static final String SP_VERSION_KEY = "rc_version";
	private String mConfigLocation;
	private long mUpdateTime;
	private SharedPreferences mPreferences;
	private Context mContext;
	private ArrayList<RemoteConfigListener> mListeners;

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

	@SuppressLint("NewApi")
	public synchronized void init(Context context, int version) {
		mContext = context;
		mConfigLocation = mContext.getString(mContext.getResources().getIdentifier("rc_config_location", "string", mContext.getPackageName()));
		mUpdateTime = mContext.getResources().getInteger(mContext.getResources().getIdentifier("rc_config_update_interval", "integer", mContext.getPackageName()));		
		mPreferences = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
		int oldVersion = mPreferences.getInt(SP_VERSION_KEY, -1);
		if(!mPreferences.getBoolean(REMOTE_CONFIG_INITIALIZER, false) || version>oldVersion) {
			initializeConfigFile();
			if(Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD) {
				mPreferences.edit().putInt(SP_VERSION_KEY, version).apply();
			} else {
				mPreferences.edit().putInt(SP_VERSION_KEY, version).commit();
			}
		}
	}

	@SuppressLint("NewApi")
	private void initializeConfigFile() {
		// Start with parsing the assets/rc.json file into JSONObject
		JSONObject remoteConfig = initialFileToJsonObject();
		if(remoteConfig!=null) {
			jsonObjectIntoPreferences(remoteConfig, true);
		} else {
			throw new RuntimeException("Unable to read rc.json file. Are you sure it exists in the assets folder?");
		}
		Editor editor = mPreferences.edit();
		editor.putBoolean(REMOTE_CONFIG_INITIALIZER, true);
		if(Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD) {
			editor.apply();
		} else {
			editor.commit();
		}
		checkForUpdate(); // We'll fetch new config on launch
	}

	@SuppressLint("NewApi")
	private synchronized void jsonObjectIntoPreferences(final JSONObject jsonObject, boolean initial) {
		Editor editor = mPreferences.edit();
		ArrayList<String> changedKeys = new ArrayList<String>();
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
					if(oldValue==null && !initial)
						continue;
					String newValue = ((JSONArray)value).toString();
					if(!newValue.equals(oldValue)){
						editor.putString(newKey,newValue);
						changedKeys.add(newKey);
					}
				} else if(value instanceof String) {
					String oldValue = mPreferences.getString(newKey, null);
					if(oldValue==null && !initial)
						continue;
					String newValue = (String)value;
					if(!newValue.equals(oldValue)){
						editor.putString(newKey,newValue);
						changedKeys.add(newKey);
					}

				} else if(value instanceof Integer) {
					int oldValue = mPreferences.getInt(newKey, -1);
					if(oldValue==-1 && !initial)
						continue;
					int newValue = ((Integer)value).intValue();
					if(newValue != oldValue){
						editor.putInt(newKey,newValue);
						changedKeys.add(newKey);
					}
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		if(Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD) {
			editor.apply();
		} else {
			editor.commit();
		}
		//Let someone know we have a new value
		for (int i = 0; i < changedKeys.size(); i++) {
			Log.d(TAG, String.format(Locale.getDefault(), "Changed remote config value: %s", changedKeys.get(i)));
			if(mListeners!=null && mListeners.size()>0) {
				for(RemoteConfigListener listener : mListeners) {
					listener.onDownloadComplete(changedKeys.get(i));	
				}
			}
		}
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

	public void checkForUpdate() {
		if(isNetworkConnection(mContext) && RemoteConfig.shouldUpdate(mPreferences, mUpdateTime)) {
			// Fetch the config
			new FetchConfigAsyncTask().execute();
		}
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
			if(Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD) {
				editor.apply();
			} else {
				editor.commit();
			}
			return true;
		}
		return false;
	}

	/**
	 * Checks if there is wifi or mobile connection available 
	 * @param context The application context
	 * @return true if there is network connection available
	 */
	public static boolean isNetworkConnection(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		return activeNetwork != null && activeNetwork.isConnected();
	}

	public interface RemoteConfigListener {
		/**
		 * This method is called when the config has been downloaded and it's values put into shared preferences
		 * 
		 * @param key The key for the new value in shared preferences
		 */
		public void onDownloadComplete(String map);
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

	private class FetchConfigAsyncTask extends AsyncTask<Void, Void, JSONObject> {

		@Override
		protected JSONObject doInBackground(Void... params) {
			return Utils.readJSONFeed(mConfigLocation, null);
		}

		@Override
		protected void onPostExecute(JSONObject config) {
			if(config!=null) {
				jsonObjectIntoPreferences(config, false);
			} else {
				Log.e(TAG, "Unable to read remote config");
			}
		}
	}
}

