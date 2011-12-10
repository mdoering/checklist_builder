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
package de.doering.dwca.clemens;

import org.gbif.dwc.terms.ConceptTerm;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.text.DwcaWriter;
import org.gbif.file.CSVReader;
import org.gbif.file.CSVReaderFactory;
import org.gbif.metadata.eml.Citation;
import org.gbif.metadata.eml.Eml;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChecklistBuilder {

  private Logger log = LoggerFactory.getLogger(getClass());
  private Pattern cleanFamily = Pattern.compile("^([^ ,(]+)");
  private DwcaWriter writer;
  private static final String VERSION = "6.6";
  private static final String DOWNLOAD = "http://www.birds.cornell.edu/clementschecklist/Clements%20Checklist%20{VERSION}.csv";
  private static final String ENCODING = "UTF-8";
  private static final String DELIMITER = ",";
  private static final Character QUOTES = '"';
  private static final Integer HEADER_ROWS = 1;
  // metadata
  private static final String TITLE = "The Clements Checklist";
  private static final String HOMEPAGE = "http://www.birds.cornell.edu/clementschecklist";
  private static final String CITATION = "Clements, J. F., T. S. Schulenberg, M. J. Iliff, B.L. Sullivan, C. L. Wood, and D. Roberson. 2011. The Clements checklist of birds of the world: Version";
  private static final String LANGUAGE = "en";
  private static final String LOGO = null;
  private static final String DESCRIPTION = "The Clements Checklist of Birds of the World, 6th Edition was published and released by Cornell University Press in June 2007. The book was produced from a nearly completed manuscript left by James Clements upon his death in 2005.<br/>The Cornell Lab of Ornithology has accepted the job of maintaining the ever-changing list of species, subspecies, English names, and approximate distributions, beginning with publication of the 6th Edition. Our procedures for accomplishing this ongoing task include using the considerable expertise of our research ornithologists on staff, aided enormously by input from knowledgeable professional and amateur cooperators worldwide. We invite input on known or suspected errors or updates at any time.<br/>This web site serves as the clearinghouse for keeping your Clements Checklist up to date. We will post all corrections once a year in October. At the same time, we'll post updates to the taxonomy, scientific and English nomenclature, and range descriptions, to incorporate changes that have made their way into the literature and are generally accepted by the appropriate scientific body or community. In the future, we will also be posting a list of alternative English names.";
  private static final String CONTACT_ORG = "Cornell Lab of Ornithology";
  private static final String CONTACT_EMAIL = "cornellbirds@cornell.edu";

  // Clements 6.6 change,Text for website,Sort 6.6,CATEGORY,SCIENTIFIC NAME,ENGLISH NAME,RANGE,ORDER,FAMILY,EXTINCT,EXTINCT_YEAR,SORT 6.5,PAGE 6.0,Protected Sort 4 Aug 2010,,,,,,,
  private static final int COL_ID = 2;
  private static final int COL_RANK = 3;
  private static final int COL_NAME = 4;
  private static final int COL_EN_NAME = 5;
  private static final int COL_RANGE = 6;
  private static final int COL_ORDER = 7;
  private static final int COL_FAMILY = 8;
  private static final int COL_EXTINCT = 9;
  private static final int COL_EXTINCT_YEAR = 10;
  private static final int COL_MIN = 10;

  @Inject
  public ChecklistBuilder() {
  }

  private void parseData() throws IOException {
    // get webapge
    String url = DOWNLOAD.replace("{VERSION}", VERSION);
    log.info("Downloading latest data from {}", url);

    DefaultHttpClient client = new DefaultHttpClient();
    HttpGet get = new HttpGet(url);

    // download
    HttpResponse response = client.execute(get);
    HttpEntity entity = response.getEntity();
    CSVReader reader = CSVReaderFactory.build(entity.getContent(), ENCODING, DELIMITER, QUOTES, HEADER_ROWS);

    // parse rows
    int line = HEADER_ROWS;
    for (String[] row : reader){
      line++;
      if (row== null || row.length < COL_MIN){
        log.warn("Row with too little columns: {}", line);
        continue;
      }
      Integer id = null;
      try {
        id = Integer.parseInt(row[COL_ID]);
      } catch (NumberFormatException e) {
        log.warn("Suspicous row with non integer id, ignore line: {}", line);
        continue;
      }
      writer.newRecord(id.toString());
      writer.addCoreColumn(DwcTerm.scientificName, row[COL_NAME]);
      writer.addCoreColumn(DwcTerm.taxonRank, row[COL_RANK]);
      writer.addCoreColumn(DwcTerm.kingdom, "Animalia");
      writer.addCoreColumn(DwcTerm.classs, "Aves");
      writer.addCoreColumn(DwcTerm.order, row[COL_ORDER]);
      if (Strings.isNullOrEmpty(row[COL_FAMILY])){
        Matcher m = cleanFamily.matcher(row[COL_FAMILY]);
        if (m.find()){
          writer.addCoreColumn(DwcTerm.family, m.group());
        }
      }

      Map<ConceptTerm, String> data = new HashMap<ConceptTerm, String>();
      data.put(DwcTerm.vernacularName, row[COL_EN_NAME]);
      data.put(DcTerm.language, "en");
      writer.addExtensionRecord(GbifTerm.VernacularName, data);

      data = new HashMap<ConceptTerm, String>();
      data.put(DcTerm.description, row[COL_RANGE]);
      data.put(DcTerm.type, "Distribution");
      writer.addExtensionRecord(GbifTerm.Description, data);

      // extinct
      data = new HashMap<ConceptTerm, String>();
      data.put(GbifTerm.isExtinct, "1".equalsIgnoreCase(Strings.nullToEmpty(row[COL_EXTINCT])) ? "true" : "false");
      if (!Strings.isNullOrEmpty(row[COL_EXTINCT_YEAR])){
        data.put(GbifTerm.livingPeriod, "Recent until " + row[COL_EXTINCT_YEAR]);
      }
      writer.addExtensionRecord(GbifTerm.SpeciesProfile, data);
    }

    // close
    reader.close();
    EntityUtils.consume(entity);
  }

  public File build() throws IOException {
    // new writer
    File dwcaDir = FileUtils.createTempDir("clemens-", "");
    File dwcaZip = new File(dwcaDir.getAbsoluteFile() + ".zip");
    log.info("Writing archive files to temporary folder " + dwcaDir);
    writer = new DwcaWriter(DwcTerm.Taxon, dwcaDir);

    // metadata
    Eml eml = new Eml();
    eml.setTitle(TITLE);
    Citation cite = new Citation();
    cite.setCitation(CITATION);
    eml.setCitation(cite);
    eml.setDescription(DESCRIPTION);
    eml.setLanguage(LANGUAGE);
    eml.setHomepageUrl(HOMEPAGE);
    eml.setLogoUrl(LOGO);
    org.gbif.metadata.eml.Agent contact = new org.gbif.metadata.eml.Agent();
    contact.setOrganisation(CONTACT_ORG);
    contact.setEmail(CONTACT_EMAIL);
    eml.setContact(contact);

    // parse file and some metadata
    parseData();

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

  public static void main(String[] args) throws IOException {
    ChecklistBuilder builder = new ChecklistBuilder();
    File archive = builder.build();
    System.out.println("Archive generated at " + archive.getAbsolutePath());
  }
}
