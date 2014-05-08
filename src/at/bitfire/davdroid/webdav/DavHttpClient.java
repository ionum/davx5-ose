/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import at.bitfire.davdroid.Constants;
import ch.boye.httpclientandroidlib.client.config.RequestConfig;
import ch.boye.httpclientandroidlib.config.Registry;
import ch.boye.httpclientandroidlib.config.RegistryBuilder;
import ch.boye.httpclientandroidlib.conn.socket.ConnectionSocketFactory;
import ch.boye.httpclientandroidlib.conn.socket.PlainConnectionSocketFactory;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ch.boye.httpclientandroidlib.impl.client.HttpClientBuilder;
import ch.boye.httpclientandroidlib.impl.client.HttpClients;
import ch.boye.httpclientandroidlib.impl.conn.ManagedHttpClientConnectionFactory;
import ch.boye.httpclientandroidlib.impl.conn.PoolingHttpClientConnectionManager;

public class DavHttpClient {
	private final static String TAG = "davdroid.DavHttpClient";
	
	private final static RequestConfig defaultRqConfig;
	private final static Registry<ConnectionSocketFactory> socketFactoryRegistry;
		
	static {
		socketFactoryRegistry =	RegistryBuilder.<ConnectionSocketFactory> create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", TlsSniSocketFactory.INSTANCE)
				.build();
		
		// use request defaults from AndroidHttpClient
		defaultRqConfig = RequestConfig.copy(RequestConfig.DEFAULT)
				.setConnectTimeout(20*1000)
				.setSocketTimeout(20*1000)
				.setStaleConnectionCheckEnabled(false)
				.build();
	}


	public static CloseableHttpClient create(Context context) {
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		// limits per DavHttpClient (= per DavSyncAdapter extends AbstractThreadedSyncAdapter)
		connectionManager.setMaxTotal(3);				// max.  3 connections in total
		connectionManager.setDefaultMaxPerRoute(2);		// max.  2 connections per host
		
		HttpClientBuilder builder = HttpClients.custom()
				.useSystemProperties()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(defaultRqConfig)
				.setRetryHandler(DavHttpRequestRetryHandler.INSTANCE)
				.setUserAgent("DAVdroid/" + Constants.APP_VERSION)
				.disableCookieManagement();

		// debug options
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		if (settings.getBoolean(Constants.SETTING_DISABLE_COMPRESSION, false))
			builder = builder.disableContentCompression();
		
		boolean networkLogging = settings.getBoolean(Constants.SETTING_NETWORK_LOGGING, false);
		Log.d(TAG, "Network logging: " + networkLogging);
		ManagedHttpClientConnectionFactory.INSTANCE.wirelog.enableDebug(networkLogging);
		ManagedHttpClientConnectionFactory.INSTANCE.log.enableDebug(networkLogging);

		return builder.build();
	}

}
