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

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.Language;
import org.gbif.registry.metadata.EMLWriter;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.Date;
import java.util.Set;
import java.util.regex.Pattern;

public class ArchiveBuilder extends AbstractBuilder {

    private Pattern removeFamily = Pattern.compile("^ *[A-Z][a-z]+ ");
    // metadata
    private static final String TITLE = "The International Plant Names Index";
    private static final URI HOMEPAGE = URI.create("http://www.ipni.org");
    private static final URI LOGO = URI.create("http://www.ipni.org/images/logo/ipni_logo_175.png");
    private static final String DESCRIPTION = "The International Plant Names Index (IPNI) is a database of the names and associated basic bibliographical details of seed plants, ferns and lycophytes. Its goal is to eliminate the need for repeated reference to primary sources for basic bibliographic information about plant names. The data are freely available and are gradually being standardized and checked. IPNI is a dynamic resource, depending on direct contributions by all members of the botanical community.\n" +
            "IPNI is the product of a collaboration between The Royal Botanic Gardens, Kew, The Harvard University Herbaria, and the Australian National Herbarium.\n" +
            "The records in IPNI come from three sources: the Index Kewensis (IK), the Gray Card Index (GCI) and the Australian Plant Names Index (APNI).\n" +
            "Over one million records have come from Index Kewensis. This is global in coverage and lists names from the first edition of Linnaeus's Species Plantarum to those being published now. Basic bibliographic details are included for each name and for later records the year of publication is also included. Until 1971 infraspecific names were not included – while the Gray Index and the Australian Plant Names provide some of the infraspecific names which are lacking in IK but their coverage is not so extensive, so many names below species level are missing from IPNI. The current electronic version of the Index Kewensis was produced by an optical reading process in the mid 1980s. Despite careful checking of the scanned data, many errors were introduced into the Index at this time.\n" +
            "Over 350,000 records have come from the Gray Index (originally the Gray Herbarium Card Index) which includes names for New World taxa published on or after January 1886. Basic bibliographic details and date are included for each name and many records include information about types. The data were converted to electronic form in the early 1990s and much time has been invested in their standardization and verification since then. Although most of the citations in the Gray Index are for names also recorded in Index Kewensis, there are many records for New World infraspecific names which are unique to the Gray Index. The duplicate records are also of considerable value since they tend to be more detailed than the equivalent IK record and also because errors in either index can be detected by comparing records for the same name. Furthermore the Gray Index includes many records of typifications subsequent to the time of validation of a name (epi-, lecto- and neo-typifications) which are not duplicated elsewhere.\n" +
            "Over 63,000 records have come from the Australian Plant Names Index which has been compiled since 1973 and includes all scientific names used in the literature for Australian vascular plants. Levels of detail and validation are higher for this index than for the IK or the GI. Most names have been checked back to their original place of publication and type information is included for each name. APNI includes many names not included in the IK, especially of Australian infraspecific taxa, and, as in the case of the Gray Index, overlap in coverage between IK and APNI offers scope for checking data and eliminating errors.\n" +
            "Index Kewensis includes only the names of seed plants. Index Filicum, covering the ferns (and incorporating lycophytes published after 1960), is now included as of 2004. The Gray Index names include vascular plants of the New World. The Australian Plant Name Index records names for all Australian plants but its contribution to IPNI is restricted to the flowering plants, the ferns and their allies.\n" +
            "Thus while there is considerable overlap in content between the three indices they are in many ways complementary. IPNI, the product of a merger between these three, represents the most comprehensive listing of plant names available today. A number of editorial processes are planned to improve the quality of the data over the coming years. These include deduplication, standardisation and verification.";
    private static final String KEW = "Royal Botanic Gardens, Kew";
    private static final String EMAIL = "ipnifeedback@ipni.org";
    private final Set<String> testFamilies = Sets.newHashSet();
    private File dwcaDir;

    @Inject
    public ArchiveBuilder(CliConfiguration cfg) {
        super(DatasetType.CHECKLIST, cfg);
    }

    public ArchiveBuilder(CliConfiguration cfg, String ... families) {
        super(DatasetType.CHECKLIST, cfg);
        for (String f : families) {
            testFamilies.add(f);
        }
    }

    @Override
    protected void parseData() throws Exception {
        IPNICrawler crawler = new IPNICrawler(http, dwcaDir, testFamilies);
        crawler.run();
    }

    /**
     * We do not want to use the dwca writer, so we override the base class run method instead
     */
    @Override
    public void run() {
        try {
            dwcaDir = cfg.archiveDir();
            org.apache.commons.io.FileUtils.forceMkdir(dwcaDir);

            parseData();
            addMetadata();
            addMetaXml();

            // compress
            LOG.info("Bundling archive at {}", dwcaDir.getAbsolutePath());
            File zip = new File(dwcaDir.getParentFile(), dwcaDir.getName()+".zip");
            CompressionUtil.zipDir(dwcaDir, zip);
            LOG.info("Dwc archive completed at {} !", zip);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds static meta.xml from resources.
     */
    private void addMetaXml() throws IOException {
        FileUtils.copyStreamToFile(FileUtils.classpathStream("ipni/meta.xml"), new File(dwcaDir, "meta.xml"));
    }

    @Override
    protected void addMetadata() throws IOException {
        // metadata
        dataset.setTitle(TITLE);
        dataset.setDescription(DESCRIPTION);
        dataset.setHomepage(HOMEPAGE);
        dataset.setLanguage(Language.ENGLISH);
        dataset.setLogoUrl(LOGO);
        dataset.setPubDate(new Date());
        //setCitation(CITATION);
        addContact(KEW, EMAIL);
        try (Writer writer = new FileWriter(new File(dwcaDir, "eml.xml"))) {
            EMLWriter.write(dataset, writer);
        }
        // write constituent metadata
        org.apache.commons.io.FileUtils.forceMkdir(new File(dwcaDir, "dataset"));
        for (IPNICrawler.SOURCE src : IPNICrawler.SOURCE.values()) {
            String fname = src.name()+".xml";
            FileUtils.copyStreamToFile(FileUtils.classpathStream("ipni/"+fname), new File(dwcaDir, "dataset/"+fname));
        }
    }

}
