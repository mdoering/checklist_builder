package de.doering.dwca.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 */
public class HttpUtils {
  private static Logger LOG = LoggerFactory.getLogger(HttpUtils.class);
  private final HttpClient client;
  private final String username;
  private final String password;
  private static final String LAST_MODIFIED = "Last-Modified";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public HttpUtils(HttpClient client, String username, String password) {
    this.client = client;
    this.username = username;
    this.password = password;
  }

  public String get(String url) throws Exception {
    HttpResponse<String> resp = execString(HttpRequest.newBuilder(URI.create(url)));
    if (HttpUtils.success(resp)) {
      return resp.body();
    }
    LOG.warn("Error getting {}: {}", url, resp.statusCode());
    return null;
  }

  public InputStream getStream(String url) throws Exception {
    return getStream(URI.create(url));
  }
  public InputStream getStream(URI url) throws Exception {
    HttpResponse<InputStream> resp = execStream(HttpRequest.newBuilder(url));
    if (HttpUtils.success(resp)) {
      return resp.body();
    }
    LOG.warn("Error getting {}: {}", url, resp.statusCode());
    return null;
  }

  public int download(String url, File downloadTo) throws Exception {
    return download(URI.create(url), downloadTo);
  }

  public int download(URI url, File downloadTo) throws Exception {
    HttpRequest get = basicAuth(HttpRequest.newBuilder(url)).build();
    // execute
    HttpResponse<Path> resp = client.send(get, HttpResponse.BodyHandlers.ofFile(downloadTo.toPath()));
    if (success(resp)) {
      LOG.info("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());
    } else {
      LOG.error("Downloading {} to {} failed!: {}", url, downloadTo.getAbsolutePath(), resp.statusCode());
    }
    return resp.statusCode();
  }

  private HttpResponse<InputStream> execStream(HttpRequest.Builder req) throws Exception {
    basicAuth(req);
    return client.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
  }

  private HttpResponse<String> execString(HttpRequest.Builder req) throws Exception {
    basicAuth(req);
    return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest.Builder basicAuth(HttpRequest.Builder req) {
    if (username != null) {
      String auth = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
      req.header("Authorization", auth);
    }
    return req;
  }

  private HttpRequest.Builder json(String url) {
    return HttpRequest.newBuilder(URI.create(url))
          .header(HttpHeaders.CONTENT_TYPE, "application/json")
          .header(HttpHeaders.ACCEPT, "application/json");
  }

  public <T> T readJson(String url, Class<T> objClazz) throws Exception {
    HttpResponse<InputStream> resp = execStream(json(url));
    if (success(resp)) {
      return MAPPER.readValue(resp.body(), objClazz);
    }
    LOG.warn("Error getting {}: {}", url, resp.statusCode());
    return null;
  }

  /**
   * Reads a JSON response containing a "results" property containing an array.
   */
  public <T> List<T> readJsonResult(String url, Class<T> objClazz) throws Exception {
    HttpResponse<InputStream> resp = execStream(json(url));
    if (success(resp)) {
      JsonNode parent = new ObjectMapper().readTree(resp.body());
      JavaType itemType = MAPPER.getTypeFactory().constructCollectionType(List.class, objClazz);
      return MAPPER.readValue(parent.get("result").toString(), itemType);
    }
    LOG.warn("Error getting {}: {}", url, resp.statusCode());
    return null;
  }

  public static boolean success(HttpResponse<?> resp) {
    return resp != null && resp.statusCode() >= 200 && resp.statusCode() < 300;
  }

  private void saveToFile(HttpResponse<InputStream> response, File downloadTo) throws IOException {
    // copy stream to local file
    FileUtils.forceMkdir(downloadTo.getParentFile());
    try (OutputStream fos = new FileOutputStream(downloadTo, false)){
      IOUtils.copy(response.body(), fos);
    }
    // update last modified of file with http header date from server
    Optional<String> modHeader = response.headers().firstValue(LAST_MODIFIED);
    if (modHeader.isPresent()) {
      Date date = parseHeaderDate(modHeader.get());
      downloadTo.setLastModified(date.getTime());
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
