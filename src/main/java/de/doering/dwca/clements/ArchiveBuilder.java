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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.gbif.api.model.registry.Citation;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;

import java.io.InputStream;
import java.net.URI;
import java.text.DateFormatSymbols;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArchiveBuilder extends AbstractBuilder {

    private Pattern cleanFamily = Pattern.compile("^([^ ,(]+)");
    private static final String DOWNLOAD = "https://www.birds.cornell.edu/clementschecklist/wp-content/uploads/{YEAR}/%02d/Clements-Checklist-v{YEAR}-{MONTH_NAME}-{YEAR}.xlsx";
    // metadata
    private static final String TITLE = "The Clements Checklist";
    private static final URI HOMEPAGE = URI.create("https://www.birds.cornell.edu/clementschecklist");
    private static final String CITATION = "Clements, J. F., T. S. Schulenberg, M. J. Iliff, D. Roberson, T. A. Fredericks, B. L. Sullivan, and C. L. Wood. {YEAR}. The eBird/Clements checklist of birds of the world: v{YEAR}. Downloaded from http://www.birds.cornell.edu/clementschecklist/download/";
    private static final URI LOGO = null;
    private static final String DESCRIPTION = "The Clements Checklist of Birds of the World, 6th Edition was published and released by Cornell University Press in June 2007. The book was produced from a nearly completed manuscript left by James Clements upon his death in 2005.\n" +
            "\n" +
            "The Cornell Lab of Ornithology has accepted the job of maintaining the ever-changing list of species, subspecies, English names, and approximate distributions, beginning with publication of the 6th Edition. Our procedures for accomplishing this ongoing task include using the considerable expertise of our research ornithologists on staff, aided enormously by input from knowledgeable professional and amateur cooperators worldwide. We invite input on known or suspected errors or updates at any time.\n" +
            "\n" +
            "This website serves as the clearinghouse for keeping your Clements Checklist up to date. We will post all corrections once a year in August. At the same time, we’ll post updates to the taxonomy, scientific and English nomenclature, and range descriptions, to incorporate changes that have made their way into the literature and are generally accepted by the appropriate scientific body or community. In the future, we will also be posting a list of alternative English names.";
    private static final String CONTACT_ORG = "Cornell Lab of Ornithology";
    private static final String CONTACT_EMAIL = "cornellbirds@cornell.edu";

    // sort v2017	Clements v2017 change	text for website v2017	category	English name	scientific name	range	order	family	extinct	extinct year	sort v2016	page 6.0
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
    // reference sheet
    private static final int COL_REF_ABBREV = 0;
    private static final int COL_REF_AUTHOR = 2;
    private static final int COL_REF_YEAR = 3;
    private static final int COL_REF_TITLE = 4;
    private static final int COL_REF_JOURNAL = 5;

    @Inject
    public ArchiveBuilder(CliConfiguration cfg) {
        super(DatasetType.CHECKLIST, cfg);
    }

    /**
     * We prefer the excel sheet over the CSV file as the CSV contains bad encodings for some characters of the
     * distribution area names.
     */
    @Override
    protected void parseData() throws Exception {
        // find latest clements list
        // recently these have been published annually in august only, but there have been different month before
        // in case we dont find a list for this month we get a 404 and exit
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

        // download excel
        LOG.info("Downloading latest data from {}", url);
        InputStream in = http.getStream(url);
        if (in == null) {
            throw new IllegalStateException("Unable to download Clements XLS from " + url);
        }
        Workbook wb = WorkbookFactory.create(in);
        Sheet taxa = wb.getSheetAt(0);
        LOG.info("{} taxa found in excel sheet", taxa.getPhysicalNumberOfRows());

        // parse rows
        Iterator<Row> iter = taxa.rowIterator();
        while (iter.hasNext()) {
            Row row = iter.next();
            String id = col(row, COL_ID);
            if (StringUtils.isBlank(id)) {
                LOG.warn("Suspicous row with empty id, ignore line {}", row.getRowNum());
                continue;
            }
            writer.newRecord(id);
            writer.addCoreColumn(DwcTerm.scientificName, col(row, COL_NAME));
            writer.addCoreColumn(DwcTerm.taxonRank, col(row, COL_RANK));
            writer.addCoreColumn(DwcTerm.kingdom, "Animalia");
            writer.addCoreColumn(DwcTerm.class_, "Aves");
            writer.addCoreColumn(DwcTerm.order, col(row, COL_ORDER));
            if (!Strings.isNullOrEmpty(col(row, COL_FAMILY))) {
                Matcher m = cleanFamily.matcher(col(row, COL_FAMILY));
                if (m.find()) {
                    writer.addCoreColumn(DwcTerm.family, m.group());
                }
            }
            writer.addCoreColumn(DwcTerm.taxonRemarks, col(row, COL_REMARKS));

            Map<Term, String> data = new HashMap<Term, String>();
            data.put(DwcTerm.vernacularName, col(row, COL_EN_NAME));
            data.put(DcTerm.language, "en");
            writer.addExtensionRecord(GbifTerm.VernacularName, data);

            data = new HashMap<Term, String>();
            data.put(DcTerm.description, col(row, COL_RANGE));
            data.put(DcTerm.type, "Distribution");
            writer.addExtensionRecord(GbifTerm.Description, data);

            // extinct
            data = new HashMap<Term, String>();
            data.put(GbifTerm.isExtinct, "1".equalsIgnoreCase(Strings.nullToEmpty(col(row, COL_EXTINCT))) ? "true" : "false");
            if (!Strings.isNullOrEmpty(col(row, COL_EXTINCT_YEAR))) {
                data.put(GbifTerm.livingPeriod, "Recent until " + col(row, COL_EXTINCT_YEAR));
            }
            writer.addExtensionRecord(GbifTerm.SpeciesProfile, data);
        }

        // parse references and add to EML bibliography
        Sheet refs = wb.getSheetAt(1);
        LOG.info("{} references found in excel sheet", refs.getPhysicalNumberOfRows());
        iter = refs.rowIterator();
        while (iter.hasNext()) {
            Row row = iter.next();
            String abbrev = col(row, COL_REF_ABBREV);
            if (StringUtils.isBlank(abbrev)) {
                LOG.warn("Suspicous reference with empty citation abbreviation, ignore line {}", row.getRowNum());
                continue;
            }
            String refCitation = buildCitation(col(row, COL_REF_AUTHOR), col(row, COL_REF_YEAR), col(row, COL_REF_TITLE), col(row, COL_REF_JOURNAL));
            if (!StringUtils.isBlank(refCitation)) {
                Citation c = new Citation();
                c.setText(refCitation);
                c.setIdentifier(link(row, COL_REF_TITLE));
                dataset.getBibliographicCitations().add(c);
            }
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
