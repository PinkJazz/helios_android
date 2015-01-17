package com.Helios;

import android.location.Location;

import com.kontakt.sdk.android.device.Beacon;

class BeaconInfo {
	String proximityUUID;
	int major, minor;
	long timestamp;
	private Location mLocation;
	Beacon beacon;
	
	BeaconInfo(Beacon beacon, Location mLoc){
		this.beacon = beacon;
		
		this.proximityUUID = beacon.getProximityUUID().toString();
		this.major = beacon.getMajor();
		this.minor = beacon.getMinor();				
		this.timestamp = beacon.getTimestamp();
		
		mLocation = mLoc;
	}
	
	BeaconInfo(String proximityUUID, int major, int minor){
		this.proximityUUID = proximityUUID;
		this.major = major;
		this.minor = minor;
	}

	String getBeaconUniqueKey(){
		return proximityUUID + "_" + major + "_" + minor;
	}
	
	public String toString(){
		StringBuffer sb = new StringBuffer();
		sb.append("Timestamp = " + timestamp + "\n");
		sb.append("Latitude = " + getLocation().getLatitude() + ", Longitude = " + getLocation().getLongitude() + "\n");
		sb.append("Proximity = " + getProximity() + "\n");
		return sb.toString();
	}
	
	// helper functions to see if two beacons have matching ID's etc
	
	boolean matchBeacon(BeaconInfo beacon){
		// return true if the beacon in the parameter has the same proximity UUID, major and minor
		// as _this_ beacon
		if(this.proximityUUID.contentEquals(beacon.proximityUUID) && this.major == beacon.major
				&& this.minor == beacon.minor)
			return true;
		
		return false;
	}
	
	boolean matchProximity(BeaconInfo beacon){
		// return true if the beacon in the parameter has the same proximity value as _this_ beacon
		if(this.getProximity().contentEquals(beacon.getProximity().toString()))
			return true;
		
		return false;
	}
	
	double calcDistance(BeaconInfo beacon2){
		// calculate scalar distance between two beacon Locations using spherical law of cosines
		// returns -1 if either beacon does not have a location value
		if(getLocation() == null || beacon2.getLocation() == null)
			return -1;
		
		double lat1, lat2, lon1, lon2, dist;
		
		lat1 = getLocation().getLatitude() * Math.PI/180.0;
		lat2 = beacon2.getLocation().getLatitude() * Math.PI/180.0;
		lon1 = getLocation().getLongitude() * Math.PI/180.0;
		lon2 = beacon2.getLocation().getLongitude() * Math.PI/180.0;
		
		dist = Math.acos(Math.sin(lat1) * Math.sin(lat2) + Math.cos(lat1)*Math.cos(lat2)*Math.cos(lon2 - lon1));
		return dist * 6371000; // Radius of earth = 6,371,000 metres
	}
	
	long calcTimeDiff(BeaconInfo beacon2){
		// beacon2 should be the more recently observed beacon in most cases when calling this function
		return (beacon2.timestamp - timestamp); 
	}
	// encapsulate some of the methods in Kontakt.io SDK to make
	// switching to different beacons easier
	
	double getRSSI(){
		// returns RSSI value from this beacon observation 
		return beacon.getRssi();
	}
	
	String getBeaconUniqueId(){
		return beacon.getBeaconUniqueId();
	}
	
	String getProximity(){
		return beacon.getProximity().toString();
	}
	
	Location getLocation() {
		return mLocation;
	}

	void setLocation(Location mLocation) {
		this.mLocation = mLocation;
	}

}
