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
package de.doering.dwca.iocwbn;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.text.DwcaWriter;
import org.gbif.metadata.eml.Eml;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.google.inject.Inject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ChecklistBuilder {

  private Logger log = LoggerFactory.getLogger(getClass());
  private DwcaWriter writer;
  private static final String XML_DOWNLOAD = "http://www.worldbirdnames.org/master_ioc-names_xml.xml";
  private static final String ENCODING = "UTF-8";
  // metadata
  private static final String VERSION = "3.4";
  private static final String HOMEPAGE = "http://www.worldbirdnames.org";
  private static final String LANGUAGE = "en";
  private static final String LOGO = "http://www.worldbirdnames.org/img/hdr7.jpg";
  private static final String CONTACT_FIRSTNAME = "Frank";
  private static final String CONTACT_LASTNAME = "Gill";
  private static final String CONTACT_LINK = "http://en.wikipedia.org/wiki/Frank_Gill_%28ornithologist%29";
  private static final String LICENSE = "Creative Commons Attribution 3.0 Unported License";
  private static final String TITLE = "IOC World Bird List, version " + VERSION;
  private static final String DESCRIPTION = "<p>The initial goal of the IOC World Bird List project was to compile a set of unique English-language names for the extant species of the birds of the world.</p>\n"
                                            + "<p>Launched initially in 1990 and restarted in 1994 after the loss of our colleague Burt L. Monroe Jr,  the project took 15 years to complete. All participants gave freely and generously of their valuable time and resources. This was a volunteer effort of the ornithological community on behalf of the International Ornithological Congress (IOC).</p>\n"
                                            + "<p>The first product – a list of  English names  in Birds of the World, Recommended English Names (Princeton University Press, 2006) – was based on a consensus of leading ornithologists worldwide and conformed to standard rules of construction, namely</p>\n"
                                            + "<ul>\n" + " <li>based on explicit guidelines and spelling rules</li>\n"
                                            + " <li>selected to involve minimal use of hyphens for group names</li>\n"
                                            + " <li>anglicized without glottal stops, accents, and the like</li>\n"
                                            + " <li>based on interregional agreement and global consensus, with compromises</li>\n"
                                            + " <li>selected with deference to long-established names</li>\n"
                                            + " <li>aligned with current species taxonomy</li>\n"
                                            + " <li>available for general adoption</li>\n"
                                            + " <li>sponsored and endorsed by the IOC and by committee members</li>\n"
                                            + "</ul>\n"
                                            + "The project continues as a work in progress that compiles the taxonomy of world birds as well as their English names</p>\n"
                                            + "<p>Frank Gill, David Donsker &  Minturn Wright, April 2007, 2012</p>\n"
                                            + "<p>Co-chairs, IOC Standing Committee on English Names</p>";

  @Inject
  public ChecklistBuilder() {
  }

  private void parsePage(Eml eml) throws IOException, SAXException, ParserConfigurationException {
    // get xml data
    log.info("Downloading latest IOC world bird list from {}", XML_DOWNLOAD);

    DefaultHttpClient client = new DefaultHttpClient();
    HttpGet get = new HttpGet(XML_DOWNLOAD);

    // execute
    HttpResponse response = client.execute(get);
    HttpEntity entity = response.getEntity();
    // parse page
    SAXParserFactory factory = SAXParserFactory.newInstance();

    final SAXParser parser = factory.newSAXParser();
    IocXmlHandler handler = new IocXmlHandler(writer, eml);
    try {
      Reader reader = new InputStreamReader(entity.getContent(), ENCODING);
      parser.parse(new InputSource(reader), handler);
    } catch (Exception e) {
      log.error("Cannot process IOC XML", e);
    }
  }

  public File build() throws IOException, SAXException, ParserConfigurationException {
    // new writer
    File dwcaDir = FileUtils.createTempDir("iocwbn-", "");
    File dwcaZip = new File(dwcaDir.getAbsoluteFile() + ".zip");
    log.info("Writing archive files to temporary folder " + dwcaDir);
    writer = new DwcaWriter(DwcTerm.Taxon, dwcaDir);

    // metadata
    Eml eml = new Eml();
    eml.setTitle(TITLE);
    eml.setDescription(DESCRIPTION);
    eml.setLanguage(LANGUAGE);
    eml.setHomepageUrl(HOMEPAGE);
    eml.setLogoUrl(LOGO);
    org.gbif.metadata.eml.Agent contact = new org.gbif.metadata.eml.Agent();
    contact.setFirstName(CONTACT_FIRSTNAME);
    contact.setLastName(CONTACT_LASTNAME);
    contact.setHomepage(CONTACT_LINK);
    eml.setContact(contact);
    eml.setIntellectualRights(LICENSE);
    // parse file and some metadata
    parsePage(eml);

    // finish archive and zip it
    log.info("Bundling archive at {}", dwcaZip);
    writer.setEml(eml);
    writer.finalize();

    // compress
    CompressionUtil.zipDir(dwcaDir, dwcaZip);
    // remove temp folder
    //org.apache.commons.io.FileUtils.deleteDirectory(dwcaDir);

    log.info("Dwc archive completed at {} !", dwcaZip);

    return dwcaZip;
  }

  public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {
    ChecklistBuilder builder = new ChecklistBuilder();
    File archive = builder.build();
    System.out.println("Archive generated at " + archive.getAbsolutePath());
  }
}
