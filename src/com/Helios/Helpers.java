package com.Helios;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.widget.Toast;

// Miscellaneous support functions used by various classes
class Helpers {
	
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
	


}
