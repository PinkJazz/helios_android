package com.Helios;

import java.util.HashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

// Miscellaneous support functions used by various classes
class Helpers {

	private Helpers(){}	// not to be instantiated
	
	static enum ActivityType {RECORD_VIDEO, FOREGROUND_BLUETOOTH_MONITOR, BACKGROUND_BLUETOOTH_MONITOR
				, ADD_NEW_BEACONS}

	static boolean isWifiConnected(Context con){
		ConnectivityManager connectivity = (ConnectivityManager) con.getSystemService(Context.CONNECTIVITY_SERVICE);
        //If connectivity object is not null
        if (connectivity != null) {
            //Get network info - WIFI internet access
            NetworkInfo info = connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
 
            if (info != null) {
                //Look for whether device is currently connected to WIFI network
                if (info.isConnected()) {
                    return true;
                }
            }
        }
        return false;
	}
	
	static void displayToast(Handler handler, final Context con, final String text, final int toast_length){
		// helper method uses the main thread to display a toast
		// we use this because if this class is used by a Service
		// as opposed to an Activity, we can't access the UI thread 
		// in the normal way using RunOnUIThread
		handler.post(new Runnable(){
			public void run(){
				Toast.makeText(con, text, toast_length).show();
			}
		});
	}
	
    static void showAlert(final Context con, final String title, final String msg){
    	// shows alert dialog box if user tries to log in again while 
    	// BackgroundVideoRecorder is already running
    	AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(con);
 
			// set title
		alertDialogBuilder.setTitle(title);
		alertDialogBuilder.setMessage(msg).setCancelable(true)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {

		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		        // TODO Auto-generated method stub
		        // Do something
		        dialog.dismiss();
		        }
		    });
		
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();
    }
    
	static void updateTextView(Handler handler, final TextView tv, final String text) {
		// TODO: there is probably a cleaner way to replace calls to this method
		// with a call to runOnUiThread
		handler.post(new Runnable() {
			public void run() {
				tv.setText(text);
			}
		});
	}

	static Map<String, Boolean> getUUIDMap(){
	// creates Map of UUID's that we will monitor. Used to send to the beaconManager class
		Map<String, Boolean> m = new HashMap<String, Boolean>();
		
		m.put(Config.DYNAMIC_PROXIMITY_UUID, false);
		m.put(Config.STATIC_PROXIMITY_UUID, false);
		m.put(Config.OLD_PROXIMITY_UUID, false);
		m.put(Config.OLD_STATIC_PROXIMITY_UUID, false);
		
		return m;
	}

	static void createStopPauseNotification(String title, String stopText, String pauseText, 
			Service con, Class<?> serviceClass, String token, String mEmail, int NOTIFICATION_ID) {

		PendingIntent stopIntent = PendingIntent
				.getService(con, 0, getIntent(TokenFetcherTask.REQUEST_TYPE_STOP, con, serviceClass, token, mEmail),
						PendingIntent.FLAG_CANCEL_CURRENT);
		PendingIntent pauseIntent = PendingIntent.getService(con, 1,
				getIntent(TokenFetcherTask.REQUEST_TYPE_PAUSE, con, serviceClass, token, mEmail),
				PendingIntent.FLAG_CANCEL_CURRENT);

		// Start foreground service to avoid unexpected kill
		Notification notification = new Notification.Builder(con)
				.setContentTitle(title)
				.setContentText("").setSmallIcon(R.drawable.eye)
				.addAction(R.drawable.pause, pauseText, pauseIntent)
				.addAction(R.drawable.stop, stopText, stopIntent).build();
		con.startForeground(NOTIFICATION_ID, notification);
	}

	static void createStopPlayNotification(String title, String stopText, String playText, 
			Service con, Class<?> serviceClass, String token, String mEmail, int NOTIFICATION_ID) {

		PendingIntent stopIntent = PendingIntent
				.getService(con, 0, getIntent(TokenFetcherTask.REQUEST_TYPE_STOP, con, serviceClass, token, mEmail),
						PendingIntent.FLAG_CANCEL_CURRENT);
		PendingIntent playIntent = PendingIntent
				.getService(con, 2, getIntent(TokenFetcherTask.REQUEST_TYPE_PLAY, con, serviceClass, token, mEmail),
						PendingIntent.FLAG_CANCEL_CURRENT);

		// Start foreground service to avoid unexpected kill
		Notification notification = new Notification.Builder(con)
				.setContentTitle(title)
				.setContentText("").setSmallIcon(R.drawable.eye)
				.addAction(R.drawable.play, playText, playIntent)
				.addAction(R.drawable.stop, stopText, stopIntent).build();
		con.startForeground(NOTIFICATION_ID, notification);
	}

	static Intent getIntent(String requestType, Context con, Class<?> serviceClass, String token, String mEmail) {
		Intent intent = new Intent(con, serviceClass);
		intent.putExtra(TokenFetcherTask.REQUEST_TYPE, requestType);
		intent.putExtra(LoginActivity.TOKEN_MSG, token);
		intent.putExtra(LoginActivity.EMAIL_MSG, mEmail);

		return intent;

	}

}
