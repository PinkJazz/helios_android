package com.Helios;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.util.Log;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQSClient;

class CognitoHelper {
		
	// used to log in to Amazon Web Services and create client object
	// to upload to S3 and send messages to SQS
	CognitoCachingCredentialsProvider cognitoProvider;
	private Map<String, String> logins = new HashMap<String, String>();
	AmazonS3Client s3Client;
	AmazonSQSClient sqsQueue;
	
    private final String TAG = "Helios_" + getClass().getSimpleName(); 

    private Context con;
    private final String token;
    
    CognitoHelper(Context con, String token){
    	this.con = con;
    	this.token = token;
    }
    
	void doCognitoLogin() {
		// log in with Cognito identity provider
	
		cognitoProvider = new CognitoCachingCredentialsProvider(con, Config.IDENTITY_POOL, Config.COGNITO_REGION);
	
		cognitoProvider.clear();
		logins.put("accounts.google.com", token);
		cognitoProvider.withLogins(logins);

		s3Client = new AmazonS3Client(cognitoProvider);
		Log.i(TAG, "Successfully initialized S3 client");
		setupSQS();		
		Log.i(TAG, "Successfully initialized SQS client");
	}
	
	private void setupSQS(){
		sqsQueue = new AmazonSQSClient(cognitoProvider);
		sqsQueue.setRegion(Config.SQS_QUEUE_REGION);
	}
	
	String getIdentityID(){
		// returns cognito id from cache or with network request.
		// Calling this from main thread will crash the app 
		
		String id = cognitoProvider.getCachedIdentityId(); 
		return (id == null) ? cognitoProvider.getIdentityId() : id;			
	}
	
	void sendCognitoMessage(final String mEmail){
		// send msg to SQS queue so server component can match Cognito ID to email address
		new Thread(new Runnable(){
			public void run(){
				String msg = "cognito#" + getIdentityID() + "#" + mEmail;
				try{
					sqsQueue.sendMessage(Config.SQS_QUEUE_URL, msg);	
					Log.d(TAG, "Sent " + msg + " to SQS queue");
				}
				catch(Exception e){
					Log.d(TAG, "Exception when sending message to SQS queue " + e.getMessage());
				}
			}
		}).start();
	}

	static void sendSQSMessage(AmazonSQSClient sqsQueue, String key) {
		if(Config.DEBUG_SQS_ENABLED)
			sqsQueue.sendMessage(Config.SQS_QUEUE_URL, key);
	}
}
