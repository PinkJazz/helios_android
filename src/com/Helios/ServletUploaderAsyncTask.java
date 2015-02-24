package com.Helios;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;


class ServletUploaderAsyncTask extends AsyncTask<Void, Void, Boolean>{
    private final String TAG = "Helios_" + getClass().getSimpleName(); 
	protected Context con;

	// used to access UI thread for toasts
	private static Handler handler = new Handler(Looper.getMainLooper());
		
	final private String payloadJSONString;
	final private String postTarget;
		
	
	ServletUploaderAsyncTask(Context con, final String payloadJSONString, final String postTarget) {
		this.con = con;
		this.payloadJSONString = payloadJSONString;
		this.postTarget = postTarget;
	}
	
	protected Boolean doInBackground(Void... params) {
		// no upload if user wants to upload on wifi only and we are not on Wifi

		if (Config.WiFiUploadOnly && !Helpers.isWifiConnected(con)){
			Log.i(TAG, "Upload unsuccessful - not on Wifi");
			Helpers.displayToast(handler, con, "Upload unsuccessful - not on Wifi", Toast.LENGTH_LONG);
			return false;
		}
		// we are either on Wifi connection or user is fine with using mobile data
		// so go ahead with the upload
		try {
				return uploadBeacon();
			}		
		catch (Exception ex) {
			onError("Following Error occured, please try again. "
					+ ex.getMessage(), ex);
		}
		return false;
	}

	protected boolean uploadBeacon() {
		URL url;
		HttpURLConnection conn;
		try {
			url = new URL(postTarget);
			conn = (HttpURLConnection) url.openConnection();
		} catch (MalformedURLException mue) {
			Log.w(TAG, "Malformed URL Exception " + mue.getMessage());
			return false;
		} catch (IOException e) {
			Log.w(TAG, "Network Exception when POSTing beacon data " + e.getMessage());
			return false;
		}
		// connection was opened successfully if we got here
		Log.i(TAG, "POSTing " + payloadJSONString);
		try {			
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");  
			conn.setFixedLengthStreamingMode(payloadJSONString.getBytes().length);
			conn.setRequestProperty("Content-Type","application/json");   

			OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream());
			Log.v(TAG, "Sending data now");
			osw.write(payloadJSONString);
			osw.flush();
			osw.close();
			Log.v(TAG, "Response status code is " + conn.getResponseCode());
			return true;

		} catch (ClientProtocolException e) {
			Log.w(TAG, "Protocol Exception when POSTing beacon data " + e.getMessage());
			return false;
		} catch (IOException e) {
			Log.w(TAG, "Network Exception when POSTing beacon data " + e.getMessage());
			return false;
		} catch (Exception e) {
			Log.w(TAG, "Exception when POSTing beacon data " + e.getMessage());
			return false;
		} finally {
			conn.disconnect();
		}
	}

		

	protected void onError(String msg, Exception e) {
		if (e != null) {
			Log.e(TAG, "Exception: ", e);
		}
		Helpers.displayToast(handler, con, msg, Toast.LENGTH_SHORT);; // will be run in UI thread
	}
}
