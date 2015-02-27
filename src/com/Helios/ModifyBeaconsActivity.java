package com.Helios;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.AccountManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class ModifyBeaconsActivity extends Activity{

	private final String TAG = "Helios_" + getClass().getSimpleName();
	
	// used to access UI thread for toasts
	private Handler handler = new Handler(Looper.getMainLooper());
	private Context con;

	private String mEmail;
	private String mToken;

	private CognitoHelper cognitoHelperObj;

	private List<TextView> textViews = new ArrayList<TextView>();
	private TextView headerTextview;
	private Button activateButton;
	
	private Map<String, BeaconInfo> monitoredBeacons = new HashMap<String, BeaconInfo>();
	private Map<String, BeaconInfo> newBeaconDetails = new HashMap<String, BeaconInfo>();
	private Queue<String> newBeaconList = new ArrayDeque<String>();
	
	private Map<String, TextView> beaconDisplay = new HashMap<String, TextView>();

	// define BroadCastReceiver to shut the service down if Bluetooth
	// gets switched off while the service is running
    private final IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        final String action = intent.getAction();

	        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
	            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
	                                                 BluetoothAdapter.ERROR);
	            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF)	{
	            	Helpers.displayToast(handler, context, "Shutting down Helios monitor", Toast.LENGTH_SHORT);
	            	Log.i(TAG, "Killing activity since Bluetooth was turned off");
	            	ModifyBeaconsActivity.this.finish();
	            }
	        }
	    }
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.new_beacon_form);
		activateButton = (Button) findViewById(R.id.add_beacon_button);
		con = this; // used to access UI thread since RangingListener does not
					// run in UI thread

		// assumes Bluetooth has been enabled before starting this activity
		// Getting permission and activating Bluetooth should occur before
		// starting this
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
			Helpers.showAlert(this, "Bluetooth Adapter Error", "Bluetooth adapter is not working or is disabled");
			finish();
		}

		Intent intent = getIntent();
		mEmail = intent.getStringExtra(LoginActivity.EMAIL_MSG);
		mToken = intent.getStringExtra(LoginActivity.TOKEN_MSG);

		cognitoHelperObj = new CognitoHelper(this, mToken);
		cognitoHelperObj.doCognitoLogin();
		if (!Config.WiFiUploadOnly || Helpers.isWifiConnected(this))
			cognitoHelperObj.sendCognitoMessage(mEmail);
		populateTextViewsList();

		// download list of beacons belonging to this user
		headerTextview.setText("Downloading beacons to add. Please wait...");
		registerReceiver(mReceiver, filter);
		new Thread(new BeaconListDownloader(this, mEmail, mToken)).start();
	}

	private void populateTextViewsList() {
		// add displays to show beacon location
		textViews.add((TextView) findViewById(R.id.tv1));
		textViews.add((TextView) findViewById(R.id.tv2));
		textViews.add((TextView) findViewById(R.id.tv3));
		textViews.add((TextView) findViewById(R.id.tv4));
		textViews.add((TextView) findViewById(R.id.tv5));
		headerTextview = (TextView) findViewById(R.id.header);

	}

	protected void onStart() {
		super.onStart();
	}

	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "Destroyed");

		unregisterReceiver(mReceiver);
	}

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.i(TAG, "onActivityResult called " + requestCode + " " + resultCode);
        String modifiedBeaconName = "";
        if (requestCode == Helpers.CHANGE_BEACON_DETAILS) {
        	// complications to handle the case where ChangeBeaconDetailsActivity didn't pick up 
        	//  the beacon and terminated without returning the data below
        	Bundle extras = data.getExtras();
        	if (extras != null) {
        	    if (extras.containsKey(ChangeBeaconDetailsActivity.MODIFIED_BEACON_NAME)) {
        	        modifiedBeaconName = data.getStringExtra(ChangeBeaconDetailsActivity.MODIFIED_BEACON_NAME);
        	    }
        	}

        	if (resultCode == RESULT_OK && !modifiedBeaconName.equals("")) {
            	// changed beacon details so let's modify the next one
                Log.v(TAG, modifiedBeaconName + " successfully modified");
            } else if (resultCode == RESULT_CANCELED || modifiedBeaconName.equals("")) {
            	Log.v(TAG, modifiedBeaconName + " NOT successfully modified");
            }
            // modify the next beacon
       //     modifyNextBeacon();
        	finish();
            return;
        } 
    }

	private void initializeBeaconDisplay() {
		runOnUiThread(new Runnable() {
			public void run(){
				headerTextview.setText("Updating " + monitoredBeacons.size() + " beacons");
				activateButton.setEnabled(true);
			}
		});
		int r = 0;
		for (String beaconID : monitoredBeacons.keySet()) {
			if (r < textViews.size()) {
				beaconDisplay.put(beaconID, textViews.get(r));
				Helpers.updateTextView(handler, textViews.get(r), 
						monitoredBeacons.get(beaconID).friendlyName);
			}
			r++;
		}
	}

    /** Called by button in the layout */
    public void addBeacons(View view) {
    	newBeaconList.addAll(monitoredBeacons.keySet());
    	modifyNextBeacon();
    }

	private void modifyNextBeacon() {
		// pops next beacon from the queue and starts activity to modify it
		if (!newBeaconList.isEmpty()){
    		String beaconID = newBeaconList.remove();
			BeaconInfo oldBeaconInfo = monitoredBeacons.get(beaconID);
			BeaconInfo newBeaconInfo = newBeaconDetails.get(beaconID);
	    	Intent intent = constructModifyBeaconIntent(oldBeaconInfo, newBeaconInfo);
	    	
//	    	intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
	    	startActivityForResult(intent, Helpers.CHANGE_BEACON_DETAILS);
    	}
	}

	private Intent constructModifyBeaconIntent(BeaconInfo oldBeaconInfo, BeaconInfo newBeaconInfo) {
		// construct Intent to modify beacon from old to new details
		Intent intent = new Intent(this, ChangeBeaconDetailsActivity.class);
		intent.putExtra(LoginActivity.EMAIL_MSG, mEmail);
		intent.putExtra(LoginActivity.TOKEN_MSG, mToken);
		intent.putExtra(ChangeBeaconDetailsActivity.REQUEST_TYPE_MSG, ChangeBeaconDetailsActivity.MODIFY_BEACON_REQUEST);
		
		intent.putExtra("beaconID", oldBeaconInfo.beaconID);
		intent.putExtra("current_UUID", oldBeaconInfo.proximityUUID);
		intent.putExtra("current_friendlyName", oldBeaconInfo.friendlyName);
		intent.putExtra("current_password", oldBeaconInfo.password);
		intent.putExtra("current_major", oldBeaconInfo.major);
		intent.putExtra("current_minor", oldBeaconInfo.minor);
		intent.putExtra("current_powerLevel", oldBeaconInfo.powerLevel);
		
		intent.putExtra("new_UUID", newBeaconInfo.proximityUUID);
		intent.putExtra("new_friendlyName", newBeaconInfo.friendlyName);
		intent.putExtra("new_password", newBeaconInfo.password);
		intent.putExtra("new_major", newBeaconInfo.major);
		intent.putExtra("new_minor", newBeaconInfo.minor);
		intent.putExtra("new_powerLevel", newBeaconInfo.powerLevel);
		return intent;
	}

	private class BeaconListDownloader implements Runnable {
		// must be called after logging in via Cognito

		private final String TAG_DOWNLOAD = "Helios_" + getClass().getSimpleName();
		private final String mEmail;
		private final String mToken;
		private final Activity parent;
		
		BeaconListDownloader(Activity parentActivity, String email, String token) {
			mEmail = email;
			mToken = token;
			parent = parentActivity;
		}

		public void run() {
			String KEY_PREFIX = "";
			try {
				KEY_PREFIX = cognitoHelperObj.getIdentityID();
			} catch (Exception e) {
				Helpers.displayToast(handler, parent, "Unrecoverable error when logging into Amazon cognito",
						Toast.LENGTH_LONG);
				Log.d(TAG_DOWNLOAD, "Cognito Login error - " + e.getMessage());
				parent.finish();
			}
			try{
				downloadListFromRDS();
			} catch(JSONException jse){
				Log.d(TAG_DOWNLOAD, "Error parsing list of beacons to monitor - " + jse.getMessage());
				parent.finish();				
			}
		}
		
		private void downloadListFromRDS() throws JSONException{
			String uniqueID, friendlyName, password;
			int major, minor, powerLevel, beaconID;
			
			JSONObject serverResponse = getBeaconList();
			
			JSONArray beaconArray = serverResponse.getJSONArray("Beacons");
			for(int i = 0; i < beaconArray.length(); i++){
				JSONObject beacon = beaconArray.getJSONObject(i);
				
				uniqueID = beacon.getString("Unique_id");
				friendlyName = beacon.getString("Friendly_name");;
				major = beacon.getInt("Major_id");
				minor = beacon.getInt("Minor_id");
				powerLevel = beacon.getInt("powerLevel");
				password = beacon.getString("password");
				beaconID = beacon.getInt("BeaconId");
				
				BeaconInfo oldBeac = new BeaconInfo(uniqueID, major, minor, friendlyName, password, powerLevel, beaconID);
				monitoredBeacons.put(oldBeac.getBeaconUniqueKey(), oldBeac);

				// now get information for the new values that this beacon should be changed to
				uniqueID = beacon.getString("New_Unique_id");
				friendlyName = beacon.getString("New_Friendly_name");;
				major = beacon.getInt("New_Major_id");
				minor = beacon.getInt("New_Minor_id");
				powerLevel = beacon.getInt("New_powerLevel");
				password = beacon.getString("New_password");

				BeaconInfo beac = new BeaconInfo(uniqueID, major, minor, friendlyName, password, powerLevel, beaconID);
				// put it in new beacon details map with key from *old* beacon details
				newBeaconDetails.put(oldBeac.getBeaconUniqueKey(), beac);

			}			
			initializeBeaconDisplay();
		}
		
		private JSONObject getBeaconList(){
			URL url;
			HttpURLConnection conn;
			try {
				url = new URL(Config.BEACON_LIST_DOWNLOAD_POST_TARGET);
				conn = (HttpURLConnection) url.openConnection();
			} catch (MalformedURLException mue) {
				Log.w(TAG_DOWNLOAD, "Malformed URL Exception " + mue.getMessage());
				return null;
			} catch (IOException e) {
				Log.w(TAG_DOWNLOAD, "Network Exception when POSTing beacon data " + e.getMessage());
				return null;
			}
			// connection was opened successfully if we got here

			try {			
				JSONObject requestObj = getRequestObj();
				String payloadObj = requestObj.toString();

				conn.setDoOutput(true);
				conn.setRequestMethod("POST");  
				conn.setFixedLengthStreamingMode(payloadObj.getBytes().length);
				conn.setRequestProperty("Content-Type","application/json");   

				OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream());
				Log.v(TAG_DOWNLOAD, "Sending request now");
				osw.write(payloadObj.toString());
				osw.flush();
				osw.close();
				Log.v(TAG_DOWNLOAD, "Response status code is " + conn.getResponseCode());
		    	StringBuffer jb = new StringBuffer();
		    	String line = null;
		    	try {
		    	    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    	    while ((line = reader.readLine()) != null)
		    	      jb.append(line);
					reader.close();
		    	} catch (Exception e) { 
		      	    Log.w(TAG_DOWNLOAD, "Error reading request string" + e.toString());
		    	}
		    	JSONObject response = new JSONObject(jb.toString());	    	
				Log.v(TAG_DOWNLOAD, "Response string from DBServlet is " + response.toString());
				return response;

			} catch (ClientProtocolException e) {
				Log.w(TAG_DOWNLOAD, "Protocol Exception when downloading beacon data " + e.getMessage());
				return null;
			} catch (IOException e) {
				Log.w(TAG_DOWNLOAD, "Network Exception when downloading beacon data " + e.getMessage());
				return null;
			} catch (Exception e) {
				Log.w(TAG_DOWNLOAD, "Exception when downloading beacon data " + e.getMessage());
				return null;
			} finally {
				conn.disconnect();
			}	
		}

		private JSONObject getRequestObj() throws JSONException{
			// create request object to ask servlet to send list of beacons
			JSONObject requestObj = new JSONObject();
			
			requestObj.put("Email", mEmail);
			requestObj.put("Token", mToken);
			requestObj.put("query", "get_modified_beacon");
			
			return requestObj;
		}
	}

}


