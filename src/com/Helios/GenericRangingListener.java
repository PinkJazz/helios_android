package com.Helios;

import java.util.List;
import java.util.Map;

import android.util.Log;

import com.kontakt.sdk.android.device.BeaconDevice;
import com.kontakt.sdk.android.device.Region;
import com.kontakt.sdk.android.manager.BeaconManager;


class GenericRangingListener implements BeaconManager.RangingListener{
	private final String TAG = "Helios_" + getClass().getSimpleName();

	private GenericBeaconUpdateReceiver updateReceiver;
	private Map<String, BeaconInfo> monitoredBeacons;

	private String beaconID;
	
	public GenericRangingListener(GenericBeaconUpdateReceiver updateReceiver,  
			Map<String, BeaconInfo> monitoredBeacons) {
		this.updateReceiver = updateReceiver;
		this.monitoredBeacons = monitoredBeacons;
	}
	
	public void onBeaconsDiscovered(final Region region, final List<BeaconDevice> beacons) {

		for (BeaconDevice beacon : beacons) {
			BeaconInfo beaconInfo = new BeaconInfo(beacon, null);
			beaconID = beaconInfo.getBeaconUniqueKey();
			
			if(System.currentTimeMillis() - beacon.getTimestamp() > 1000){
				// system appears to queue up calls and calls this function up to 5-10 seconds
				// after the actual observation - we ignore these observations since they are stale
				Log.w(TAG, "Called for " + beaconID + " more than a second after observation " + Long.toString(System.currentTimeMillis() - beacon.getTimestamp()));
				continue;
			}
			
			Log.v(TAG, "Ranging picked up " + beaconID);
			if (beaconInfo.isStaticBeacon()){
			// let the update receiver know that a static beacon was discovered
				updateReceiver.processStaticBeacon(beaconInfo);
				continue;
			}
			// if we picked up some beacon that does not belong to this user, ignore it. 
			if (!monitoredBeacons.containsKey(beaconID))
				continue;

			beaconInfo.friendlyName = monitoredBeacons.get(beaconID).friendlyName;
			updateReceiver.processMonitoredBeacon(beaconInfo);
		}
	}
}
