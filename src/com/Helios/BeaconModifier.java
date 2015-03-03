package com.Helios;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.kontakt.sdk.android.connection.BeaconConnection;
import com.kontakt.sdk.android.device.BeaconDevice;

class BeaconModifier implements Runnable{
	private final String TAG = "Helios_" + getClass().getSimpleName();
	ChangeBeaconDetailsActivity mActivity;
	
	private BeaconConnection beaconConn;
	private BeaconDevice beacon;
	private BeaconInfo currentBeaconInfo;	
	private BeaconInfo newBeaconInfo;	
	
	private final int NUM_CHARACTERISTICS = 5;
	private int numCharacteristicsToUpdate;  // counter to make sure all characteristics were updated
	private Handler handler = new Handler(Looper.getMainLooper());
			
	public BeaconModifier(ChangeBeaconDetailsActivity parentActivity, BeaconInfo beaconInfo, BeaconInfo newBeaconInfo) {
		this.mActivity = parentActivity;
		this.beacon = beaconInfo.beacon;
		this.currentBeaconInfo = beaconInfo;
		
		this.newBeaconInfo = newBeaconInfo;
		Log.i(TAG, "Modifying to " + newBeaconInfo.proximityUUID + " " + newBeaconInfo.major + " " + 
				newBeaconInfo.minor + " " + newBeaconInfo.powerLevel);
	}
	
	public void run() {		
		numCharacteristicsToUpdate = NUM_CHARACTERISTICS;
		final BeaconConnection.ConnectionListener connListener = createConnectionListener();
		mActivity.runOnUiThread(new Runnable(){
			public void run(){
				// it appears this initialization has to happen on main thread
		        beaconConn = BeaconConnection.newInstance(mActivity, beacon, connListener);
		        connectBeacon();
			}
		});
	}
	
	private void connectBeacon(){		
		beaconConn.connect();		
	}
	
	void overwriteNextParameter(){
		// clumsy way to deal with the fact that Kontakt's SDK does not allow batch update
		// for all the parameters we need so we have to queue updates and only send an 
		// overwrite after the previous one completed. The onAuthenticationSuccessful method 
		// in the connectionListener calls this method initially and the onWriteSuccess method in the WriteListener
		// calls this method repeatedly after each successful write
		
		switch(numCharacteristicsToUpdate){
		case 0:
			return;
		case 5:
			beaconConn.overwriteProximity(java.util.UUID.fromString(newBeaconInfo.proximityUUID), new KontaktBeaconWriteListener(this, beaconConn, "major"));
			break;
		case 4:
			beaconConn.overwriteMinor(newBeaconInfo.minor, new KontaktBeaconWriteListener(this, beaconConn, "major"));
			break;
		case 3:
			beaconConn.overwriteMajor(newBeaconInfo.major, new KontaktBeaconWriteListener(this, beaconConn, "minor"));
			break;
		case 2:
			beaconConn.overwritePowerLevel(newBeaconInfo.powerLevel, new KontaktBeaconWriteListener(this, beaconConn, "Power Level"));
			break;
		case 1:
			beaconConn.overwriteModelName("Helios", new KontaktBeaconWriteListener(this, beaconConn, "modelName"));
			break;
		}
	}
	private BeaconConnection.ConnectionListener createConnectionListener() {
		return new BeaconConnection.ConnectionListener() {
			@Override
			public void onConnected() {
				Helpers.displayToast(handler, mActivity, "Connected. Updating can take upto 30 seconds... ", Toast.LENGTH_SHORT);

				String name = beaconConn.getBeaconDevice().getBeaconUniqueId();
				int txPower = beaconConn.getBeaconDevice().getTxPower();
				int major = beaconConn.getBeaconDevice().getMajor();
				int minor = beaconConn.getBeaconDevice().getMinor();
				String password = new String(beaconConn.getBeaconDevice().getPassword());
				int batteryLevel = beaconConn.getBeaconDevice().getBatteryPower();

				Log.i(TAG, "Details are: name is " + name + " " + txPower + " " + major + " " + minor);
				Log.i(TAG, "TxPower is " + txPower + " battery is " + batteryLevel);

				Log.i(TAG, "Connected");
			}

			@Override
			public void onAuthenticationSuccess(final BeaconDevice.Characteristics characteristics) {
				Helpers.displayToast(handler, mActivity, "Authentication Success", Toast.LENGTH_SHORT);
				Log.i(TAG, "Authentication Success");
				overwriteNextParameter();
			}

			@Override
			public void onAuthenticationFailure(final int failureCode) {
				// TODO: Attempt to roll back changes and do callback to onBeaconModified
				switch (failureCode) {
				case BeaconConnection.FAILURE_UNKNOWN_BEACON:
					Helpers.displayToast(handler, mActivity, "Failure unknown beacon", Toast.LENGTH_SHORT);
					Log.w(TAG, "Failure unknown beacon");
					break;
				case BeaconConnection.FAILURE_WRONG_PASSWORD:
					Helpers.displayToast(handler, mActivity, "Failure wrong password", Toast.LENGTH_SHORT);
					Log.w(TAG, "Failure wrong password");
					break;
				default:
					throw new IllegalArgumentException(String.format("Unknown beacon connection failure code: %d",
							failureCode));
				}
			}

			@Override
			public void onCharacteristicsUpdated(final BeaconDevice.Characteristics characteristics) {
				numCharacteristicsToUpdate--;
				Log.i(TAG, (NUM_CHARACTERISTICS - numCharacteristicsToUpdate) + " characteristics updated");
				if(numCharacteristicsToUpdate == 0){
					Helpers.displayToast(handler, mActivity, "Characteristics updated", Toast.LENGTH_SHORT);
					Log.i(TAG, "All characteristics updated successfully");
					Log.i(TAG, "New Power level is " + characteristics.getPowerLevel());
					Log.i(TAG, "New Major is " + characteristics.getMajor());
					Log.i(TAG, "New Minor is " + characteristics.getMinor());
					beaconConn.disconnect();
					mActivity.onBeaconModifiedSuccess(newBeaconInfo);
				}
			}

			@Override
			public void onErrorOccured(final int errorCode) {
				switch (errorCode) {

				case BeaconConnection.ERROR_OVERWRITE_REQUEST:
					Helpers.displayToast(handler, mActivity, "Overwrite request error", Toast.LENGTH_SHORT);
					break;

				case BeaconConnection.ERROR_SERVICES_DISCOVERY:
					Helpers.displayToast(handler, mActivity, "Services discovery error", Toast.LENGTH_SHORT);
					break;

				case BeaconConnection.ERROR_AUTHENTICATION:
					Helpers.displayToast(handler, mActivity, "Authentication error", Toast.LENGTH_SHORT);
					break;

				case BeaconConnection.ERROR_CHARACTERISTIC_READING:
					Helpers.displayToast(handler, mActivity, "Characteristic reading error", Toast.LENGTH_SHORT);
					break;

				default:
					throw new IllegalStateException("Unexpected connection error occured: " + errorCode);
				}
			}

			@Override
			public void onDisconnected() {
				Helpers.displayToast(handler, mActivity, "Disconnected", Toast.LENGTH_SHORT);
			}
        };
    }
	
	

}
