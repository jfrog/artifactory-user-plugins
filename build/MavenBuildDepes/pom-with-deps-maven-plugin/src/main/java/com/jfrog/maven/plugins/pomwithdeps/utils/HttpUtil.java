package com.jfrog.maven.plugins.pomwithdeps.utils;


import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

import static org.apache.http.params.HttpConnectionParams.setConnectionTimeout;

/**
 * Created by user on 30/03/2016.
 */
public class HttpUtil {
    private static BasicHttpContext localContext;
    private final static String CLIENT_NAME = "com.jfrog.maven.plugins.pom-with-deps-maven-plugin";
    private final static String CLIENT_VERSION = "1.0.0";

    public static DefaultHttpClient createHttpClient(String userName, String password, int timeout) {
        BasicHttpParams params = new BasicHttpParams();
        int timeoutMilliSeconds = timeout * 1000;
        setConnectionTimeout(params, timeoutMilliSeconds);
        HttpConnectionParams.setSoTimeout(params, timeoutMilliSeconds);
        DefaultHttpClient client = new DefaultHttpClient(params);

        client.getParams().setParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);

        if (userName != null && !"".equals(userName)) {
            client.getCredentialsProvider().setCredentials(
                    new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                    new UsernamePasswordCredentials(userName, password)
            );
            localContext = new BasicHttpContext();

            // Generate BASIC scheme object and stick it to the local execution context
            BasicScheme basicAuth = new BasicScheme();
            localContext.setAttribute("preemptive-auth", basicAuth);

            // Add as the first request interceptor
            client.addRequestInterceptor(new PreemptiveAuth(), 0);
        }
        boolean requestSentRetryEnabled = Boolean.parseBoolean(System.getProperty("requestSentRetryEnabled"));
        if (requestSentRetryEnabled) {
            client.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(3, requestSentRetryEnabled));
        }
        // set the following user agent with each request
        String userAgent = CLIENT_NAME + "/" + CLIENT_VERSION;
        HttpProtocolParams.setUserAgent(client.getParams(), userAgent);
        return client;
    }

    static class PreemptiveAuth implements HttpRequestInterceptor {
        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {

            AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);

            // If no auth scheme available yet, try to initialize it preemptively
            if (authState.getAuthScheme() == null) {
                AuthScheme authScheme = (AuthScheme) context.getAttribute("preemptive-auth");
                CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(
                        ClientContext.CREDS_PROVIDER);
                HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
                if (authScheme != null) {
                    Credentials creds = credsProvider.getCredentials(
                            new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                    if (creds == null) {
                        throw new HttpException("No credentials for preemptive authentication");
                    }
                    authState.setAuthScheme(authScheme);
                    authState.setCredentials(creds);
                }
            }
        }
    }

    public static HttpResponse execute(HttpUriRequest request, DefaultHttpClient httpClient) throws IOException {
        if (localContext != null) {
            return httpClient.execute(request, localContext);
        } else {
            return httpClient.execute(request);
        }
    }
}
