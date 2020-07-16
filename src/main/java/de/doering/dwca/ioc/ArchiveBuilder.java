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
package de.doering.dwca.ioc;

import org.gbif.api.vocabulary.ContactType;
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
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ArchiveBuilder extends AbstractBuilder {
  public static final String XML_DOWNLOAD = "https://www.worldbirdnames.org/master_ioc-names_xml.xml";
  public static final String ENCODING = "UTF-8";
  // metadata
  public static final String HOMEPAGE = "https://www.worldbirdnames.org";
  public static final String LOGO = "https://www.worldbirdnames.org/img/hdr7.jpg";
  public static final String CONTACT_ORG = "IOC World Bird List";
  private static final String CONTACT_EMAIL = "worldbirdnames@gmail.com";
  public static final String LICENSE = "Creative Commons Attribution 3.0 Unported License";
  private static final String TITLE = "IOC World Bird List, v";
  private static final String DESCRIPTION = "The IOC World Bird List is an open access resource of the international community of ornithologists. " +
      "Our goal is to facilitate worldwide communication in ornithology and conservation based on an  up-to-date classification of world birds and a set of English names that follows explicit guidelines for spelling and construction (Gill & Wright 2006).\n" +
      "\n" +
      "To keep up with the active industry of taxonomic revisions, the IOC editorial team and advisors update the web-based list quarterly.  " +
      "The updates include changes of recommended names or classification, additions of newly described species, corrections of nomenclature, and updates of species taxonomy.\n" +
      "\n" +
      "The IOC World Bird List complements three other primary world bird lists that differ slightly in their primary goals and taxonomic philosophy, i.e. The Clements Checklist of the Birds of the World, The Howard & Moore Complete Checklist of the Birds of the World, 4th Edition, and HBW Alive/Bird Life International.  " +
      "Improved alignment of these independent taxonomic works is a goal of the newly structured International Ornithologists Union, including a Round Table discussion at the 2018 meeting in Vancouver, British Columbia.\n" +
      "\n" +
      "Special thanks always to our expert advisors (left panel), to Sally Conyne for compiling Ranges, to Eng-Li Green for website management, to Larry Master and Colin Campbell for photos, to Peter Kovalik for spreadsheet magic, and to all volunteer participants. We welcome your corrections and your suggestions for improvement.  " +
      "You can reach us at worldbirdnames@gmail.com.";

  @Inject
  public ArchiveBuilder(CliConfiguration cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  @Override
  protected void parseData() throws IOException, SAXException, ParserConfigurationException {
    // get xml data
    LOG.info("Downloading latest IOC world bird list from {}", XML_DOWNLOAD);

    // parse page
    SAXParserFactory factory = SAXParserFactory.newInstance();

    final SAXParser parser = factory.newSAXParser();
    IocXmlHandler handler = new IocXmlHandler(writer);

    try {
      // execute
      HttpGet get = new HttpGet(XML_DOWNLOAD);
      HttpResponse response = http.execute(get);

      Reader reader = new InputStreamReader(response.getEntity().getContent(), ENCODING);
      parser.parse(new InputSource(reader), handler);

      setPubDate(handler.getYear());
      dataset.setTitle(TITLE + handler.getVersion());

    } catch (Exception e) {
      LOG.error("Cannot process IOC XML", e);
    }
  }

  @Override
  protected void addMetadata() {
    dataset.setDescription(DESCRIPTION);
    dataset.setHomepage(uri(HOMEPAGE));
    dataset.setLogoUrl(uri(LOGO));
    addContact(CONTACT_ORG, CONTACT_EMAIL);
    addContactPerson("Frank", "Frank", ContactType.EDITOR);
    addContactPerson("David", "Donsker", ContactType.EDITOR);
    dataset.setRights(LICENSE);
  }

}
