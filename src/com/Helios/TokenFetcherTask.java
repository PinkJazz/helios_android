package com.Helios;

import java.io.IOException;

import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;

class TokenFetcherTask extends AsyncTask<Void, Void, String>{

    private final String TAG = "Helios_" + getClass().getSimpleName(); 
	protected LoginActivity mActivity;
	
	protected String mScope;
	protected String mEmail;
	protected String token;
	
	public static final String REQUEST_TYPE_PLAY = "RESTART_RECORDING";
	public static final String REQUEST_TYPE_PAUSE = "PAUSE_RECORDING";
	public static final String REQUEST_TYPE_START = "START_RECORDING";
	public static final String REQUEST_TYPE_STOP = "STOP_RECORDING";
	public static final String REQUEST_TYPE = "REQUEST_TYPE";
	
	TokenFetcherTask(LoginActivity activity, String email, String scope) {
		this.mActivity = activity;
		this.mScope = scope;
		this.mEmail = email;
	}
	
	protected String doInBackground(Void... params) {		
		try {
			token = fetchToken();
		}		
		catch (IOException ex) {
			onError("IO Exception occured, please try again. "
					+ ex.getMessage(), ex);
		}
		return token;
	}
	
	protected void onPostExecute(String token){
		// takes token and starts new BarcodeActivity

	/*	checks for null because the way GoogleAuthUtil.getToken works is as follows
		on the first call, it throws a UserRecoverableAuthException which is when the authorization
		window pops up. The AsyncTask TokenFetcherTask terminates at this point and returns null.
		This then filters back through the LoginActivity and, if the user authorizes it, 
		gets to method handleAuthorizeResult which starts another TokenFetcherTask and only on this second attempt
		does the task successfully return a token and start the BarcodeActivity. 
	*/
		if(token != null){
			mActivity.setToken(token);			
			if(mActivity.getActivityType() == Helpers.ActivityType.FOREGROUND_BLUETOOTH_MONITOR){ 
				// monitor for Bluetooth beacons
				Intent intent = new Intent(mActivity, BluetoothMonitorActivity.class);
				intent.putExtra(LoginActivity.TOKEN_MSG, token);
				intent.putExtra(LoginActivity.EMAIL_MSG, mEmail);
				mActivity.startActivity(intent);
				Log.i(TAG, "Monitoring for bluetooth beacons");
				return;
			}
			
			if(mActivity.getActivityType() == Helpers.ActivityType.ADD_NEW_BEACONS){ 
				// add new Bluetooth beacons
				Intent intent = new Intent(mActivity, AddNewBeaconsActivity.class);
				intent.putExtra(LoginActivity.TOKEN_MSG, token);
				intent.putExtra(LoginActivity.EMAIL_MSG, mEmail);
				mActivity.startActivity(intent);
				Log.i(TAG, "Adding new beacons");
				return;
			}
			
			if(mActivity.getActivityType() == Helpers.ActivityType.MODIFY_BEACONS){ 
				// modify an existing Bluetooth beacons
				Intent intent = new Intent(mActivity, ModifyBeaconsActivity.class);
				intent.putExtra(LoginActivity.TOKEN_MSG, token);
				intent.putExtra(LoginActivity.EMAIL_MSG, mEmail);
				mActivity.startActivity(intent);
				Log.i(TAG, "Modifying beacons");
				return;
			}

			if(mActivity.getActivityType() == Helpers.ActivityType.BACKGROUND_BLUETOOTH_MONITOR){ 
				// monitor for Bluetooth beacons
				Intent intent = new Intent(mActivity, BluetoothMonitorService.class);
				intent.putExtra(LoginActivity.TOKEN_MSG, token);
				intent.putExtra(LoginActivity.EMAIL_MSG, mEmail);
				intent.putExtra(TokenFetcherTask.REQUEST_TYPE, TokenFetcherTask.REQUEST_TYPE_START);
				mActivity.startService(intent);
				Log.i(TAG, "Started new BluetoothMonitorService with token for " + mEmail);
				mActivity.finish();
				return;
			}

			if(mActivity.getActivityType() == Helpers.ActivityType.RECORD_VIDEO){ 
				 // start service to upload video to server where the decoding will occur
				Intent intent = new Intent(mActivity, BackgroundVideoRecorder.class);
				intent.putExtra(LoginActivity.TOKEN_MSG, token);
				intent.putExtra(LoginActivity.EMAIL_MSG, mEmail);
				intent.putExtra(TokenFetcherTask.REQUEST_TYPE, TokenFetcherTask.REQUEST_TYPE_START);
				mActivity.startService(intent);
				Log.i(TAG, "Started new BackgroundVideoRecorder with token for " + mEmail);
				mActivity.finish();
				return;
			}
		}
	}
	
	/**
	 * Get a authentication token if one is not available. If the error is not
	 * recoverable then it displays the error message on parent activity right
	 * away.
	 */
	protected String fetchToken() throws IOException {
		/** the method fetches an access token from Google Play services.
		 * 
		 * @throws IOException
		 *             if communication with user info server failed.
		 */
		try {
			Log.i(TAG, "Trying to get token for scope " + mScope + " for user " + mEmail);
			String token = GoogleAuthUtil.getToken(mActivity, mEmail, mScope); 
			Log.i(TAG, "Got token for scope " + mScope + " for user " + mEmail);		
			mActivity.resetEmail();
			return token;
			
		} catch (UserRecoverableAuthException userRecoverableException) {
			// GooglePlayServices.apk is either old, disabled, or not present,
			// which is recoverable, so we need to show the user some UI through the
			// activity. Also gets thrown the first time getToken is called 
			// in order to get the user to authorize it
			Log.i(TAG, "UserRecoverableAuthException thrown for " + mEmail + " and " + mScope);
			mActivity.handleException(userRecoverableException);
		} catch (GoogleAuthException fatalException) {
			onError("Unrecoverable error " + fatalException.getMessage(),
					fatalException);
		}
		return null;
	}

	protected void onError(String msg, Exception e) {
		if (e != null) {
			Log.e(TAG, "Exception: ", e);
		}
		mActivity.show(msg); // will be run in UI thread
	}

}
