package com.Helios;

import com.amazonaws.regions.Regions;

final class Config {
	static final String ACCOUNT_ID = "886472035129";
	static final String IDENTITY_POOL = "us-east-1:a41f8041-3c79-4b62-974a-362cffac8301";
	static final String AUTH_ROLE_ARN = "arn:aws:iam::886472035129:role/helios-upload";
	static final String UNAUTH_ROLE_ARN = "arn:aws:iam::886472035129:role/helios-upload";	
	static final Regions COGNITO_REGION = Regions.US_EAST_1;
	
	private static final String SERVICE_ACCOUNT_ID = "754068036571-v8j0kf4tivmh252qn3e45uk2fbcu4ktv.apps.googleusercontent.com";
	static final String SCOPE = "audience:server:client_id:" + SERVICE_ACCOUNT_ID;
	static final String S3_BUCKET_NAME = "helios-smart";
	
	static long UPLOAD_INTERVAL = 75000;
	static long MAX_VIDEO_FILE_SIZE = 50000000; 

}
