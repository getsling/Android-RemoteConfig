package is.gangverk.example.remoteconfig;

import is.gangverk.remoteconfig.RemoteConfig;
import android.app.Application;

public class RemoteApplication extends Application {
	private static final int REMOTE_CONFIG_VERSION = 1;
	
	@Override
	public void onCreate() {
		super.onCreate();
		RemoteConfig.getInstance().init(getApplicationContext(), REMOTE_CONFIG_VERSION);
	}	
}
