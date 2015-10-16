/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.content.Context;
import android.os.Build;

import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.CertificatePinner;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.tls.OkHostnameVerifier;
import com.squareup.okhttp.logging.HttpLoggingInterceptor;

import java.io.IOException;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import at.bitfire.dav4android.BasicDigestAuthenticator;
import at.bitfire.dav4android.HttpUtils;
import de.duenndns.ssl.MemorizingTrustManager;
import lombok.RequiredArgsConstructor;

public class HttpClient extends OkHttpClient {
    protected static final String HEADER_AUTHORIZATION = "Authorization";

    final static UserAgentInterceptor userAgentInterceptor = new UserAgentInterceptor();
    final static HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
        @Override
        public void log(String message) {
            Constants.log.trace(message);
        }
    });
    static {
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
    }

    static final String userAgent;
    static {
        String date = new SimpleDateFormat("yyyy/MM/dd").format(BuildConfig.buildTime);
        userAgent = "DAVdroid/" + BuildConfig.VERSION_NAME + " (" + date + "; dav4android) Android/" + Build.VERSION.RELEASE;
    }

    final Context context;
    protected String username, password;


    public HttpClient() {
        super();
        context = null;
        initialize();
    }

    public HttpClient(Context context, String username, String password, boolean preemptive) {
        super();
        this.context = context;

        initialize();

        // authentication
        this.username = username;
        this.password = password;
        if (preemptive)
            networkInterceptors().add(new PreemptiveAuthenticationInterceptor(username, password));
        else
            setAuthenticator(new BasicDigestAuthenticator(null, username, password));
    }

    /**
     * Creates a new HttpClient (based on another one) which can be used to download external resources:
     * 1. it does not use preemptive authentication
     * 2. it only authenticates against a given host
     * @param client  user name and password from this client will be used
     * @param host    authentication will be restricted to this host
     */
    public HttpClient(HttpClient client, String host) {
        super();
        context = client.context;

        initialize();

        username = client.username;
        password = client.password;
        setAuthenticator(new BasicDigestAuthenticator(host, username, password));
    }


    protected void initialize() {
        if (context != null) {
            // use MemorizingTrustManager to manage self-signed certificates
            MemorizingTrustManager mtm = new MemorizingTrustManager(context);
            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, new X509TrustManager[] { mtm }, null);
                setSslSocketFactory(sc.getSocketFactory());
                setHostnameVerifier(mtm.wrapHostnameVerifier(OkHostnameVerifier.INSTANCE));
            } catch (NoSuchAlgorithmException e) {
                Constants.log.error("Couldn't get SSL Context for MemorizingTrustManager", e);
            } catch (KeyManagementException e) {
                Constants.log.error("Key management error while initializing MemorizingTrustManager", e);
            }
        }

        // set timeouts
        setConnectTimeout(30, TimeUnit.SECONDS);
        setWriteTimeout(15, TimeUnit.SECONDS);
        setReadTimeout(45, TimeUnit.SECONDS);

        // add User-Agent to every request
        networkInterceptors().add(userAgentInterceptor);

        // enable verbose logs, if requested
        if (Constants.log.isTraceEnabled())
            enableLogs();
    }

    protected void enableLogs() {
        interceptors().add(loggingInterceptor);
    }


    static class UserAgentInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .build();
            return chain.proceed(request);
        }
    }


    @RequiredArgsConstructor
    static class PreemptiveAuthenticationInterceptor implements Interceptor {
        final String username, password;

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build();
            return chain.proceed(request);
        }
    }

}
