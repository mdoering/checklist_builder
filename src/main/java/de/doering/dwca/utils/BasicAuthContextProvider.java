package de.doering.dwca.utils;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;

/**
 *
 */
public class BasicAuthContextProvider {
  private final AuthCache authCache;
  private final CredentialsProvider credentialsProvider;

  public BasicAuthContextProvider(HttpHost host, String user, String password) {
    // Create AuthCache instance
    this.authCache = new BasicAuthCache();
    // Generate BASIC scheme object and add it to the local auth cache
    BasicScheme basicAuth = new BasicScheme();
    authCache.put(host, basicAuth);

    credentialsProvider = new BasicCredentialsProvider();
    AuthScope scope = new AuthScope(host);
    credentialsProvider.setCredentials(scope, new UsernamePasswordCredentials("mdoering@gbif.org", "NzFhs9MAC44L"));
  }

  public HttpClientContext newBasicAuthContext() {
    // Add AuthCache to the execution context
    HttpClientContext context = HttpClientContext.create();
    context.setCredentialsProvider(credentialsProvider);
    context.setAuthCache(authCache);
    return context;
  }
}
