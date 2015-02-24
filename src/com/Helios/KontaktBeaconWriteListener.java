package com.Helios;

import android.util.Log;

import com.kontakt.sdk.android.connection.BeaconConnection;

public class KontaktBeaconWriteListener implements BeaconConnection.WriteListener{
	private final String TAG = "Helios_" + getClass().getSimpleName();
	private BeaconConnection beaconConn;
	private String characteristicName;
	private BeaconModifier mod;
	
	KontaktBeaconWriteListener(BeaconModifier modifier, BeaconConnection beaconConn, String characteristicName){
		this.beaconConn = beaconConn;
		this.characteristicName = characteristicName;		
		this.mod = modifier;
	}
	
	@Override
	public void onWriteSuccess() {
		Log.v("KontaktBeaconModifier", characteristicName + " written successfully");
		mod.overwriteNextParameter();
	}

	@Override
	public void onWriteFailure() {
		Log.w("KontaktBeaconModifier", characteristicName + " overwrite failure");
		// TODO: put in something here to try to roll back changes
		beaconConn.disconnect();
	}
}
