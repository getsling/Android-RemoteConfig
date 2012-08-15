package com.gangverk.remoteconfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

public class RemoteConfig {
	public static final String LAST_DOWNLOADED_CONFIG_KEY = "lastDownloadedConfig";
	private Context context;
	private String configLocation;
	private long updateTime;
	private HashMap<String, String> availableMaps;
	private SharedPreferences preferences;
	OnDownloadedConfigListener onDownloadedConfigListener  = null; 

	public RemoteConfig(Context context) {
		this(context, null, null);
	}

	public RemoteConfig(Context context, String configLocation, HashMap<String, String> availableMaps) {
		this(context, configLocation, availableMaps, 3600);
	}

	public RemoteConfig(Context context, String configLocation, HashMap<String, String> availableMaps, long updateTime) {
		this.context = context;
		this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
		this.updateTime = updateTime*1000; // Convert to seconds
		if(configLocation != null && availableMaps != null) {
			this.configLocation = configLocation;
			this.availableMaps = availableMaps;
			InitializeClass();
		}
	}

	private void InitializeClass () {
		// Iterate through availablemaps and put into userdefaults if there is nothing there to begin with		
		Editor editor = this.preferences.edit();
		ArrayList<String> keys = new ArrayList<String>();
		for (Map.Entry<String, String> entry : availableMaps.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if(!this.preferences.contains(key)) {
				editor.putString(key, value);
			} else {
				if(!this.preferences.getString(key, null).equals(value)) {
					keys.add(key);
				}
			}
		}
		// Replace the keys in availableMaps with the ones in the shared preferences
		for(int i=0;i<keys.size();i++) {
			String key = keys.get(i);
			this.availableMaps.put(key,this.preferences.getString(key, null));
		}
		editor.commit();

		if(RemoteConfig.shouldUpdate(this.preferences, this.updateTime)) {
			// Fetch the config
			new FetchConfigAsyncTask(this.context, this.configLocation, this.availableMaps).execute();
		}
	}

	protected void setConfigLocation(String location) {
		this.configLocation = location;
		if(this.availableMaps != null) {
			InitializeClass();
		}
	}

	protected void setAvailableMaps(HashMap<String, String> map) {
		this.availableMaps = map;
		if(this.configLocation != null) {
			InitializeClass();
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
		return this.preferences.getString(mapping, null);
	}

	public int getInt(String mapping) {
		return this.preferences.getInt(mapping, -1);
	}

	private synchronized static boolean shouldUpdate(SharedPreferences preferences, long updateTime) {
		long lastDownloadedConfig = preferences.getLong(RemoteConfig.LAST_DOWNLOADED_CONFIG_KEY, -1);
		if(lastDownloadedConfig < 0 || System.currentTimeMillis() - lastDownloadedConfig > updateTime) {
			Editor editor = preferences.edit();
			editor.putLong(RemoteConfig.LAST_DOWNLOADED_CONFIG_KEY, System.currentTimeMillis());
			editor.commit();
			return true;
		}
		return false;
	}

	/**
	 * Set how often this config should be updated
	 * 
	 * @param updateTime The update time in seconds
	 */
	public void setUpdateTime(long updateTime) {
		this.updateTime = updateTime*1000;
	}

	public interface OnDownloadedConfigListener {
		/**
		 * This method is called when the config has been downloaded and it's values put into shared preferences
		 * 
		 * @param map The new map for value in shared preferences
		 * @param value The new value for map in shared preferences
		 */
		public abstract void onDownloadedComplete(String map, String value);
	}

	// Allows the user to set an Listener and react to the event
	public void setOnDownloadedConfigListener(OnDownloadedConfigListener listener) {
		onDownloadedConfigListener = listener;
	}

	private class FetchConfigAsyncTask extends AsyncTask<Void, Void, JSONArray>
	{
		private Context context;
		private String configLocation;
		private HashMap<String, String> availableMaps;

		public FetchConfigAsyncTask(Context context, String configLocation, HashMap<String, String> availableMaps) {
			this.context = context;
			this.configLocation = configLocation;
			this.availableMaps = availableMaps;
		}

		@Override
		protected JSONArray doInBackground(Void... params) {
			String feed = JSONMethods.readJSONFeed(configLocation, null, null);
			JSONArray array = JSONMethods.parseJSON(feed);
			return array;
		}

		@Override
		protected void onPostExecute(JSONArray array) {
			if(array != null) {
				SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this.context);
				Editor editor = settings.edit();
				for(int i=0;i<array.length();i++) {
					try {
						JSONObject object = array.getJSONObject(i); // Object is the new values gotten from the internet
						for (Map.Entry<String, String> entry : this.availableMaps.entrySet()) {
							String key = entry.getKey();
							String oldValue = entry.getValue();
							if(object.has(key)) {
								String newValue = object.getString(key);
								if(oldValue == null || !newValue.equals(oldValue)){
									editor.putString(key,newValue);
									// Let someone know we have a new value
									if(onDownloadedConfigListener!=null) {
										onDownloadedConfigListener.onDownloadedComplete(key,newValue);
									}
								}
							}
						}					
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				editor.commit();
			}
		}
	}
}
