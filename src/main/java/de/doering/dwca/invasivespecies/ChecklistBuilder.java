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
package de.doering.dwca.invasivespecies;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.text.DwcaWriter;
import org.gbif.metadata.eml.Eml;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import com.google.inject.Inject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.ccil.cowan.tagsoup.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

public class ChecklistBuilder {

  private Logger log = LoggerFactory.getLogger(getClass());
  private DwcaWriter writer;
  private static final String WEBAPGE = "http://www.invasivespecies.net/database/species/List.asp";
  private static final String LINK_BASE = "http://www.invasivespecies.net/database/species/";
  private static final String ENCODING = "latin1";

  @Inject
  public ChecklistBuilder() {
  }

  private void parsePage() throws IOException {
    // get webapge
    log.info("Downloading latest invasives webpage from {}", WEBAPGE);

    DefaultHttpClient client = new DefaultHttpClient();
    HttpGet get = new HttpGet(WEBAPGE);

    // execute
    HttpResponse response = client.execute(get);
    HttpEntity entity = response.getEntity();
    // parse page
    final Parser parser = new Parser();
    InvasivespeciesHandler handler = new InvasivespeciesHandler(writer, LINK_BASE);
    try {
      parser.setContentHandler(handler);
      parser.setFeature(Parser.namespacesFeature, false);
      Reader reader = new InputStreamReader(entity.getContent(), ENCODING);
      parser.parse(new InputSource(reader));
    } catch (Exception e) {
      log.error("Cannot process page", e);
    }
  }

  public File build() throws IOException {
    // new writer
    File dwcaDir = FileUtils.createTempDir("invasives-", "-dwca");
    File dwcaZip = new File(dwcaDir.getAbsoluteFile() + ".zip");
    log.info("Writing archive files to temporary folder " + dwcaDir);
    writer = new DwcaWriter(DwcTerm.Taxon, dwcaDir);

    // parse file
    parsePage();

    // finish archive and zip it
    log.info("Bundling archive at {}", dwcaZip);
    writer.setEml(buildEml());
    writer.finalize();

    // compress
    CompressionUtil.zipDir(dwcaDir, dwcaZip);
    // remove temp folder
    org.apache.commons.io.FileUtils.deleteDirectory(dwcaDir);

    log.info("Dwc archive completed at {} !", dwcaZip);

    return dwcaZip;
  }


  private Eml buildEml() {
    Eml eml = new Eml();
    eml.setTitle("Global Invasive Species Database", "en");
    eml.setAbstract(
      "The Global Invasive Species Database is a free, online searchable source of information about species that negatively impact biodiversity. The GISD aims to increase public awareness about invasive species and to facilitate effective prevention and management activities by disseminating specialist’s knowledge and experience to a broad global audience. It focuses on invasive alien species that threaten native biodiversity and covers all taxonomic groups from micro-organisms to animals and plants.");
    eml.setHomepageUrl("http://www.invasivespecies.net");
    eml.setLogoUrl("http://www.issg.org/picts/issg_logo.gif");
    org.gbif.metadata.eml.Agent contact = new org.gbif.metadata.eml.Agent();
    contact.setFirstName("Michael");
    contact.setLastName("Browne");
    contact.setEmail("mtjbro@XTRA.CO.NZ");
    eml.setContact(contact);
    return eml;
  }

  public static void main(String[] args) throws IOException {
    ChecklistBuilder builder = new ChecklistBuilder();
    File archive = builder.build();
    System.out.println("Archive generated at " + archive.getAbsolutePath());
  }
}
