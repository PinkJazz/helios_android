package com.Helios;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationServices;
import com.kontakt.sdk.android.configuration.ForceScanConfiguration;
import com.kontakt.sdk.android.configuration.MonitorPeriod;
import com.kontakt.sdk.android.connection.OnServiceBoundListener;
import com.kontakt.sdk.android.device.Beacon;
import com.kontakt.sdk.android.device.Region;
import com.kontakt.sdk.android.factory.Filters;
import com.kontakt.sdk.android.manager.BeaconManager;

public class BluetoothMonitorService extends Service 
	implements BeaconManager.RangingListener, ConnectionCallbacks, OnConnectionFailedListener {

	private int NOTIFICATION_ID = 112;
	private final String TAG = "Helios_" + getClass().getSimpleName();
	private final String title = "Helios Bluetooth Monitor";
	
	// used to access UI thread for toasts
	private Handler handler = new Handler(Looper.getMainLooper());
	private Context con;

	private String mEmail;
	private String token;

	// used for location services using new Location API
	private GoogleApiClient mGoogleApiClient;
	private Location mLocation;

//	BeaconManager beaconManager;
	KontaktBeaconManagerBridge kontaktBeaconManager;
	private Map<String, BeaconInfo> discoveredBeacons = new HashMap<String, BeaconInfo>();
	private Map<String, BeaconInfo> monitoredBeacons = new HashMap<String, BeaconInfo>();
	private Map<String, BeaconInfo> staticBeacons = new HashMap<String, BeaconInfo>();

	// used to log in to Amazon Web Services and create client object
	// to upload to S3 and send messages to SQS
	CognitoHelper cognitoHelperObj;

	public void onCreate(){
		con = this;
		
		mGoogleApiClient = new GoogleApiClient.Builder(this)
		.addConnectionCallbacks(this)
		.addOnConnectionFailedListener(this)
		.addApi(LocationServices.API).build();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	public int onStartCommand(Intent intent, int flags, int startID) {
		// gets email and access token from calling activity
		mEmail = intent.getStringExtra(LoginActivity.EMAIL_MSG);
		token = intent.getStringExtra(LoginActivity.TOKEN_MSG);
		String requestType = intent.getStringExtra(TokenFetcherTask.REQUEST_TYPE);
		Log.v(TAG, "onStartCommand received request " + requestType);		

		if (requestType.equals(TokenFetcherTask.REQUEST_TYPE_START)) {
			Log.i(TAG, "Started service for user " + mEmail);
			Helpers.createStopPauseNotification(title, "Stop", "Pause",
					this, BluetoothMonitorService.class, token, mEmail, NOTIFICATION_ID);
			mGoogleApiClient.connect();

	        cognitoHelperObj = new CognitoHelper(this, token);
	        cognitoHelperObj.doCognitoLogin();
			if(!Config.WiFiUploadOnly || Helpers.isWifiConnected(this))
				cognitoHelperObj.sendCognitoMessage(mEmail);
			
			new Thread(new BeaconListDownloader()).start();
		}

		if (requestType.equals(TokenFetcherTask.REQUEST_TYPE_PAUSE)) {
			Log.i(TAG, "Paused recording for user " + mEmail);
			disconnectBeaconManager();
			Helpers.createStopPlayNotification(title, "Stop", "Monitor",
					this, BluetoothMonitorService.class, token, mEmail, NOTIFICATION_ID);
		}

		if (requestType.equals(TokenFetcherTask.REQUEST_TYPE_PLAY)) {
			Log.i(TAG, "Restarted recording for user " + mEmail);
			Helpers.createStopPauseNotification(title, "Stop", "Pause",
					this, BluetoothMonitorService.class, token, mEmail, NOTIFICATION_ID);
			// initializeBeaconManager();
			kontaktBeaconManager = new KontaktBeaconManagerBridge(this, this);
			connectBeaconManager();
		}

		if (requestType.equals(TokenFetcherTask.REQUEST_TYPE_STOP)) {
			Log.i(TAG, "Stopped service for user " + mEmail);
			stopSelf();
		}

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		disconnectBeaconManager();
		kontaktBeaconManager = null;
		if (mGoogleApiClient.isConnected())
			mGoogleApiClient.disconnect();
	}

	
	private void disconnectBeaconManager() {
		kontaktBeaconManager.disconnectBeaconManager();
	}

	private void connectBeaconManager() {
		try {
			kontaktBeaconManager.connectBeaconManager();
		} catch (RemoteException e) {
			showRemoteException(e);			
		}
	}

	private void showRemoteException(RemoteException e) {
		Log.e(TAG, "Unrecoverable error when connecting beacon manager " + e.getMessage());
		Helpers.showAlert(con, "Beacon error", "Unrecoverable error when connecting beacon manager");
		stopSelf();
	}
	
	// Methods implemented for BeaconManager.RangingListener
	public void onBeaconsDiscovered(final Region region, final List<Beacon> beacons) {
		String beaconID, beaconUniqueId;
		String prox, text;

		if (mGoogleApiClient.isConnected())
			mLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
		else
			mLocation = null;

		for (Beacon beacon : beacons) {
			BeaconInfo beaconInfo = new BeaconInfo(beacon, mLocation);
			beaconID = beaconInfo.getBeaconUniqueKey();
			prox = beaconInfo.getProximity();
			beaconUniqueId = beaconInfo.getBeaconUniqueId();

			text = "Discovered beacon name " + beaconUniqueId;
			Log.v(TAG, text + " " + beaconID + " " + prox);

			if (beaconInfo.isStaticBeacon()){
			// add to static beacon list and maintain the size at a max of 3
				updateStaticBeaconList(beaconInfo);
				continue;
			}
			// if we picked up some random beacon that does not belong to this
			// user, ignore it. This also ignores static beacons so that they do not get uploaded
			if (!monitoredBeacons.containsKey(beaconID))
				continue;

			String friendlyName = monitoredBeacons.get(beaconID).friendlyName;
			
			if (isBeaconUploadable(beaconInfo)) {
				beaconInfo.friendlyName = friendlyName;
				Log.i(TAG, "Inserted " + beaconUniqueId + " " + beaconID + " " + prox);
				discoveredBeacons.put(beaconID, beaconInfo);
				new BeaconUploader(this, mEmail, cognitoHelperObj, beaconInfo, staticBeacons, System.currentTimeMillis(), Config.WiFiUploadOnly).execute();
			}
		}
	}

	private Map<String, BeaconInfo> updateStaticBeaconList(BeaconInfo beacon){
		String uniqueKey = beacon.getBeaconUniqueKey();
		
		Log.v(TAG, beacon.getBeaconUniqueId() + " is a static beacon");
		staticBeacons.put(uniqueKey, beacon);
		// if there are now more than 3 beacons in the map, we throw out 
		// the oldest beacon observation in the map and put this one in
		Long minTimestamp = Long.MAX_VALUE;
		Long currentTimestamp;
		String minKey = "";
		
		if(staticBeacons.size() > 3){
			for(String observedStaticBeacon: staticBeacons.keySet()){
				currentTimestamp = staticBeacons.get(observedStaticBeacon).getTimestamp();
				if(currentTimestamp < minTimestamp){
					minTimestamp = currentTimestamp;
					minKey = observedStaticBeacon;
				}
			} // for
			Log.v(TAG, "Removed " + minKey + " from static beacon map");
			staticBeacons.remove(minKey);
		}
		return staticBeacons;
	}
	
	private boolean isBeaconUploadable(BeaconInfo newBeaconInfo) {
		// decides whether a beacon observation should be uploaded to S3 or not
		// depending on when it was last seen
		String beaconID = newBeaconInfo.getBeaconUniqueKey();
		BeaconInfo beaconObs = discoveredBeacons.get(beaconID);

		// if we haven't seen this beacon before, upload it
		if (beaconObs == null)
			return true;

		double dist = beaconObs.calcDistance(newBeaconInfo);

		if (newBeaconInfo.matchProximity(beaconObs)) {
			// if we have moved more than 10 metres and the proximity is unch,
			// object might have moved
			if (dist > 10)
				return true;
			else
				return false;
		} else {
			// proximity indicator has changed
			// we assume if proximity indicator changes and the distance also changed
			// we have moved but object has not so no need to upload its location
			if (dist > 10)
				return false;
			else {
				// proximity indicator has changed but our location has not
				// either object has moved or there is some noise in the
				// proximity indicator
				// TODO: this should be some moving avg of proximity indicator
				// rather than a timestamp
				if (beaconObs.calcTimeDiff(newBeaconInfo) > 30000)
					return true;
				else
					return false;
			}
		}
	}
	/*
	 * Callback methods for Google Location Services
	 */

	@Override
	public void onConnected(Bundle connectionHint) {
		Log.v(TAG, "Location services connected");
	}

	/*
	 * Called by Google Play services if the connection to GoogleApiClient drops
	 * because of an error.
	 */
	public void onDisconnected() {
		Log.v(TAG, "Disconnected. Location information no longer available.");
	}

	@Override
	public void onConnectionSuspended(int cause) {
		// The connection to Google Play services was lost for some reason. We
		// call connect() to
		// attempt to re-establish the connection.
		Log.i(TAG, "Location services connection suspended");
		mGoogleApiClient.connect();
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		/*
		 * we simply notify user that Locations will not be recorded
		 */
		Log.e(TAG, "Error when connecting to Location Services "
				+ connectionResult.getErrorCode()
				+ " Location services not available");
		Helpers.displayToast(handler, con, "Location services not available",
				Toast.LENGTH_LONG);
	}

	// Inner class to download list of beacons for a given user
	private class BeaconListDownloader implements Runnable {
		// must be called after logging in via Cognito

		private final String TAG_DOWNLOAD = "Helios_" + getClass().getSimpleName();

		BeaconListDownloader() {
		}

		public void run() {
			String KEY_PREFIX = "";
			try{
				KEY_PREFIX = cognitoHelperObj.getIdentityID();
			}
			catch(Exception e){
				Helpers.displayToast(handler, con, "Unrecoverable error when logging into Amazon cognito", Toast.LENGTH_LONG);
				Log.d(TAG, "Cognito Login error - " + e.getMessage());
				BluetoothMonitorService.this.stopSelf();
			}
			String key = KEY_PREFIX + Config.BEACON_LIST;
			
			Helpers.displayToast(handler, con, "Downloading beacons to monitor..." , Toast.LENGTH_SHORT);
			String line;
			try {
				S3ObjectInputStream istream = cognitoHelperObj.s3Client.getObject(Config.S3_BUCKET_NAME, key)
						.getObjectContent();
				BufferedReader reader = new BufferedReader(new InputStreamReader(istream));

				while ((line = reader.readLine()) != null) {
					String [] beaconDetails = line.split("\\s*,\\s*");
					BeaconInfo beac = new BeaconInfo(beaconDetails[0], Integer.valueOf(beaconDetails[1]), 
							Integer.valueOf(beaconDetails[2]), beaconDetails[3]);
					monitoredBeacons.put(beac.getBeaconUniqueKey(), beac);
					
					Log.i(TAG_DOWNLOAD, "Monitoring beacon " + beaconDetails[0] + " " + Integer.valueOf(beaconDetails[1]) + " " + 
							Integer.valueOf(beaconDetails[2]) + " " + beaconDetails[3]);					
					Log.i(TAG_DOWNLOAD, "Added beacon " + beac.getBeaconUniqueKey());
				}
				istream.close();
			} catch (AmazonServiceException ase) {
				Log.i(TAG_DOWNLOAD, key + " file could be missing or invalid - " + ase.getMessage());
			} catch (AmazonClientException ace) {
				Log.i(TAG_DOWNLOAD, key + " file could be missing or invalid - " + ace.getMessage());
			} catch (IOException ioe) {
				Log.e(TAG_DOWNLOAD, key + " file could be missing or invalid - " + ioe.getMessage());
			}
			// initialize beacon manager on UI thread because Kontakt.io SDK requires this
			handler.post(new Runnable() {
				public void run() {
					kontaktBeaconManager = new KontaktBeaconManagerBridge(BluetoothMonitorService.this, BluetoothMonitorService.this);
					connectBeaconManager();
				}
			});
		}
	}

}
