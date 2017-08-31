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
package de.doering.dwca.clements;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.InputStreamReader;
import java.net.URI;
import java.text.DateFormatSymbols;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArchiveBuilder extends AbstractBuilder {

    private Pattern cleanFamily = Pattern.compile("^([^ ,(]+)");
    private static final String DOWNLOAD = "http://www.birds.cornell.edu/clementschecklist/wp-content/uploads/{YEAR}/%02d/Clements-Checklist-v{YEAR}-{MONTH_NAME}-{YEAR}.csv";
    private static final String ENCODING = "UTF-8";
    private static final String DELIMITER = ",";
    private static final Character QUOTES = '"';
    private static final Integer HEADER_ROWS = 1;
    // metadata
    private static final String TITLE = "The Clements Checklist";
    private static final URI HOMEPAGE = URI.create("http://www.birds.cornell.edu/clementschecklist");
    private static final String CITATION = "Clements, J. F., T. S. Schulenberg, M. J. Iliff, D. Roberson, T. A. Fredericks, B. L. Sullivan, and C. L. Wood. {YEAR}. The eBird/Clements checklist of birds of the world: v{YEAR}. Downloaded from http://www.birds.cornell.edu/clementschecklist/download/";
    private static final URI LOGO = null;
    private static final String DESCRIPTION = "The Clements Checklist of Birds of the World, 6th Edition was published and released by Cornell University Press in June 2007. The book was produced from a nearly completed manuscript left by James Clements upon his death in 2005.<br/>The Cornell Lab of Ornithology has accepted the job of maintaining the ever-changing list of species, subspecies, English names, and approximate distributions, beginning with publication of the 6th Edition. Our procedures for accomplishing this ongoing task include using the considerable expertise of our research ornithologists on staff, aided enormously by input from knowledgeable professional and amateur cooperators worldwide. We invite input on known or suspected errors or updates at any time.<br/>This web site serves as the clearinghouse for keeping your Clements Checklist up to date. We will post all corrections once a year in October. At the same time, we'll post updates to the taxonomy, scientific and English nomenclature, and range descriptions, to incorporate changes that have made their way into the literature and are generally accepted by the appropriate scientific body or community. In the future, we will also be posting a list of alternative English names.";
    private static final String CONTACT_ORG = "Cornell Lab of Ornithology";
    private static final String CONTACT_EMAIL = "cornellbirds@cornell.edu";

    // sort v2015,Clements v2015 change,Text for website v2015,Category,English name,Scientific name,Range,Order,Family,EXTINCT,EXTINCT_YEAR_CLEMENTS,sort 6.9,sort 6.8,PAGE 6.0
    // sort v2017,Clements v2017 change,text for website v2017,category,English name,scientific name,range,order,family,extinct,extinct year,sort v2016,page 6.0,,,,,,,,,,,
    private static final int COL_ID = 0;
    private static final int COL_REMARKS = 2;
    private static final int COL_RANK = 3;
    private static final int COL_EN_NAME = 4;
    private static final int COL_NAME = 5;
    private static final int COL_RANGE = 6;
    private static final int COL_ORDER = 7;
    private static final int COL_FAMILY = 8;
    private static final int COL_EXTINCT = 9;
    private static final int COL_EXTINCT_YEAR = 10;
    private static final int COL_MIN = COL_EXTINCT_YEAR +1;

    @Inject
    public ArchiveBuilder(CliConfiguration cfg) {
        super(DatasetType.CHECKLIST, cfg);
    }

    @Override
    protected void parseData() throws Exception {
        // update pubdate metadata
        Date today = new Date();
        int year = 1900 + today.getYear();
        int month = 1 + today.getMonth();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month-1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        dataset.setPubDate(cal.getTime());

        // get latest CSV
        DateFormatSymbols dfs = new DateFormatSymbols();
        String[] months = dfs.getMonths();
        String url = String.format(DOWNLOAD, month)
                .replace("{YEAR}", String.valueOf(year))
                .replace("{MONTH_NAME}", months[month - 1]);
        LOG.info("Downloading latest data from {}", url);
        HttpGet get = new HttpGet(url);

        // download
        CloseableHttpResponse response = client.execute(get);
        try {
            if (response.getStatusLine().getStatusCode() != 200) {
                LOG.error("Unable to download Clements CSV from {}: {}", url, response.getStatusLine());
                throw new IllegalStateException("Unable to download Clements CSV: " + response.getStatusLine().toString());
            }
            HttpEntity entity = response.getEntity();
            CsvListReader reader = new CsvListReader(new InputStreamReader(entity.getContent()), CsvPreference.EXCEL_PREFERENCE);

            // parse rows
            int line = 0;
            List<String> row;
            while ( (row = reader.read()) != null) {
                line++;
                if (line <= HEADER_ROWS) {
                    continue;
                }
                if (row.size() < COL_MIN) {
                    LOG.warn("Row with too little columns: {}", line);
                    continue;
                }
                String id = row.get(COL_ID);
                if (StringUtils.isBlank(id)) {
                    LOG.warn("Suspicous row with empty id, ignore line: {}", line);
                    continue;
                }
                writer.newRecord(id);
                writer.addCoreColumn(DwcTerm.scientificName, row.get(COL_NAME));
                writer.addCoreColumn(DwcTerm.taxonRank, row.get(COL_RANK));
                writer.addCoreColumn(DwcTerm.kingdom, "Animalia");
                writer.addCoreColumn(DwcTerm.class_, "Aves");
                writer.addCoreColumn(DwcTerm.order, row.get(COL_ORDER));
                if (!Strings.isNullOrEmpty(row.get(COL_FAMILY))) {
                    Matcher m = cleanFamily.matcher(row.get(COL_FAMILY));
                    if (m.find()) {
                        writer.addCoreColumn(DwcTerm.family, m.group());
                    }
                }
                writer.addCoreColumn(DwcTerm.taxonRemarks, row.get(COL_REMARKS));

                Map<Term, String> data = new HashMap<Term, String>();
                data.put(DwcTerm.vernacularName, row.get(COL_EN_NAME));
                data.put(DcTerm.language, "en");
                writer.addExtensionRecord(GbifTerm.VernacularName, data);

                data = new HashMap<Term, String>();
                data.put(DcTerm.description, row.get(COL_RANGE));
                data.put(DcTerm.type, "Distribution");
                writer.addExtensionRecord(GbifTerm.Description, data);

                // extinct
                data = new HashMap<Term, String>();
                data.put(GbifTerm.isExtinct, "1".equalsIgnoreCase(Strings.nullToEmpty(row.get(COL_EXTINCT))) ? "true" : "false");
                if (!Strings.isNullOrEmpty(row.get(COL_EXTINCT_YEAR))) {
                    data.put(GbifTerm.livingPeriod, "Recent until " + row.get(COL_EXTINCT_YEAR));
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
        setCitation(CITATION);
        dataset.setDescription(DESCRIPTION);
        dataset.setHomepage(HOMEPAGE);
        dataset.setLogoUrl(LOGO);
        addContact(CONTACT_ORG, CONTACT_EMAIL);
    }
}
