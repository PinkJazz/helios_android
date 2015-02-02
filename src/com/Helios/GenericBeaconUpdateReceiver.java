package com.Helios;


public interface GenericBeaconUpdateReceiver {
	public void processStaticBeacon(BeaconInfo beaconInfo);
	public void processMonitoredBeacon(BeaconInfo beaconInfo);
}
