/*
 * Copyright 2011 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.doering.dwca.ioc;

import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.BuilderConfig;
import org.gbif.api.vocabulary.ContactType;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.License;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class ArchiveBuilder extends AbstractBuilder {
  public static final String XML_DOWNLOAD = "https://www.worldbirdnames.org/master_ioc-names_xml.xml";
  public static final String ENCODING = "UTF-8";
  // metadata
  public static final String HOMEPAGE = "https://www.worldbirdnames.org";
  public static final String LOGO = "https://www.worldbirdnames.org/img/hdr7.jpg";
  public static final String CONTACT_ORG = "IOC World Bird List";
  private static final String CONTACT_EMAIL = "worldbirdnames@gmail.com";
  public static final License LICENSE = License.CC_BY_4_0;
  private static final String TITLE = "IOC World Bird List, v";
  private static final String DESCRIPTION = "The IOC World Bird List is an open access resource of the international community of ornithologists. " +
      "Our goal is to facilitate worldwide communication in ornithology and conservation based on an  up-to-date classification of world birds and a set of English names that follows explicit guidelines for spelling and construction (Gill & Wright 2006).\n" +
      "\n" +
      "To keep up with the active industry of taxonomic revisions, the IOC editorial team and advisors update the web-based list quarterly.  " +
      "The updates include changes of recommended names or classification, additions of newly described species, corrections of nomenclature, and updates of species taxonomy.\n" +
      "\n" +
      "The IOC World Bird List complements three other primary world bird lists that differ slightly in their primary goals and taxonomic philosophy, i.e. The Clements Checklist of the Birds of the World, The Howard & Moore Complete Checklist of the Birds of the World, 4th Edition, and HBW Alive/Bird Life International.  " +
      "Improved alignment of these independent taxonomic works is a goal of the newly structured International Ornithologists Union, including a Round Table discussion at the 2018 meeting in Vancouver, British Columbia.\n" +
      "\n" +
      "Special thanks always to our expert advisors (left panel), to Sally Conyne for compiling Ranges, to Eng-Li Green for website management, to Larry Master and Colin Campbell for photos, to Peter Kovalik for spreadsheet magic, and to all volunteer participants. We welcome your corrections and your suggestions for improvement.  " +
      "You can reach us at worldbirdnames@gmail.com.";
  List<String> cookies = null;


  public ArchiveBuilder(BuilderConfig cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  @Override
  protected void parseData() throws IOException, SAXException, ParserConfigurationException {
    // get xml data
    LOG.info("Downloading latest IOC world bird list from {}", XML_DOWNLOAD);

    // parse page
    SAXParserFactory factory = SAXParserFactory.newInstance();

    final SAXParser parser = factory.newSAXParser();
    IocXmlHandler handler = new IocXmlHandler(writer);

    try {
      // execute
      InputStream in = getStreamWithCookies();
      Reader reader = new InputStreamReader(in, ENCODING);
      parser.parse(new InputSource(reader), handler);

      setPubDate(handler.getYear());
      dataset.setTitle(TITLE);
      dataset.setVersion(handler.getVersion());

    } catch (Exception e) {
      LOG.error("Cannot process IOC XML", e);
    }
  }

  void setCookies() throws Exception {
    HttpResponse<?> cookieResponse = http.head(XML_DOWNLOAD);
    cookies = cookieResponse.headers().allValues("Set-Cookie");
  }

  InputStream getStreamWithCookies() throws Exception {
    if (cookies == null) {
      setCookies();
    }
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(XML_DOWNLOAD));
    if (cookies != null && !cookies.isEmpty()) {
      builder.header("Cookie", cookies.get(0).split(";")[0] + "; " + cookies.get(1).split(";")[0]);
    }
    return http.send(builder, HttpResponse.BodyHandlers.ofInputStream()).body();
  }

  @Override
  protected void addMetadata() {
    dataset.setDescription(DESCRIPTION);
    dataset.setHomepage(uri(HOMEPAGE));
    dataset.setLogoUrl(uri(LOGO));
    dataset.setLicense(LICENSE);
    addContact(CONTACT_ORG, CONTACT_EMAIL);
    addContactPerson("Frank", "Frank", ContactType.EDITOR);
    addContactPerson("David", "Donsker", ContactType.EDITOR);
  }

}
