package com.Helios;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

final class Config {
	static final String ACCOUNT_ID = "886472035129";
	private static final String SERVICE_ACCOUNT_ID = "754068036571-v8j0kf4tivmh252qn3e45uk2fbcu4ktv.apps.googleusercontent.com";
	private static final String WEB_APPLICATION_ID = "754068036571-026alu09j3akpda9odq6bcmj9jmm7fns.apps.googleusercontent.com";
	
	static final String IDENTITY_POOL = "us-east-1:a41f8041-3c79-4b62-974a-362cffac8301";
	
	static final Regions COGNITO_REGION = Regions.US_EAST_1;
	
	static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/" + ACCOUNT_ID + "/helios";
	static final Region SQS_QUEUE_REGION = Region.getRegion(Regions.US_EAST_1);
			
	// scope string for web component to authenticate with Google via Cognito
	static final String SCOPE = "audience:server:client_id:" + WEB_APPLICATION_ID;
	static final String S3_BUCKET_NAME = "helios-smart";
	
	static long UPLOAD_INTERVAL = 75000;
	static long MAX_VIDEO_FILE_SIZE = 50000000;
	
	// parameters to monitor Bluetooth beacons
	static final String OLD_PROXIMITY_UUID = "f7826da6-4fa2-4e98-8024-bc5b71e0893e"; 
	static final String OLD_STATIC_PROXIMITY_UUID = "f7826da6-4fa2-4e98-8024-bc5b71e0893f"; 

	public static final String STATIC_PROXIMITY_UUID = "08239eff-32e7-4721-a56f-ee463b75d4e8"; 
	public static final String DYNAMIC_PROXIMITY_UUID = "c1df3fd9-1e95-4248-981f-4668d4eb6539"; 

	static final String BEACON_FOLDER = "beacons";
	static final String BEACON_LIST = "/" + BEACON_FOLDER + "/beacons.txt";
	
	static final int STATIC_BEACON_OBSERVATION_LAG = 1000;
	
	static boolean WiFiUploadOnly = true;
	static boolean DEBUG_SQS_ENABLED = true; // should be true unless we are debugging
	
	// target address to HTTP POST beacon data
	// target address to HTTP POST beacon data
	static final String BEACON_UPLOAD_POST_TARGET = "http://50.112.176.173:8080/test";	
	static final String BEACON_LIST_DOWNLOAD_POST_TARGET = "http://50.112.176.173:8080/db";
	static final String NEW_BEACON_ADDED_POST_TARGET = "http://50.112.176.173:8080/db";
	
	static final String DEBUG_TEST_POST_TARGET = "http://160.39.241.244:8080/test";
	
	
	private Config(){} // no instantiation
}
