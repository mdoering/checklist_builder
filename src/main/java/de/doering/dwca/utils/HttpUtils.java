package de.doering.dwca.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.CodingErrorAction;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLContext;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class HttpUtils {
  private static Logger LOG = LoggerFactory.getLogger(HttpUtils.class);
  private final CloseableHttpClient client;
  private final UsernamePasswordCredentials credentials;
  private static final String LAST_MODIFIED = "Last-Modified";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public HttpUtils(CloseableHttpClient client) {
    this(client, null);
  }

  public HttpUtils(CloseableHttpClient client, UsernamePasswordCredentials credentials) {
    this.client = client;
    this.credentials = credentials;
  }

  public static CloseableHttpClient newMultithreadedClient(int timeout, int maxConnections, int maxPerRoute) {
    SSLContext sslcontext = SSLContexts.createSystemDefault();
    SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
        sslcontext,
        new String[] {"TLSv1.2", "TLSv1", "SSLv2Hello", "TLSv1.1", "SSLv3"},
        null,
        SSLConnectionSocketFactory.getDefaultHostnameVerifier());

    Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
        .register("http", PlainConnectionSocketFactory.getSocketFactory())
        .register("https", socketFactory)
        .build();

    ConnectionConfig connectionConfig = ConnectionConfig.custom()
        .setMalformedInputAction(CodingErrorAction.IGNORE)
        .setUnmappableInputAction(CodingErrorAction.IGNORE)
        .setCharset(Consts.UTF_8)
        .build();

    RequestConfig defaultRequestConfig = RequestConfig.copy(RequestConfig.DEFAULT)
        .setConnectTimeout(timeout)
        .setSocketTimeout(timeout)
        .build();

    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
    cm.setDefaultConnectionConfig(connectionConfig);
    cm.setMaxTotal(maxConnections);
    cm.setDefaultMaxPerRoute(maxPerRoute);

    HttpClientBuilder builder = HttpClients.custom()
        .setConnectionManager(cm)
        .setDefaultRequestConfig(defaultRequestConfig);

    return builder.build();
  }

  public String get(String url) throws IOException, AuthenticationException {
    try (CloseableHttpResponse response = execute(new HttpGet(url))) {
      if (HttpUtils.success(response.getStatusLine())) {
        return EntityUtils.toString(response.getEntity());
      }
      LOG.warn("Error getting {}: {}", url, response.getStatusLine());
      return null;
    }
  }
  public StatusLine download(String url, File downloadTo) throws IOException, AuthenticationException {
    return download(new URL(url), downloadTo);
  }
  public StatusLine download(URL url, File downloadTo) throws IOException, AuthenticationException {
    HttpGet get = new HttpGet(url.toString());

    // execute
    CloseableHttpResponse response = execute(get);
    final StatusLine status = response.getStatusLine();
    try {
      // write to file only when download succeeds
      if (success(status)) {
        saveToFile(response, downloadTo);
        LOG.info("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());
      } else {
        LOG.error("Downloading {} to {} failed!: {}", url, downloadTo.getAbsolutePath(), status.getStatusCode());
      }
    } finally {
      closeQuietly(response);
    }
    return status;
  }

  private CloseableHttpResponse execute(HttpUriRequest req) throws IOException, AuthenticationException {
    if (credentials != null) {
      req.addHeader(new BasicScheme().authenticate(credentials, req));
      //req.setHeader(HttpHeaders.AUTHORIZATION, "application/json");
    }
    return client.execute(req);
  }

  public <T> T readJson(String url, Class<T> objClazz) throws IOException, AuthenticationException {
    HttpUriRequest request = RequestBuilder.get()
        .setUri(url)
        .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .setHeader(HttpHeaders.ACCEPT, "application/json")
        .build();
    try (CloseableHttpResponse response = execute(request)) {
      if (HttpUtils.success(response.getStatusLine())) {
        return MAPPER.readValue(response.getEntity().getContent(), objClazz);
      }
      LOG.warn("Error getting {}: {}", url, response.getStatusLine());
      return null;
    }
  }

  /**
   * Reads a JSON response containing a "results" property containing an array.
   */
  public <T> List<T> readJsonResult(String url, Class<T> objClazz) throws IOException, AuthenticationException {
    HttpUriRequest request = RequestBuilder.get()
      .setUri(url)
      .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
      .setHeader(HttpHeaders.ACCEPT, "application/json")
      .build();
    try (CloseableHttpResponse response = execute(request)) {
      if (HttpUtils.success(response.getStatusLine())) {
        JsonNode parent = new ObjectMapper().readTree(response.getEntity().getContent());
        JavaType itemType = MAPPER.getTypeFactory().constructCollectionType(List.class, objClazz);
        return MAPPER.readValue(parent.get("result").toString(), itemType);
      }
      LOG.error("Error getting {}: {}", url, response.getStatusLine());
      return null;
    }
  }

  public static boolean success(StatusLine status) {
    return status != null && status.getStatusCode() >= 200 && status.getStatusCode() < 300;
  }

  private void saveToFile(CloseableHttpResponse response, File downloadTo) throws IOException {
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      Date serverModified = null;
      Header modHeader = response.getFirstHeader(LAST_MODIFIED);
      if (modHeader != null) {
        serverModified = parseHeaderDate(modHeader.getValue());
      }

      // copy stream to local file
      FileUtils.forceMkdir(downloadTo.getParentFile());
      try (OutputStream fos = new FileOutputStream(downloadTo, false)){
        entity.writeTo(fos);
      }
      // update last modified of file with http header date from server
      if (serverModified != null) {
        downloadTo.setLastModified(serverModified.getTime());
      }
    }
  }

  /**
   * Parses a RFC2616 compliant date string such as used in http headers.
   *
   * @see <a href="http://tools.ietf.org/html/rfc2616#section-3.3">RFC 2616</a> specification.
   *      example:
   *      Wed, 21 Jul 2010 22:37:31 GMT
   * @param rfcDate RFC2616 compliant date string
   * @return the parsed date or null if it cannot be parsed
   */
  private static Date parseHeaderDate(String rfcDate) {
    try {
      if (rfcDate != null) {
        // as its not thread safe we create a new instance each time
        return new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(rfcDate);
      }
    } catch (ParseException e) {
      LOG.warn("Can't parse RFC2616 date");
    }
    return null;
  }

  public static void closeQuietly(CloseableHttpResponse resp) {
    if (resp != null) {
      try {
        resp.close();
      } catch (IOException e) {
        LOG.warn("Failed to close http response", e);
      }
    }
  }
}
