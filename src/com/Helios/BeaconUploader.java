package com.Helios;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.NameValuePair;
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

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.sqs.AmazonSQSClient;


class BeaconUploader extends AsyncTask<Void, Void, Boolean>{
    private final String TAG = "Helios_" + getClass().getSimpleName(); 
	protected Context con;

	private String KEY_PREFIX;

	// used to access UI thread for toasts
	private static Handler handler = new Handler(Looper.getMainLooper());
		
	protected String mEmail;
	protected String token;
	private boolean WifiUploadOnly;

	private AmazonS3Client s3Client;
	private AmazonSQSClient sqsQueue;
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
		this.s3Client = cognitoHelper.s3Client;
		this.sqsQueue = cognitoHelper.sqsQueue;
		
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
				return uploadBeacon();
			}		
		catch (AmazonServiceException ase) {
            onError("AmazonServiceException", ase);
            Log.e(TAG, "Error Message:    " + ase.getMessage());
            Log.e(TAG, "HTTP Status Code: " + ase.getStatusCode());
            Log.e(TAG, "AWS Error Code:   " + ase.getErrorCode());
            Log.e(TAG, "Error Type:       " + ase.getErrorType());
            Log.e(TAG, "Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            onError("Caught an AmazonClientException", ace);
            Log.e(TAG, "Error Message: " + ace.getMessage());
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
			url = new URL(Config.POST_TARGET);
			conn = (HttpURLConnection) url.openConnection();
		} catch (MalformedURLException mue) {
			Log.w(TAG, "Malformed URL Exception " + mue.getMessage());
			return false;
		} catch (IOException e) {
			Log.w(TAG, "Network Exception when POSTing beacon data " + e.getMessage());
			return false;
		}
		// connection was opened successfully if we got here
		String payloadObj = constructPayload();
		try {			
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");  
			conn.setFixedLengthStreamingMode(payloadObj.getBytes().length);
			conn.setRequestProperty("Content-Type","application/json");   

			OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream());
			Log.v(TAG, "Sending data now");
			osw.write(payloadObj.toString());
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
			int i = 0;
			for (BeaconInfo beacon : staticBeacons) {
				i++;
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
	
	private byte[] getQuery(List<NameValuePair> params) throws UnsupportedEncodingException
	{
	    StringBuilder result = new StringBuilder();
	    boolean first = true;

	    for (NameValuePair pair : params)
	    {
	        if (first)
	            first = false;
	        else
	            result.append("&");

	        result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
	        result.append("=");
	        result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
	    }

	    return result.toString().getBytes();
	}
}
