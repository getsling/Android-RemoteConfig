package com.gangverk.remoteconfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

public class JSONMethods {
	/*
	 * This method takes in a url as a string and returns JSON string from a website
	 */
	public static String readJSONFeed(String url, String[] headers, String[] headerValues) {
		StringBuilder builder = new StringBuilder();
		HttpClient client = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(url);
		if(headers != null) {
			for(int i = 0; i<headers.length ;i++) {
				httpGet.setHeader(headers[i], headerValues[i]);
			}
		}
		try {
			HttpResponse response = client.execute(httpGet);
			StatusLine statusLine = response.getStatusLine();
			int statusCode = statusLine.getStatusCode();
			if(statusCode == HttpURLConnection.HTTP_OK) {
				HttpEntity entity = response.getEntity();
				InputStream content = entity.getContent();
				BufferedReader reader = new BufferedReader(new InputStreamReader(content));
				String line;
				while((line = reader.readLine()) != null) {
					builder.append(line);
				}
			} else {
				return null;
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return builder.toString();
	}
	
	/*
	 * This method parses JSON feed string into JSON
	 */
	public static JSONArray parseJSON(String feed) {
		JSONArray jsonArray = null;
		try {
			if(feed.startsWith("{")) {
				jsonArray = new JSONArray();
				jsonArray.put(new JSONObject(feed));
			} else {
				jsonArray = new JSONArray(feed);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsonArray;
	}
}
