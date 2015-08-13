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

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;

public class ArchiveBuilder extends AbstractBuilder {

    private Pattern removeFamily = Pattern.compile("^ *[A-Z][a-z]+ ");
    // metadata
    private static final String TITLE = "The International Plant Names Index";
    private static final URI HOMEPAGE = URI.create("http://www.ipni.org");
    private static final URI LOGO = URI.create("http://www.ipni.org/images/ipni_logoonly_sm.jpg");

    // for the classic delimited output
    public static final int COL_ID = 0;
    public static final int COL_FAMILY = 2;
    public static final int COL_BASIONYM = 24;
    public static final int COL_REPL_SYNONYM = 25;
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
        IPNICrawler crawler = new IPNICrawler(client, dwcaDir, testFamilies);
        crawler.run();
    }

    /**
     * We do not want to use the dwca writer, so we override the base class run method instead
     */
    @Override
    public void run() {
        try {
            dwcaDir = cfg.archiveDir();

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
        dataset.setHomepage(HOMEPAGE);
        dataset.setLanguage(Language.ENGLISH);
        dataset.setLogoUrl(LOGO);
        dataset.setPubDate(new Date());
        //setCitation(CITATION);
        //dataset.setDescription(DESCRIPTION);
        //addContact(CONTACT_ORG, CONTACT_EMAIL);
        Writer writer = new FileWriter(new File(dwcaDir, "eml.xml"));
        EMLWriter.write(dataset, writer);
    }

}
