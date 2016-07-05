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
package de.doering.dwca.pbdb;

import org.gbif.api.vocabulary.DatasetType;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.io.CSVReader;
import org.gbif.io.CSVReaderFactory;

import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

/**
 * http://paleobiodb.org
 */
public class ArchiveBuilder extends AbstractBuilder {

  // for testing add parameter: &limit=100
  private static final String DOWNLOAD = "http://paleobiodb.org/data1.2/taxa/list.tsv?all_records&show=attr,common,app,size,ecospace,img,ref,refattr";
  private static final String ENCODING = "UTF-8";
  private static final String DELIMITER = "\t";
  private static final Integer HEADER_ROWS = 1;
  // metadata
  private static final String TITLE = "The Paleobiology Database";
  private static final URI HOMEPAGE = URI.create("https://paleobiodb.org/");
  private static final URI LOGO = URI.create("https://paleobiodb.org/build/img/logo_white.png");
  private static final String ORGANISATION = "The Paleobiology Database";
  private static final String CONTACT_EMAIL = "info@paleobiodb.org";
  private static final String DESCRIPTION1 = "The Paleobiology Database is a public database of paleontological data that anyone can use, maintained by an international non-governmental group of paleontologists.";
  private static final String DESCRIPTION2 = "Fossil occurrences from scientific publications are added to the database by our contributing members. Thanks to our membership, which includes nearly 400 scientists from over 130 institutions in 24 countries, the Paleobiology Database is able to provide scientists and the public with information about the fossil record.";

  private static final int COL_ID = 0;
  private static final int COL_RANK = 4;
  private static final int COL_NAME = 5;
  private static final int COL_AUTHOR = 6;
  private static final int COL_ACCEPTED_ID = 9;
  private static final int COL_PARENT_ID = 12;
  private static final int COL_ACC_TO = 33;
  private static final int COL_EN_NAME = 7;
  private static final int COL_EXTANT = 16;
  private static final int COL_ENVIRONMENT = 24;
  private static final int COL_HABITAT = 27;
  private static final int[] APPEARANCE_COLS = new int[]{18,19,20,21};
  private static final int COL_MIN = COL_ACC_TO;

  private static final String TRUE = Boolean.TRUE.toString();


  @Inject
  public ArchiveBuilder(CliConfiguration cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  @Override
  protected void parseData() throws Exception {
    // update pubdate metadata
    dataset.setPubDate(new Date());

    // get latest CSV
    LOG.info("Downloading latest data from {}", DOWNLOAD);
    HttpGet get = new HttpGet(DOWNLOAD);

    // download
    CloseableHttpResponse response = client.execute(get);
    try {
      HttpEntity entity = response.getEntity();
      CSVReader reader = CSVReaderFactory.build(entity.getContent(), ENCODING, DELIMITER, '"', HEADER_ROWS);

      // parse rows
      int line = HEADER_ROWS;
      for (String[] row : reader) {
        line++;
        if (row == null || row.length < COL_MIN) {
          LOG.warn("Row with too little columns: {}", line);
          continue;
        }

        Integer id = null;
        try {
          id = Integer.parseInt(row[COL_ID]);
        } catch (NumberFormatException e) {
          LOG.warn("Suspicous row with non integer id, ignore line: {}", line);
          continue;
        }
        writer.newRecord(id.toString());
        writer.addCoreColumn(DwcTerm.acceptedNameUsageID, row[COL_ACCEPTED_ID]);
        writer.addCoreColumn(DwcTerm.parentNameUsageID, row[COL_PARENT_ID]);
        writer.addCoreColumn(DwcTerm.taxonRank, row[COL_RANK]);
        writer.addCoreColumn(DwcTerm.scientificName, row[COL_NAME]);
        writer.addCoreColumn(DwcTerm.scientificNameAuthorship, row[COL_AUTHOR]);
        writer.addCoreColumn(DwcTerm.nameAccordingTo, row[COL_ACC_TO]);

        Map<Term, String> data = new HashMap<Term, String>();
        if (!Strings.isNullOrEmpty(row[COL_EN_NAME])) {
          data.put(DwcTerm.vernacularName, row[COL_EN_NAME]);
          data.put(DcTerm.language, "en");
          writer.addExtensionRecord(GbifTerm.VernacularName, data);
        }

        // invert extinct
        data = new HashMap<Term, String>();
        if (!Strings.isNullOrEmpty(row[COL_EXTANT])) {
          Boolean isExtinct = null;
          switch (row[COL_EXTANT].toLowerCase()) {
            case "extinct":
              isExtinct = true;
              break;
            case "extant":
              isExtinct = false;
              break;
          }
          data.put(GbifTerm.isExtinct, isExtinct == null ? null : isExtinct.toString());
        }

        // marine, freshwater, terrestrial
        String env = Strings.nullToEmpty(row[COL_ENVIRONMENT]).toLowerCase();
        if (env.contains("marine") || env.contains("oceanic")) {
          data.put(GbifTerm.isMarine, TRUE);
        }
        if (env.contains("freshwater")) {
          data.put(GbifTerm.isFreshwater, TRUE);
        }
        if (env.contains("terrestrial")) {
          data.put(GbifTerm.isTerrestrial, TRUE);
        }

        // habitat
        data.put(DwcTerm.habitat, row[COL_HABITAT]);

        // concat living period
        LinkedList<Double> appearance = Lists.newLinkedList();
          for (int col : APPEARANCE_COLS) {
            String val = Strings.emptyToNull(row[col]);
            if (val != null) {
              try {
                appearance.add(Double.valueOf(val));
              } catch (NumberFormatException e) {
                LOG.warn("Failed to convert living time {} to double", val);
              }
            }
          }
          if (appearance.size()>1) {
            Collections.sort(appearance);
            String livingPeriod = String.format("%s to %s Ma", appearance.getLast(), appearance.getFirst());
            data.put(GbifTerm.livingPeriod, livingPeriod);
          }
        writer.addExtensionRecord(GbifTerm.SpeciesProfile, data);
      }
    } finally {
      response.close();
    }
  }

  @Override
  protected void addMetadata() {
    // metadata
    dataset.setTitle(TITLE);
    dataset.setDescription(DESCRIPTION1 + "\n\n" + DESCRIPTION2);
    dataset.setHomepage(HOMEPAGE);
    dataset.setLogoUrl(LOGO);
    addContact(ORGANISATION, CONTACT_EMAIL);
  }
}
