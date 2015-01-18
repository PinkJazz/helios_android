package com.Helios;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;
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

public class BluetoothMonitorActivity extends Activity implements BeaconManager.RangingListener, ConnectionCallbacks,
		OnConnectionFailedListener {

	BeaconManager beaconManager;
	private static int REQUEST_CODE_ENABLE_BLUETOOTH = 1001;
	private final String TAG = "Helios_" + getClass().getSimpleName();

	// used to access UI thread for toasts
	private static Handler handler = new Handler(Looper.getMainLooper());
	private Context con;

	private String mEmail;
	private String mToken;

	private CognitoHelper cognitoHelperObj;

	// used for location services using new Location API
	private GoogleApiClient mGoogleApiClient;
	private Location mLocation;

	private List<TextView> textViews = new ArrayList<TextView>();
	private TextView headerTextview;
	private Map<String, BeaconInfo> discoveredBeacons = new HashMap<String, BeaconInfo>();
	private Map<String, BeaconInfo> monitoredBeacons = new HashMap<String, BeaconInfo>();
	private Map<String, TextView> beaconDisplay = new HashMap<String, TextView>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		con = this; // used to access UI thread since RangingListener does not
					// run in UI thread

		Intent intent = getIntent();
		mEmail = intent.getStringExtra(LoginActivity.EMAIL_MSG);
		mToken = intent.getStringExtra(LoginActivity.TOKEN_MSG);

		mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this).addApi(LocationServices.API).build();

		mGoogleApiClient.connect();

		cognitoHelperObj = new CognitoHelper(this, mToken);
		cognitoHelperObj.doCognitoLogin();
		if(!Config.WiFiUploadOnly || Helpers.isWifiConnected(this))
			cognitoHelperObj.sendCognitoMessage(mEmail);
		// add displays to show beacon location

		textViews.add((TextView) findViewById(R.id.tv1));
		textViews.add((TextView) findViewById(R.id.tv2));
		textViews.add((TextView) findViewById(R.id.tv3));
		textViews.add((TextView) findViewById(R.id.tv4));
		textViews.add((TextView) findViewById(R.id.tv5));

		headerTextview = (TextView) findViewById(R.id.header);
		// download list of beacons belonging to this user
		new Thread(new BeaconListDownloader()).start();
	}

	/**
	 * 
	 */
	private void initializeBeaconManager() {
		// starts up Kontakt.io beacon manager
		int r = 0;
		for (String beaconID : monitoredBeacons.keySet()) {
			if (r < textViews.size()) {
				beaconDisplay.put(beaconID, textViews.get(r));
			}
			r++;
		}
		beaconManager = BeaconManager.newInstance(this);
		beaconManager.setMonitorPeriod(MonitorPeriod.MINIMAL);
		beaconManager.setForceScanConfiguration(ForceScanConfiguration.DEFAULT);
		beaconManager.addFilter(Filters.newProximityUUIDFilter(java.util.UUID.fromString(Config.PROXIMITY_UUID)));
		// beaconManager.addFilter(Filters.newMajorFilter(29358));

		beaconManager.registerRangingListener(this);
		Log.v(TAG, "Started bluetooth activity with email " + mEmail);

		if (!beaconManager.isBluetoothEnabled()) {
			final Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(intent, REQUEST_CODE_ENABLE_BLUETOOTH);
		} else {
			connectBeaconManager();
		}
	}

	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (beaconManager != null)
			beaconManager.stopRanging();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (beaconManager != null)
			beaconManager.disconnect();
		beaconManager = null;
		if (mGoogleApiClient.isConnected())
			mGoogleApiClient.disconnect();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
			if (resultCode == Activity.RESULT_OK) {
				connectBeaconManager();
			} else {
				Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_LONG).show();
				getActionBar().setSubtitle("Bluetooth not enabled");
			}
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private void connectBeaconManager() {
		try {
			beaconManager.connect(new OnServiceBoundListener() {
				@Override
				public void onServiceBound() {
					try {
						beaconManager.startRanging();
						headerTextview.setText("Monitoring " + monitoredBeacons.size() + " beacons");
					} catch (RemoteException e) {
						Log.e(TAG, e.getMessage());
						e.printStackTrace();
					}
				}
			});
		} catch (RemoteException e) {
			Log.e(TAG, e.getMessage());
			throw new IllegalStateException(e);
		}
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

			// if we picked up some random beacon that does not belong to this
			// user, ignore it
			if (!monitoredBeacons.containsKey(beaconID))
				continue;

			String friendlyName = monitoredBeacons.get(beaconID).friendlyName;
			updateTextView(beaconDisplay.get(beaconID),
					beaconUniqueId + " " + friendlyName + " - " + prox + " RSSI - " + beaconInfo.getRSSI());

			if (isBeaconUploadable(beaconInfo)) {
				beaconInfo.friendlyName = friendlyName;
				Log.i(TAG, "Inserted " + beaconUniqueId + " " + beaconID + " " + prox);
				discoveredBeacons.put(beaconID, beaconInfo);
				new ImageUploader(this, mEmail, cognitoHelperObj, beaconInfo, Config.WiFiUploadOnly).execute();
			}
		}
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

	private void displayToast(final String text, final int toast_length) {
		// helper method uses the main thread to display a toast
		// we use this because if this class is used by a Service
		// as opposed to an Activity, we can't access the UI thread
		// in the normal way using RunOnUIThread
		handler.post(new Runnable() {
			public void run() {
				Toast.makeText(con, text, toast_length).show();
			}
		});
	}

	private void updateTextView(final TextView tv, final String text) {
		// TODO: there is probably a cleaner way to replace calls to this method
		// with a call to runOnUiThread
		handler.post(new Runnable() {
			public void run() {
				tv.setText(text);
			}
		});
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
		Log.e(TAG, "Error when connecting to Location Services " + connectionResult.getErrorCode()
				+ " Location services not available");
		displayToast("Location services not available", Toast.LENGTH_LONG);
	}

	// Inner class to download list of beacons for a given user
	private class BeaconListDownloader implements Runnable {
		// must be called after logging in via Cognito

		private final String TAG_DOWNLOAD = "Helios_" + getClass().getSimpleName();

		BeaconListDownloader() {
		}

		public void run() {
			headerTextview.setText("Downloading beacons to monitor. Please wait...");
			String KEY_PREFIX = cognitoHelperObj.getIdentityID();
			String key = KEY_PREFIX + Config.BEACON_LIST;

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
			// initialize beacon manager on UI thread because Kontakt.io SDK
			// requires this
			BluetoothMonitorActivity.this.runOnUiThread(new Runnable() {
				public void run() {
					initializeBeaconManager();
				}
			});
		}
	}
}
