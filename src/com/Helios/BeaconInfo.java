package com.Helios;

import android.location.Location;

import com.kontakt.sdk.android.device.Beacon;

class BeaconInfo {
	String proximityUUID;
	int major, minor;
	long timestamp;
	Location mLocation;
	
	BeaconInfo(Beacon beacon){
		this.proximityUUID = beacon.getProximityUUID().toString();
		this.major = beacon.getMajor();
		this.minor = beacon.getMinor();				
		this.timestamp = beacon.getTimestamp();
	}
	
	String getBeaconUniqueKey(){
		return proximityUUID + "_" + major + "_" + minor;
	}
	
	public String toString(){
		StringBuffer sb = new StringBuffer();
		sb.append("Timestamp = " + timestamp);
		return sb.toString();
	}
}
