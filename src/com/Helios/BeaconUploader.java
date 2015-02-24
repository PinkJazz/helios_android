package com.Helios;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;


class BeaconUploader extends AsyncTask<Void, Void, Boolean>{
    private final String TAG = "Helios_" + getClass().getSimpleName(); 
	protected Context con;

	private String KEY_PREFIX;

	// used to access UI thread for toasts
	private static Handler handler = new Handler(Looper.getMainLooper());
		
	protected String mEmail;
	protected String token;
	private boolean WifiUploadOnly;

	private CognitoHelper cognitoHelperObj;
	
	private BeaconInfo beaconInfo;
	Set<BeaconInfo> staticBeacons = new HashSet<BeaconInfo>();

	BeaconUploader(Context con, String mEmail, String token, CognitoHelper cognitoHelper, BeaconInfo beaconInfo, 
			Map<String, BeaconInfo> staticBeacons, long observationTime, boolean WifiUploadOnly) {
		// used to upload bluetooth beacon details to Amazon S3
		this.con = con;
		this.mEmail = mEmail;
		this.token = token;
		
		this.cognitoHelperObj = cognitoHelper;
		
		this.beaconInfo = beaconInfo;
		this.WifiUploadOnly = WifiUploadOnly;
		
		getValidStaticBeacons(beaconInfo, staticBeacons, observationTime);
		
	}
	
	private Set<BeaconInfo> getValidStaticBeacons(BeaconInfo observedBeacon, Map<String, BeaconInfo> staticBeacons, long observationTime){
		/*
		 * This function takes the list of static beacon but only uploads static beacons observed 
		 * less than a second before observing the beacon we upload
		 */
		for(BeaconInfo beacon: staticBeacons.values()){			
			if (beacon.getTimestamp() > observationTime - 1000)
				this.staticBeacons.add(beacon);
		}
		
		return this.staticBeacons;
	}

	protected Boolean doInBackground(Void... params) {
		// no upload if user wants to upload on wifi only and we are not on Wifi
		this.KEY_PREFIX = cognitoHelperObj.getIdentityID();		

		if (WifiUploadOnly && !Helpers.isWifiConnected(con)){
			Log.i(TAG, "Upload unsuccessful - not on Wifi");
			Helpers.displayToast(handler, con, "Upload unsuccessful - not on Wifi", Toast.LENGTH_LONG);
			return false;
		}
		// we are either on Wifi connection or user is fine with using mobile data
		// so go ahead with the upload
		try {
				new ServletUploaderAsyncTask(con, constructPayload(), Config.BEACON_UPLOAD_POST_TARGET).execute();
			}		
		catch (Exception ex) {
			onError("Following Error occured, please try again. "
					+ ex.getMessage(), ex);
		}
		return false;
	}

	private String constructPayload() {
		// Add your data to be POSTed
		try {
			JSONObject payloadJSONObj = new JSONObject();

			payloadJSONObj.put("Email", mEmail);
			payloadJSONObj.put("Token", token);
			
			// put in details of observed beacon
			JSONObject beaconObj = new JSONObject();
			beaconObj.put("UniqueKey", beaconInfo.getBeaconUniqueKey());
			beaconObj.put("Timestamp", Long.toString(beaconInfo.getTimestamp()));
			beaconObj.put("Latitude", Double.toString(beaconInfo.getLocation().getLatitude()));
			beaconObj.put("Longitude", Double.toString(beaconInfo.getLocation().getLongitude()));
			beaconObj.put("Proximity", beaconInfo.getProximity());
			beaconObj.put("FriendlyName", beaconInfo.friendlyName);

			payloadJSONObj.put("Beacon", beaconObj);
			
			// now put in details of static beacon, if any
			JSONArray staticBeaconArr = new JSONArray();
			for (BeaconInfo beacon : staticBeacons) {
				JSONObject staticBeacon = new JSONObject();

				staticBeacon.put("StaticBeaconUniqueKey", beacon.getBeaconUniqueKey());
				staticBeacon.put("StaticBeaconProximity", beacon.getProximity());
				staticBeacon.put("StaticBeaconRSSI", Double.toString(beacon.getRSSI()));
				staticBeacon.put("StaticBeaconFriendlyName", beacon.friendlyName);

				staticBeaconArr.put(staticBeacon);
			}
			payloadJSONObj.put("Static Beacons", staticBeaconArr);
			return payloadJSONObj.toString();

		} catch (JSONException jse) {
			Log.w(TAG, "JSON Exception " + jse.getMessage());
			return null;
		}
	}

	protected void onError(String msg, Exception e) {
		if (e != null) {
			Log.e(TAG, "Exception: ", e);
		}
		Helpers.displayToast(handler, con, msg, Toast.LENGTH_SHORT);; // will be run in UI thread
	}
}
