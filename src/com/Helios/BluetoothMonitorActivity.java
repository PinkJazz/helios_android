package com.Helios;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import android.widget.Toast;

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

public class BluetoothMonitorActivity extends Activity implements BeaconManager.RangingListener, 
		ConnectionCallbacks, OnConnectionFailedListener {

	BeaconManager beaconManager;
	private static int REQUEST_CODE_ENABLE_BLUETOOTH = 1001;
	private final String TAG = "Helios_" + getClass().getSimpleName();

	// used to access UI thread for toasts
	private static Handler handler = new Handler(Looper.getMainLooper());
	private Context con;

	private String mEmail;
	private String mToken;

	private CognitoHelper cognitoHelperObj;

	private Map<String, String> discoveredBeacons = new HashMap<String, String>();

	// used for location services using new Location API
	private GoogleApiClient mGoogleApiClient;
	private Location mLocation;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		con = this; // used to access UI thread since RangingListener does not
					// run in UI thread

		Intent intent = getIntent();
		mEmail = intent.getStringExtra(LoginActivity.EMAIL_MSG);
		mToken = intent.getStringExtra(LoginActivity.TOKEN_MSG);

		mGoogleApiClient = new GoogleApiClient.Builder(this)
		.addConnectionCallbacks(this)
		.addOnConnectionFailedListener(this)
		.addApi(LocationServices.API).build();

		mGoogleApiClient.connect();
		
		cognitoHelperObj = new CognitoHelper(this, mToken);
		cognitoHelperObj.doCognitoLogin();

		beaconManager = BeaconManager.newInstance(this);
		beaconManager.setMonitorPeriod(MonitorPeriod.MINIMAL);
		beaconManager.setForceScanConfiguration(ForceScanConfiguration.DEFAULT);
		beaconManager.addFilter(Filters.newProximityUUIDFilter(java.util.UUID
				.fromString(Config.PROXIMITY_UUID)));
		// beaconManager.addFilter(Filters.newMajorFilter(29358));
		
		beaconManager.registerRangingListener(this);
		Log.v(TAG, "Started bluetooth activity with email " + mEmail);

	}

	protected void onStart() {
		super.onStart();
		if (!beaconManager.isBluetoothEnabled()) {
			final Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(intent, REQUEST_CODE_ENABLE_BLUETOOTH);
		} else {
			connectBeaconManager();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		beaconManager.stopRanging();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
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
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
			});
		} catch (RemoteException e) {
			throw new IllegalStateException(e);
		}
	}

	// Methods implemented for BeaconManager.RangingListener
	public void onBeaconsDiscovered(final Region region, final List<Beacon> beacons) {
		String beaconID;
		String prox, text;
		String value;

		if (mGoogleApiClient.isConnected())
			mLocation = LocationServices.FusedLocationApi
					.getLastLocation(mGoogleApiClient);
		else
			mLocation = null;

		for (Beacon beacon : beacons) {
			text = "Discovered beacon name " + beacon.getBeaconUniqueId();
			BeaconInfo beaconInfo = new BeaconInfo(beacon);
			beaconID = beaconInfo.getBeaconUniqueKey();
			prox = beacon.getProximity().toString();
			Log.i(TAG, text + " " + beaconID + " " + prox);
			
			value = discoveredBeacons.get(beaconID);
			if (value == null) {
				Log.i(TAG, "Inserted " + beacon.getBeaconUniqueId() + " " + beaconID + " " + prox);
				discoveredBeacons.put(beaconID, prox);
				new ImageUploader(this, cognitoHelperObj, beaconInfo, mLocation, true).execute();
				displayToast(text, Toast.LENGTH_SHORT);
			} else if (!(value.contentEquals(prox))) {
				Log.i(TAG, "Inserted different prox for " + beacon.getBeaconUniqueId() + " " + beaconID + " " + prox);
				discoveredBeacons.put(beaconID, prox);
				new ImageUploader(this, cognitoHelperObj, beaconInfo, mLocation, true).execute();
				displayToast(text, Toast.LENGTH_SHORT);
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
		displayToast("Location services not available", Toast.LENGTH_LONG);
	}

}
