package com.Helios;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import com.kontakt.sdk.android.configuration.ForceScanConfiguration;
import com.kontakt.sdk.android.configuration.MonitorPeriod;
import com.kontakt.sdk.android.connection.OnServiceBoundListener;
import com.kontakt.sdk.android.factory.Filters;
import com.kontakt.sdk.android.manager.BeaconManager;

class KontaktBeaconManagerBridge {
	private BeaconManager beaconManager;
	private Context con;
	private final String TAG = "Helios_" + getClass().getSimpleName();

	KontaktBeaconManagerBridge(Context con, BeaconManager.RangingListener rangeListener) {
		this.con = con;

		// starts up Kontakt.io beacon manager
		beaconManager = BeaconManager.newInstance(con);
		beaconManager.setMonitorPeriod(MonitorPeriod.MINIMAL);
		beaconManager.setForceScanConfiguration(ForceScanConfiguration.DEFAULT);
		beaconManager.addFilter(Filters.newProximityUUIDFilter(java.util.UUID.fromString(Config.PROXIMITY_UUID)));
		// beaconManager.addFilter(Filters.newMajorFilter(29358));

		beaconManager.registerRangingListener(rangeListener);		
	}

	boolean disconnectBeaconManager() {
		if (beaconManager != null) {
			beaconManager.stopRanging();
			beaconManager.disconnect();
			return true;
		}

		return false;
	}

	void connectBeaconManager() throws RemoteException {

		beaconManager.connect(new OnServiceBoundListener() {
			@Override
			public void onServiceBound() {
				try {
					beaconManager.startRanging();
				} catch (RemoteException e) {
					showRemoteException(con, KontaktBeaconManagerBridge.this.TAG, e);
				}
			}
		});
	}

	static void showRemoteException(Context con, String localTag, RemoteException e) {
		Log.e(localTag, "Unrecoverable error when connecting beacon manager " + e.getMessage());
		Helpers.showAlert(con, "Beacon error", "Unrecoverable error when connecting beacon manager");
	}

}
