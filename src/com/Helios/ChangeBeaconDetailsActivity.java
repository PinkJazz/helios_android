package com.Helios;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.kontakt.sdk.android.configuration.ForceScanConfiguration;
import com.kontakt.sdk.android.configuration.MonitorPeriod;
import com.kontakt.sdk.android.connection.BeaconConnection;
import com.kontakt.sdk.android.connection.OnServiceBoundListener;
import com.kontakt.sdk.android.data.RssiCalculators;
import com.kontakt.sdk.android.device.BeaconDevice;
import com.kontakt.sdk.android.device.Region;
import com.kontakt.sdk.android.factory.AdvertisingPackage;
import com.kontakt.sdk.android.factory.Filters;
import com.kontakt.sdk.android.manager.BeaconManager;


public class ChangeBeaconDetailsActivity extends Activity implements BeaconManager.RangingListener{
	
	BeaconManager beaconManager;
	private final String TAG = "Helios_" + getClass().getSimpleName();
	
	// used to access UI thread for toasts
	private static Handler handler = new Handler(Looper.getMainLooper());

    Map<String, Boolean> beaconMap = new HashMap<String, Boolean>();
    BeaconConnection beaconConnection;
    
    String UUID, password, friendlyName;
    int major, minor;
    
    private String mEmail;
    private String mToken;
    private boolean updated = false;
    BeaconInfo newBeaconInfo;
    int beaconID;   // only sent if this is a modify beacon request. otherwise default value is -1
    private int requestType;
    
    static String REQUEST_TYPE_MSG = "Request Type";
    static String MODIFIED_BEACON_NAME = "Modified Beacon Name"; //used when sending result back via Intent
    static int ADD_BEACON_REQUEST = 1;
    static int MODIFY_BEACON_REQUEST = 2;
    
    private boolean IS_RANGING = false; // monitors whether Kontakt.io ranging is currently active 
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.change_beacon_main);
        
        Intent intent = getIntent();
        mEmail = intent.getStringExtra(LoginActivity.EMAIL_MSG);
        mToken = intent.getStringExtra(LoginActivity.TOKEN_MSG);
        requestType = intent.getIntExtra(ChangeBeaconDetailsActivity.REQUEST_TYPE_MSG, ADD_BEACON_REQUEST);
        
        beaconID = intent.getIntExtra("beaconID", -1);
        
        UUID = intent.getStringExtra("current_UUID").trim();
        friendlyName = intent.getStringExtra("current_friendlyName").trim();
        password = intent.getStringExtra("current_password").trim();
        major = intent.getIntExtra("current_major", 0);
        minor = intent.getIntExtra("current_minor", 0);
        
        String new_UUID = intent.getStringExtra("new_UUID").trim();
        String new_friendlyName = intent.getStringExtra("new_friendlyName").trim();
        String new_password = intent.getStringExtra("new_password").trim();
        int new_major = intent.getIntExtra("new_major", 0);
        int new_minor = intent.getIntExtra("new_minor", 0);
        
        newBeaconInfo = new BeaconInfo(new_UUID, new_major, new_minor, new_friendlyName);
        newBeaconInfo.password = new_password;
        newBeaconInfo.powerLevel = intent.getIntExtra("new_powerLevel", 3);
        
        Log.i(TAG, "Change Activity received " + UUID + " " + friendlyName + " " + major + " " + minor);
        Log.i(TAG, "Change Activity changing to " + new_UUID + " " + new_friendlyName + " " + new_major + " " + new_minor);        
        
        if((beaconManager != null) && beaconManager.isConnected()){
        	beaconManager.disconnect();
        	beaconManager = null;
        }
        
        final String objectBeaconProxID = UUID;

        beaconManager = BeaconManager.newInstance(this);
        beaconManager.setRssiCalculator(RssiCalculators.newLimitedMeanRssiCalculator(1));
        beaconManager.setMonitorPeriod(MonitorPeriod.MINIMAL);
        beaconManager.setForceScanConfiguration(ForceScanConfiguration.DEFAULT);
        beaconManager.addFilter(new Filters.CustomFilter() { //create your customized filter
        	@Override
        	public Boolean apply(AdvertisingPackage advertisingPackage) {
        		String beaconID = advertisingPackage.getProximityUUID().toString();         		
        		String text = "beacon " + advertisingPackage.getProximityUUID() + " name " + advertisingPackage.getBeaconUniqueId()
       				 + " major = " + advertisingPackage.getMajor() + " minor = " + advertisingPackage.getMinor();
        		Log.v(TAG, "Saw " + text);

        		if (beaconID.equals(objectBeaconProxID)){
        			return true;
        		}
        		return false;
        	}
        	});

        beaconManager.registerRangingListener(this);
        connect();
        
    }

    protected void onStart() {
        super.onStart();
    }

    protected void onResume() {
        super.onResume();
        // TODO: need to make it robust if the app is paused and restarted
        // just putting startRanging here creates threading issues so does not work
/*        if(beaconManager.isConnected() && !IS_RANGING){
        	startRanging();
        	IS_RANGING = true;
        }*/
       }

    @Override
    protected void onStop() {
        super.onStop();
        beaconManager.stopRanging();
        IS_RANGING = false;
    }

    @Override
    protected void onDestroy() {
        if(beaconManager != null)
        	beaconManager.disconnect();
        beaconManager = null;
        Log.v(TAG, "ChangeBeaconDetailsActivity destroyed");
        super.onDestroy();

    }

    public void onBackPressed(){
    	// returns an intent if user exits by backpressing
    	Intent intent = new Intent();
    	intent.putExtra(MODIFIED_BEACON_NAME, friendlyName);
    	
    	setResult(RESULT_CANCELED, intent);
    	super.onBackPressed();    	
    }
    
    private void returnResult(int resultType){
    	// returns resultType along with beacon name
    	Intent intent = new Intent();
    	intent.putExtra(MODIFIED_BEACON_NAME, friendlyName);
    	
    	setResult(resultType, intent);
    	finish();
    }
    
    private void connect() {
        try {
            beaconManager.connect(new OnServiceBoundListener() {
                @Override
                public void onServiceBound() {
                	startRanging();
                	IS_RANGING = true;
                }
            });
        } catch (RemoteException e) {
            throw new IllegalStateException(e);
        }
    }
    
    private void startRanging(){
        try {
            beaconManager.startRanging();
            Log.v(TAG, "Started ranging");
        } catch (RemoteException e) {
        	Log.e(TAG, "Remote exception " + e.getMessage());
        	Helpers.showAlert(ChangeBeaconDetailsActivity.this, "Unrecoverable beacon error", "Unrecoverable beacon error");;
            returnResult(RESULT_CANCELED);
        } catch (Exception e) {
        	Log.e(TAG, "Exception " + e.getMessage());
        	Helpers.showAlert(ChangeBeaconDetailsActivity.this, "Unrecoverable beacon error", "Unrecoverable beacon error");;
            returnResult(RESULT_CANCELED);
        }
        
    }
    
    public void onBeaconModifiedSuccess(BeaconInfo modifiedBeaconInfo){
    	// Callback method from BeaconModifier when beacon has been changed successfully
    	Log.i(TAG, "Beacon " + modifiedBeaconInfo.friendlyName + " successfully modified");
    	Log.i(TAG, "New beacon info is " + modifiedBeaconInfo.proximityUUID + " power level = " + 
    					modifiedBeaconInfo.powerLevel); 
    	new ServletUploaderAsyncTask(this, constructPayload(modifiedBeaconInfo), 
    			Config.ADD_MODIFY_BEACON_POST_TARGET).execute();
    	returnResult(RESULT_OK);
    }
    
	private String constructPayload(BeaconInfo modifiedBeaconInfo) {
		// Add your data to be POSTed
		try {
			JSONObject payloadJSONObj = new JSONObject();

			payloadJSONObj.put("Email", mEmail);
			payloadJSONObj.put("Token", mToken);
			if(requestType == ADD_BEACON_REQUEST)
				payloadJSONObj.put("query", "new_beacon");
			else
				payloadJSONObj.put("query", "modified_beacon");
			
			// put in details of observed beacon
			JSONObject beaconObj = new JSONObject();
			beaconObj.put("New_Unique_id", modifiedBeaconInfo.proximityUUID);
			beaconObj.put("New_Major_id", Integer.toString(modifiedBeaconInfo.major));
			beaconObj.put("New_Minor_id", Integer.toString(modifiedBeaconInfo.minor));
			beaconObj.put("New_powerLevel", Integer.toString(modifiedBeaconInfo.powerLevel));
			beaconObj.put("New_password", modifiedBeaconInfo.password);
			beaconObj.put("New_Friendly_name", modifiedBeaconInfo.friendlyName);
			beaconObj.put("BeaconId", beaconID);
			
			payloadJSONObj.put("Beacon", beaconObj);
			return payloadJSONObj.toString();

		} catch (JSONException jse) {
			Log.w(TAG, "JSON Exception " + jse.getMessage());
			return null;
		}
	}
    
    // Methods implemented for BeaconManager.RangingListener
    public void onBeaconsDiscovered(final Region region, final List<BeaconDevice> beacons) {
    	for (final BeaconDevice beacon: beacons){
        	String ID = beacon.getBeaconUniqueId();        	
    		String text = "beacon " + beacon.getProximityUUID() + " name " + beacon.getBeaconUniqueId()
    				 + " major = " + beacon.getMajor() + " minor = " + beacon.getMinor();
    		Log.v(TAG, "Discovered " + text);
    		
        	if(!beaconMap.containsKey(ID)){
        		beaconMap.put(ID, true);
	    		if(isTargetBeacon(beacon) && !updated){
	    			updated = true;	    			
	    			Helpers.displayToast(handler, ChangeBeaconDetailsActivity.this, "Recognized " + text, Toast.LENGTH_SHORT);
	        		beacon.setPassword(password.getBytes());
		    		Log.i(TAG, "Recognized " + text);

		    		BeaconInfo beaconInfo = new BeaconInfo(beacon, null);
		    		beaconInfo.friendlyName = friendlyName;
		    		beaconInfo.powerLevel = 3;
		    		newBeaconInfo.beacon = beaconInfo.beacon;
		    		
	    	    	new BeaconModifier(this, beaconInfo, newBeaconInfo).run();	    	       	    	    	        	    	    	       
	    		}
	        }
    	}
    }

    	
    private boolean isTargetBeacon(BeaconDevice beacon){
    	
    	if(major == beacon.getMajor() && minor == beacon.getMinor())
    		return true;
    	
    	return false;
    }
 }
