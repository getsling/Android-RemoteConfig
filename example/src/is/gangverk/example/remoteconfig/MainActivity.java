package is.gangverk.example.remoteconfig;

import is.gangverk.remoteconfig.RemoteConfig;
import is.gangverk.remoteconfig.RemoteConfig.RemoteConfigListener;

import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity implements RemoteConfigListener {
	private TextView mStatus;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStatus = (TextView) findViewById(R.id.textview_status);
        RemoteConfig.getInstance().setRemoteConfigListener(this);
        String remoteString = RemoteConfig.getInstance().getString("remoteString");
        int remoteInt = RemoteConfig.getInstance().getInt("remoteInt");
        String remoteDeepString = RemoteConfig.getInstance().getString("remoteObject.remoteObject0");
        String remoteJsonArray = RemoteConfig.getInstance().getString("remoteArray");
        
        try {
			mStatus.setText(String.format("Remote string:%s \nRemote int:%d \nRemote deep string:%s \nRemote json array:%s",remoteString, remoteInt, remoteDeepString, new JSONArray(remoteJsonArray).toString()));
		} catch (JSONException e) {
			e.printStackTrace();
		}
    }

	@Override
	public void onDownloadComplete(String map) {
		mStatus.setText(mStatus.getText() + "\n onDownloadComplete, key=" + map);
	}
    
}
