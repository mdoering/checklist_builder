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

import org.gbif.api.vocabulary.DatasetType;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.InputSource;

public class ArchiveBuilder extends AbstractBuilder {

    private static final String WEBAPGE = "http://www.invasivespecies.net/database/species/List.asp";
    private static final String LINK_BASE = "http://www.invasivespecies.net/database/species/";
    private static final String ENCODING = "latin1";

    @Inject
    public ArchiveBuilder(CliConfiguration cfg) {
        super(DatasetType.CHECKLIST, cfg);
    }

    protected void parseData() throws IOException {
        // get webapge
        LOG.info("Downloading latest invasives webpage from {}", WEBAPGE);

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
            LOG.error("Cannot process page", e);
        }
    }

    protected void addMetadata() {
        dataset.setTitle("Global Invasive Species Database");
        dataset.setDescription(
                "The Global Invasive Species Database is a free, online searchable source of information about species that negatively impact biodiversity. The GISD aims to increase public awareness about invasive species and to facilitate effective prevention and management activities by disseminating specialist’s knowledge and experience to a broad global audience. It focuses on invasive alien species that threaten native biodiversity and covers all taxonomic groups from micro-organisms to animals and plants.");
        dataset.setHomepage(uri("http://www.invasivespecies.net"));
        dataset.setLogoUrl(uri("http://www.issg.org/picts/issg_logo.gif"));
        addContact(null, "Michael", "Browne", "mtjbro@XTRA.CO.NZ");
    }

}
