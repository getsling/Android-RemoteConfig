package is.gangverk.remoteconfig;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Pair;

public class Utils {

	/**
	 * Reads a stream and writes it into a string. Closes inputStream when done.
	 * @param inputStream The stream to read
	 * @return A string, containing stream data
	 * @throws java.io.IOException
	 */
	public static String stringFromStream(InputStream inputStream) throws java.io.IOException{
		String encoding = "UTF-8";
		StringBuilder builder = new StringBuilder();
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, encoding));
		String line;
		while((line = reader.readLine()) != null) {
			builder.append(line);
		}
		reader.close();
		return builder.toString();
	}

	private static final int HTTP_CONNECTION_TIMEOUT = 80000;
	private static final int HTTP_SOCKET_TIMEOUT = 100000;

	/**
	 * Get the default http params (to prevent infinite http hangs)
	 * @return reasonable default for a HttpClient
	 */
	public static void setHttpTimeoutParams(DefaultHttpClient httpClient) {
		HttpParams httpParameters = new BasicHttpParams();
		// connection established timeout
		HttpConnectionParams.setConnectionTimeout(httpParameters, HTTP_CONNECTION_TIMEOUT);
		// socket timeout
		HttpConnectionParams.setSoTimeout(httpParameters, HTTP_SOCKET_TIMEOUT);
		httpClient.setParams(httpParameters);
	}

	public static String readJSONFeedString(String urlString, ArrayList<Pair<String, String>> headers) {
		if(urlString==null)
			return null;
		String stringResponse = null;
		DefaultHttpClient httpClient = new DefaultHttpClient();
		Utils.setHttpTimeoutParams(httpClient);
		try {
			HttpRequestBase httpRequest = new HttpGet();
			httpRequest.addHeader("Content-Type", "application/json");
			if(headers != null) {
				for(int i = 0; i<headers.size() ;i++) {
					httpRequest.addHeader(headers.get(i).first, headers.get(i).second);
				}
			}
			httpRequest.setURI(URI.create(urlString));
			HttpResponse response = httpClient.execute(httpRequest);
			StatusLine statusLine = response.getStatusLine();
			int statusCode = statusLine.getStatusCode();
			if(statusCode==200) {
				HttpEntity entity = response.getEntity();
				stringResponse = Utils.stringFromStream(entity.getContent());
			}
		} catch (Exception e) {
			e.printStackTrace();
		} 
		return stringResponse;
	}

	public static JSONObject readJSONFeed(String urlString, ArrayList<Pair<String, String>> headers) {
		try {
			String jsonString = readJSONFeedString(urlString, headers);
			if(jsonString==null) return null;
			return new JSONObject(jsonString);
		} catch (JSONException e) {                        
			e.printStackTrace();
		}
		return null;
	}

}
