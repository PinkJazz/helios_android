package com.Helios;

import java.util.Map;

import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import com.kontakt.sdk.android.configuration.ForceScanConfiguration;
import com.kontakt.sdk.android.configuration.MonitorPeriod;
import com.kontakt.sdk.android.connection.OnServiceBoundListener;
import com.kontakt.sdk.android.data.RssiCalculators;
import com.kontakt.sdk.android.factory.AdvertisingPackage;
import com.kontakt.sdk.android.factory.Filters;
import com.kontakt.sdk.android.manager.BeaconManager;

class KontaktBeaconManagerBridge {
	private BeaconManager beaconManager;
	private Context con;
	private final String TAG = "Helios_" + getClass().getSimpleName();
	
	KontaktBeaconManagerBridge(Context con, GenericRangingListener rangeListener, final Map<String, Boolean> UUIDMap) {
		this.con = con;		

		// starts up Kontakt.io beacon manager
		beaconManager = BeaconManager.newInstance(con);
        beaconManager.setRssiCalculator(RssiCalculators.newLimitedMeanRssiCalculator(5));
		beaconManager.setMonitorPeriod(MonitorPeriod.MINIMAL);
		beaconManager.setForceScanConfiguration(ForceScanConfiguration.DEFAULT);
	//	beaconManager.addFilter(Filters.newProximityUUIDFilter(java.util.UUID.fromString(Config.OLD_PROXIMITY_UUID)));
		
        beaconManager.addFilter(new Filters.CustomFilter() { //create your customized filter
        	@Override
        	public Boolean apply(AdvertisingPackage advertisingPackage) {
        		String beaconID = advertisingPackage.getProximityUUID().toString();   
        		if(UUIDMap.containsKey(beaconID)){
            		Log.v(TAG, "Found beacon with ID " + beaconID + " " + advertisingPackage.getBeaconUniqueId());
            		return true;
        		}
        		return false;
        	}
        	}); 

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
				
					new Handler(con.getMainLooper()).post(
							new Runnable() {
								public void run(){
									try{
										beaconManager.startRanging();
										Log.v(TAG, "Started ranging");
									} catch (RemoteException e) {
										Log.e(TAG, "Remote exception " + e.getMessage());
										showRemoteException(con, KontaktBeaconManagerBridge.this.TAG, e);
									} catch (Exception e) {
										Log.e(TAG, "Exception after starting ranging" + e.getMessage());
									}
								}
							});
			}
		});
	}

	static void showRemoteException(Context con, String localTag, RemoteException e) {
		Log.e(localTag, "Unrecoverable error when connecting beacon manager " + e.getMessage());
		Helpers.showAlert(con, "Beacon error", "Unrecoverable error when connecting beacon manager");
	}

}
