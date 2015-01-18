package com.Helios;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

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

}
