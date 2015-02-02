package com.Helios;

import java.util.List;
import java.util.Map;

import com.kontakt.sdk.android.device.Beacon;
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
	
	public void onBeaconsDiscovered(final Region region, final List<Beacon> beacons) {

		for (Beacon beacon : beacons) {
			BeaconInfo beaconInfo = new BeaconInfo(beacon, null);
			beaconID = beaconInfo.getBeaconUniqueKey();

			if (beaconInfo.isStaticBeacon()){
			// let the update receiver know that a static beacon was discovered
				updateReceiver.processStaticBeacon(beaconInfo);
				continue;
			}
			// if we picked up some random beacon that does not belong to this
			// user, ignore it. This also ignores static beacons so that they do not get uploaded
			if (!monitoredBeacons.containsKey(beaconID))
				continue;

			updateReceiver.processMonitoredBeacon(beaconInfo);
		}
	}
}
