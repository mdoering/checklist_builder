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
package de.doering.dwca.iocwbn;

import org.gbif.api.vocabulary.DatasetType;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ArchiveBuilder extends AbstractBuilder {
    private static final String XML_DOWNLOAD = "http://www.worldbirdnames.org/master_ioc-names_xml.xml";
    private static final String ENCODING = "UTF-8";
    // metadata
    private static final String VERSION = "3.4";
    private static final String HOMEPAGE = "http://www.worldbirdnames.org";
    private static final String LOGO = "http://www.worldbirdnames.org/img/hdr7.jpg";
    private static final String CONTACT_FIRSTNAME = "Frank";
    private static final String CONTACT_LASTNAME = "Gill";
    private static final String LICENSE = "Creative Commons Attribution 3.0 Unported License";
    private static final String TITLE = "IOC World Bird List, version " + VERSION;
    private static final String DESCRIPTION = "<p>The initial goal of the IOC World Bird List project was to compile a set of unique English-language names for the extant species of the birds of the world.</p>\n"
            + "<p>Launched initially in 1990 and restarted in 1994 after the loss of our colleague Burt L. Monroe Jr,  the project took 15 years to complete. All participants gave freely and generously of their valuable time and resources. This was a volunteer effort of the ornithological community on behalf of the International Ornithological Congress (IOC).</p>\n"
            + "<p>The first product – a list of  English names  in Birds of the World, Recommended English Names (Princeton University Press, 2006) – was based on a consensus of leading ornithologists worldwide and conformed to standard rules of construction, namely</p>\n"
            + "<ul>\n" + " <li>based on explicit guidelines and spelling rules</li>\n"
            + " <li>selected to involve minimal use of hyphens for group names</li>\n"
            + " <li>anglicized without glottal stops, accents, and the like</li>\n"
            + " <li>based on interregional agreement and global consensus, with compromises</li>\n"
            + " <li>selected with deference to long-established names</li>\n"
            + " <li>aligned with current species taxonomy</li>\n"
            + " <li>available for general adoption</li>\n"
            + " <li>sponsored and endorsed by the IOC and by committee members</li>\n"
            + "</ul>\n"
            + "The project continues as a work in progress that compiles the taxonomy of world birds as well as their English names</p>\n"
            + "<p>Frank Gill, David Donsker &  Minturn Wright, April 2007, 2012</p>\n"
            + "<p>Co-chairs, IOC Standing Committee on English Names</p>";

    @Inject
    public ArchiveBuilder(CliConfiguration cfg) {
        super(DatasetType.CHECKLIST, cfg);
    }

    @Override
    protected void parseData() throws IOException, SAXException, ParserConfigurationException {
        // get xml data
        LOG.info("Downloading latest IOC world bird list from {}", XML_DOWNLOAD);

        HttpGet get = new HttpGet(XML_DOWNLOAD);

        // execute
        HttpResponse response = client.execute(get);
        HttpEntity entity = response.getEntity();
        // parse page
        SAXParserFactory factory = SAXParserFactory.newInstance();

        final SAXParser parser = factory.newSAXParser();
        IocXmlHandler handler = new IocXmlHandler(writer, dataset);
        try {
            Reader reader = new InputStreamReader(entity.getContent(), ENCODING);
            parser.parse(new InputSource(reader), handler);
        } catch (Exception e) {
            LOG.error("Cannot process IOC XML", e);
        }
    }

    @Override
    protected void addMetadata() {
        dataset.setTitle(TITLE);
        dataset.setDescription(DESCRIPTION);
        dataset.setHomepage(uri(HOMEPAGE));
        dataset.setLogoUrl(uri(LOGO));
        addContact(CONTACT_FIRSTNAME, CONTACT_LASTNAME);
        dataset.setRights(LICENSE);
    }

}
