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
  private static final String VERSION = "2.10";
  private static final String XML_DOWNLOAD = "http://www.worldbirdnames.org/ioc-names-{VERSION}.xml";
  private static final String ENCODING = "UTF-8";
  // metadata
  private static final String HOMEPAGE = "http://www.worldbirdnames.org";
  private static final String LANGUAGE = "en";
  private static final String LOGO = "http://www.worldbirdnames.org/img/logo7.jpg";
  private static final String BIRD = "http://www.worldbirdnames.org/img/hdr7.jpg";
  private static final String DESCRIPTION = "This initiative provides a set of unique English-language names for the extant species of the birds of the world. The names are based on a consensus of leading ornithologists worldwide and conform to standard rules of construction.<br/><br/>The English names recommended here are:<br/><br/>based on explicit guidelines and spelling rules<br/>selected to involve minimal use of hyphens for group names<br/>anglicized without glottal stops, accents, and the like<br/>based on interregional agreement and global consensus, with compromises<br/>selected with deference to long-established names<br/>aligned with current species taxonomy<br/>available for general adoption<br/>sponsored and endorsed by the IOC and by committee members<br/>This was a volunteer, community effort on behalf of the International Ornithological Congress (IOC).<br/><br/>Commissioned in 1991, the project took 15 years to complete. All participants gave freely and generously of their valuable time and resources. We waived royalty rights and subsidized the publication of the work to maximize its quality and affordability.<br/><br/>Wide dissemination, use, and improvement of the recommended International English names are our only goals. Gratis license to use this list in derivative works can be obtained by writing Frank Gill, P.O. Box 428 , Rushland PA 18956.<br/><br/>In the spirit of realized humility, we dedicate this work to Burt L. Monroe Jr. We just finished the first phase of what he started.<br/><br/>Frank Gill and Minturn Wright <br/>Co-chairs, IOC Standing Committee on English Names  <br/>April 2007";
  private static final String CONTACT_FIRSTNAME = "Frank";
  private static final String CONTACT_LASTNAME = "Gill";
  private static final String CONTACT_LINK = "http://en.wikipedia.org/wiki/Frank_Gill_%28ornithologist%29";

  @Inject
  public ChecklistBuilder() {
  }

  private void parsePage(Eml eml) throws IOException, SAXException, ParserConfigurationException {
    // get webapge
    String url = XML_DOWNLOAD.replace("{VERSION}", VERSION);
    log.info("Downloading latest IOC world bird list from {}", url);

    DefaultHttpClient client = new DefaultHttpClient();
    HttpGet get = new HttpGet(url);

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
    eml.setDescription(DESCRIPTION);
    eml.setLanguage(LANGUAGE);
    eml.setHomepageUrl(HOMEPAGE);
    eml.setLogoUrl(LOGO);
    org.gbif.metadata.eml.Agent contact = new org.gbif.metadata.eml.Agent();
    contact.setFirstName(CONTACT_FIRSTNAME);
    contact.setLastName(CONTACT_LASTNAME);
    contact.setHomepage(CONTACT_LINK);
    eml.setContact(contact);
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
