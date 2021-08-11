package de.doering.dwca.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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

  public HttpUtils(String username, String password) {
    this.client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build();
    this.username = username;
    this.password = password;
  }

  public boolean exists(String url){
    try {
      head(url);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public HttpResponse<InputStream> head(String url) throws Exception {
    HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
        .method("HEAD", HttpRequest.BodyPublishers.noBody());
    return send(req, HttpResponse.BodyHandlers.ofInputStream());
  }


  public String get(String url) throws Exception {
    return get(URI.create(url));
  }
  public String get(URI url) throws Exception {
    return send(HttpRequest.newBuilder(url), HttpResponse.BodyHandlers.ofString()).body();
  }

  public InputStream getStream(String url) throws Exception {
    return getStream(URI.create(url));
  }
  public InputStream getStream(URI url) throws Exception {
    return send(HttpRequest.newBuilder(url), HttpResponse.BodyHandlers.ofInputStream()).body();
  }

  public void download(String url, File downloadTo) throws Exception {
    download(URI.create(url), downloadTo);
  }

  public void download(URI url, File downloadTo) throws Exception {
    // execute
    send(HttpRequest.newBuilder(url), HttpResponse.BodyHandlers.ofFile(downloadTo.toPath()));
    LOG.info("Downloaded {} to {}", url, downloadTo.getAbsolutePath());
  }

  public <T> HttpResponse<T> send(HttpRequest.Builder req, HttpResponse.BodyHandler<T> bodyHandler) throws Exception {
    basicAuth(req);
    req.header("User-Agent", "GBIF-ChecklistBuilder/1.0");
    HttpResponse<T> resp = client.send(req.build(), bodyHandler);
    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
      return resp;
    }
    throw new RuntimeException("HTTP Error " + resp.statusCode() + " for " + req);
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
          .header("Content-Type", "application/json")
          .header("Accept", "application/json");
  }

  public <T> T readJson(String url, Class<T> objClazz) throws Exception {
    HttpResponse<InputStream> resp = send(json(url), HttpResponse.BodyHandlers.ofInputStream());
    return MAPPER.readValue(resp.body(), objClazz);
  }

  /**
   * Reads a JSON response containing a "results" property containing an array.
   */
  public <T> List<T> readJsonResult(String url, Class<T> objClazz) throws Exception {
    HttpResponse<InputStream> resp = send(json(url), HttpResponse.BodyHandlers.ofInputStream());
    JsonNode parent = new ObjectMapper().readTree(resp.body());
    JavaType itemType = MAPPER.getTypeFactory().constructCollectionType(List.class, objClazz);
    return MAPPER.readValue(parent.get("result").toString(), itemType);
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

}
