package com.Helios;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	BeaconUploader(Context con, String mEmail, CognitoHelper cognitoHelper, BeaconInfo beaconInfo, 
			Map<String, BeaconInfo> staticBeacons, long observationTime, boolean WifiUploadOnly) {
		// used to upload bluetooth beacon details to Amazon S3
		this.con = con;
		this.mEmail = mEmail;
		
		this.cognitoHelperObj = cognitoHelper;
		this.s3Client = cognitoHelper.s3Client;
		this.sqsQueue = cognitoHelper.sqsQueue;
		
		this.beaconInfo = beaconInfo;
		this.WifiUploadOnly = WifiUploadOnly;
		getValidStaticBeacons(beaconInfo, staticBeacons, observationTime);
		
	}
	
	private Set<BeaconInfo> getValidStaticBeacons(BeaconInfo observedBeacon, Map<String, BeaconInfo> staticBeacons, long observationTime){
		
		for(BeaconInfo beacon: staticBeacons.values()){			
			// only add static beacons observed less than a second before observing the beacon we upload
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

	private Boolean uploadBeacon() throws AmazonClientException, AmazonServiceException {
		// uploads info regarding monitored beacon to S3 and sends a message to SQS
		
		Log.i(TAG, "Got identity ID " + KEY_PREFIX);
		
		String key = KEY_PREFIX + "/" + Config.BEACON_FOLDER + "/" + beaconInfo.getBeaconUniqueKey() + "/" + beaconInfo.getTimestamp();
		ObjectMetadata met = new ObjectMetadata();
		met.setContentType("text/plain");
		
		StringBuffer uploadText = new StringBuffer(beaconInfo.toString());
		for(BeaconInfo beacon: staticBeacons){
			uploadText.append(", " + beacon.getBeaconUniqueId());
			uploadText.append(", " + beacon.getProximity());
			uploadText.append(", " + beacon.getRSSI() + "\n");					
		}
		
		for(int i = staticBeacons.size(); i < 3; i++)
			uploadText.append(", \n");
		
		met.setContentLength(uploadText.length());
		InputStream is = new ByteArrayInputStream(uploadText.toString().getBytes());
		PutObjectRequest req = new PutObjectRequest(Config.S3_BUCKET_NAME, key, is, met);
				
		PutObjectResult putResult = s3Client.putObject(req);
		if (putResult != null)
			CognitoHelper.sendSQSMessage(sqsQueue, key);
		Log.i(TAG, "Upload successful");

		return true;
	}

	protected void onError(String msg, Exception e) {
		if (e != null) {
			Log.e(TAG, "Exception: ", e);
		}
		Helpers.displayToast(handler, con, msg, Toast.LENGTH_SHORT);; // will be run in UI thread
	}	

}
