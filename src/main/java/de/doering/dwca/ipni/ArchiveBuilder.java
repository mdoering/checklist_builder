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
package de.doering.dwca.ipni;

import org.gbif.api.vocabulary.DatasetType;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.apache.commons.lang3.StringUtils;

public class ArchiveBuilder extends AbstractBuilder {

    private Pattern removeFamily = Pattern.compile("^ *[A-Z][a-z]+ ");
    // metadata
    private static final String TITLE = "The International Plant Names Index";
    private static final URI HOMEPAGE = URI.create("http://www.ipni.org");
    private static final URI LOGO = URI.create("http://www.ipni.org/images/ipni_logoonly_sm.jpg");

    private final Term replacedSynonym = new UnknownTerm(URI.create("http://ipni.org/replacedSynonym"),"replacedSynonym");
    // for the classic delimited output
    public static final int COL_ID = 0;
    public static final int COL_FAMILY = 2;
    public static final int COL_RANK = 11;
    public static final int COL_AUTHORS = 12;
    public static final int COL_NAME = 16;
    public static final int COL_PUBLISHED_IN = 19;
    public static final int COL_REMARKS = 22;
    public static final int COL_BASIONYM = 24;
    public static final int COL_REPL_SYNONYM = 25;
    public static final int COL_MIN = 30;

    @Inject
    public ArchiveBuilder(CliConfiguration cfg) {
        super(DatasetType.CHECKLIST, cfg);
    }

    @Override
    protected void parseData() throws Exception {
        IpniIterator iter = new IpniIterator();

        while (iter.hasNext()) {
            String[] row = iter.next();
            writeRow(row);
        }
    }

    private void writeRow(String[] row) throws IOException {
        if (row == null) return;
        if (row.length < COL_MIN) {
            LOG.warn("Row with too little columns:\n{}", row);
            return;
        }
        writer.newRecord(row[COL_ID]);
        writer.addCoreColumn(DwcTerm.scientificName, row[COL_NAME]);
        writer.addCoreColumn(DwcTerm.scientificNameAuthorship, row[COL_AUTHORS]);
        writer.addCoreColumn(DwcTerm.taxonRank, row[COL_RANK]);
        writer.addCoreColumn(DwcTerm.namePublishedIn, row[COL_PUBLISHED_IN]);
        writer.addCoreColumn(DwcTerm.family, row[COL_FAMILY]);
        writer.addCoreColumn(DwcTerm.kingdom, "Plantae");
        writer.addCoreColumn(DcTerm.references, "http://www.ipni.org/ipni/idPlantNameSearch.do?&show_history=true&id=" + row[COL_ID]);
        if (!StringUtils.isBlank(row[COL_BASIONYM])) {
            Matcher m = removeFamily.matcher(row[COL_BASIONYM]);
            if (m.find()) {
                writer.addCoreColumn(DwcTerm.originalNameUsage, m.replaceFirst(""));
            } else {
                writer.addCoreColumn(DwcTerm.originalNameUsage, row[COL_BASIONYM]);
            }
        }
        writer.addCoreColumn(replacedSynonym, row[COL_REPL_SYNONYM]);
        writer.addCoreColumn(DwcTerm.taxonRemarks, row[COL_REMARKS]);
    }

    @Override
    protected void addMetadata() {
        // metadata
        dataset.setTitle(TITLE);
        dataset.setHomepage(HOMEPAGE);
        dataset.setLogoUrl(LOGO);
        dataset.setPubDate(new Date());
        //setCitation(CITATION);
        //dataset.setDescription(DESCRIPTION);
        //addContact(CONTACT_ORG, CONTACT_EMAIL);
    }

}
