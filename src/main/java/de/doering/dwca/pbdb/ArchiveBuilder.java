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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.Language;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.utils.file.tabular.TabularDataFileReader;
import org.gbif.utils.file.tabular.TabularFiles;

import java.io.InputStreamReader;
import java.net.URI;
import java.util.*;

/**
 * http://paleobiodb.org
 */
public class ArchiveBuilder extends AbstractBuilder {

    // for testing add parameter: &limit=100
    private static final String DOWNLOAD = "https://paleobiodb.org/data1.2/taxa/list.tsv?all_records&show=attr,common,app,size,ecospace,img,ref,refattr";
    // metadata
    private static final String TITLE = "The Paleobiology Database";
    private static final URI HOMEPAGE = URI.create("https://paleobiodb.org/");
    private static final URI LOGO = URI.create("https://paleobiodb.org/build/logos/pbdb_color.png");
    private static final String ORGANISATION = "The Paleobiology Database";
    private static final String CONTACT_EMAIL = "info@paleobiodb.org";
    private static final String DESCRIPTION1 = "The Paleobiology Database is a public database of paleontological data that anyone can use, maintained by an international non-governmental group of paleontologists.";
    private static final String DESCRIPTION2 = "Fossil occurrences from scientific publications are added to the database by our contributing members. Thanks to our membership, which includes nearly 400 scientists from over 130 institutions in 24 countries, the Paleobiology Database is able to provide scientists and the public with information about the fossil record.";
    private static final String LICENSE = "This work is licensed under a Creative Commons Attribution (CC-BY) 4.0 License.";

    private static final int COL_ORIGINAL_ID = 0;
    private static final int COL_ID = 1;
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
    private static final int[] APPEARANCE_COLS = new int[]{18, 19, 20, 21};
    private static final int COL_MIN = COL_ACC_TO;

    private static final String TRUE = Boolean.TRUE.toString();


    @Inject
    public ArchiveBuilder(CliConfiguration cfg) {
        super(DatasetType.CHECKLIST, cfg);
    }

    private String avoidZero(String x) {
      return x != null && x.equals("0") ? null : x;
    }

    @Override
    protected void parseData() throws Exception {
        // get latest CSV
        LOG.info("Downloading latest data from {}", DOWNLOAD);
        HttpGet get = new HttpGet(DOWNLOAD);

        // download
        CloseableHttpResponse response = client.execute(get);
        try {
            TabularDataFileReader<List<String>> reader = TabularFiles.newTabularFileReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"), '\t', "\n", '"', true);

            // parse rows
            List<String> row;
            while ((row = reader.read()) != null) {
                if (row.size() < COL_MIN) {
                    LOG.warn("Row {} with too little columns", reader.getLastRecordLineNumber());
                    continue;
                }

                Integer id = null;
                try {
                    id = Integer.parseInt(row.get(COL_ID));
                } catch (NumberFormatException e) {
                    LOG.warn("Suspicous row with non integer id, ignore line: {}", reader.getLastRecordLineNumber());
                    continue;
                }
                writer.newRecord(id.toString());
                writer.addCoreColumn(DwcTerm.acceptedNameUsageID, avoidZero(row.get(COL_ACCEPTED_ID)));
                writer.addCoreColumn(DwcTerm.parentNameUsageID, avoidZero(row.get(COL_PARENT_ID)));
                writer.addCoreColumn(DwcTerm.originalNameUsageID, avoidZero(row.get(COL_ORIGINAL_ID)));
                writer.addCoreColumn(DwcTerm.taxonRank, row.get(COL_RANK));
                writer.addCoreColumn(DwcTerm.scientificName, row.get(COL_NAME));
                writer.addCoreColumn(DwcTerm.scientificNameAuthorship, row.get(COL_AUTHOR));
                writer.addCoreColumn(DwcTerm.nameAccordingTo, row.get(COL_ACC_TO));

                Map<Term, String> data = new HashMap<Term, String>();
                if (!Strings.isNullOrEmpty(row.get(COL_EN_NAME))) {
                    data.put(DwcTerm.vernacularName, row.get(COL_EN_NAME));
                    data.put(DcTerm.language, "en");
                    writer.addExtensionRecord(GbifTerm.VernacularName, data);
                }

                // invert extinct
                data = new HashMap<Term, String>();
                if (!Strings.isNullOrEmpty(row.get(COL_EXTANT))) {
                    Boolean isExtinct = null;
                    switch (row.get(COL_EXTANT).toLowerCase()) {
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
                String env = Strings.nullToEmpty(row.get(COL_ENVIRONMENT)).toLowerCase();
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
                data.put(DwcTerm.habitat, row.get(COL_HABITAT));

                // concat living period
                LinkedList<Double> appearance = Lists.newLinkedList();
                for (int col : APPEARANCE_COLS) {
                    String val = Strings.emptyToNull(row.get(col));
                    if (val != null) {
                        try {
                            appearance.add(Double.valueOf(val));
                        } catch (NumberFormatException e) {
                            LOG.warn("Failed to convert living time {} to double", val);
                        }
                    }
                }
                if (appearance.size() > 1) {
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
        dataset.setPubDate(new Date());
        dataset.setTitle(TITLE);
        dataset.setDescription(DESCRIPTION1 + "\n\n" + DESCRIPTION2);
        dataset.setRights(LICENSE);
        dataset.setLanguage(Language.ENGLISH);
        dataset.setHomepage(HOMEPAGE);
        dataset.setLogoUrl(LOGO);
        addContact(ORGANISATION, CONTACT_EMAIL);
    }
}
