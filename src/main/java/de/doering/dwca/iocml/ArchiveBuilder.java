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
package de.doering.dwca.iocml;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import de.doering.dwca.ioc.IocXmlHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.methods.HttpGet;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.Language;
import org.gbif.common.parsers.LanguageParser;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.xml.sax.InputSource;

import javax.annotation.Nullable;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;

public class ArchiveBuilder extends AbstractBuilder {
  // to be updated manually to current version !!!
  private static final String DOWNLOAD = "http://www.worldbirdnames.org/Multiling%20IOC%206.3.xlsx";
  private static final File FILE = new File("/Users/markus/Downloads/Multiling IOC 6.3.xlsx");

  // metadata
  private static final String TITLE = "Multilingual IOC World Bird List, v";
  private static final String DESCRIPTION = "The IOC World Bird List is an open access resource of the international community of ornithologists.";
  private static final String CONTACT_FIRST = "Peter";
  private static final String CONTACT_LAST = "Kovalik";
  private static final String EMAIL = "bbokovalik@gmail.com";

  // SPREADSHEET FORMAT
  private static final int SHEET_IDX = 0;
  private static final int FLATTEN_ROWS = 3;
  private static final Map<Integer, Language> LANG_COLS = Maps.newHashMap();
  private static final int COL_ORDER = 1;
  private static final int COL_FAMILY = 2;
  private static final int COL_NAME = 3;

  private int columns = -1;

  @Inject
  public ArchiveBuilder(CliConfiguration cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  protected void parseData() throws Exception {
    // get excel sheet
    parseData(downloadXls());
    //parseData(FILE);
  }

  private void parseXmlMetadata() {
    // get xml data
    LOG.info("Parse metadata from latest IOC world bird list at {}", de.doering.dwca.ioc.ArchiveBuilder.XML_DOWNLOAD);

    // parse page
    SAXParserFactory factory = SAXParserFactory.newInstance();

    try {
      final SAXParser parser = factory.newSAXParser();
      IocXmlHandler handler = new IocXmlHandler(null);
      // execute
      HttpGet get = new HttpGet(de.doering.dwca.ioc.ArchiveBuilder.XML_DOWNLOAD);
      HttpResponse response = http.execute(get);

      Reader reader = new InputStreamReader(response.getEntity().getContent(), de.doering.dwca.ioc.ArchiveBuilder.ENCODING);
      parser.parse(new InputSource(reader), handler);

      setPubDate(handler.getYear());
      dataset.setTitle(TITLE + handler.getVersion());

    } catch (Exception e) {
      LOG.error("Cannot process IOC XML", e);
    }
  }

  private File downloadXls() throws IOException, AuthenticationException, InvalidFormatException {
    // get excel sheet
    LOG.info("Downloading latest data from {}", DOWNLOAD);

    // download xls
    final File xls = File.createTempFile("ioc", "dwca");
    xls.deleteOnExit();
    http.download(DOWNLOAD, xls);

    return xls;
  }

  private String[] flattenedRow(Iterator<Row> iter, @Nullable Row first) {
    if (first == null) {
      first = iter.next();
    }
    String[] cols = new String[first.getLastCellNum()];

    for (int seed = 0; seed < FLATTEN_ROWS; seed++) {
      Row row = seed==0 ? first : iter.next();
      int idx = COL_NAME + seed;
      while (idx < row.getLastCellNum()) {
        cols[idx] = col(row, idx);
        idx += FLATTEN_ROWS;
      }
    }
    return cols;
  }

  // parse XLS
  private void parseData(File xls) throws IOException, InvalidFormatException {

    Workbook wb = WorkbookFactory.create(xls);
    Sheet sheet = wb.getSheetAt(SHEET_IDX);
    int rows = sheet.getPhysicalNumberOfRows();
    LOG.info("{} rows found in excel sheet", rows);

    Iterator<Row> iter = sheet.rowIterator();

    // header
    String[] header = flattenedRow(iter, null);
    parseHeader(header);

    String order = null;
    String family = null;
    while (iter.hasNext()) {
      Row row = iter.next();
      if (!Strings.isNullOrEmpty(col(row, COL_ORDER))) {
        order = StringUtils.capitalize(col(row, COL_ORDER));

      } else if (!Strings.isNullOrEmpty(col(row, COL_FAMILY))) {
        family = col(row, COL_FAMILY);

      } else {
        // species
        String[] cols = flattenedRow(iter, row);
        String name = col(row, COL_NAME);
        writer.newRecord(name);
        writer.addCoreColumn(DwcTerm.kingdom, "Animalia");
        writer.addCoreColumn(DwcTerm.order, order);
        writer.addCoreColumn(DwcTerm.family, family);
        writer.addCoreColumn(DwcTerm.scientificName, name);
        writer.addCoreColumn(DwcTerm.taxonRank, "species");
        // vernacular names
        for (Map.Entry<Integer, Language> entry : LANG_COLS.entrySet()) {
          String vname = cols[entry.getKey()];
          if (StringUtils.isBlank(vname)) continue;

          Map<Term, String> ext = Maps.newHashMap();
          ext.put(DcTerm.language, entry.getValue().getIso3LetterCode());
          ext.put(DwcTerm.vernacularName, vname);
          writer.addExtensionRecord(GbifTerm.VernacularName, ext);
        }
      }
    }
  }

  private void parseHeader(String[] header) {
    int idx = 0;
    for (String val : header) {
      if (idx != COL_NAME && !Strings.isNullOrEmpty(val)) {
        Language lang = LanguageParser.getInstance().parse(val).getPayload();
        if (lang == null) {
          LOG.error("Cannot parse header language {}", val);
        } else {
          LANG_COLS.put(idx, lang);
        }
      }
      idx++;
    }
  }

  @Override
  protected void addMetadata() {
    // metadata
    dataset.setDescription(DESCRIPTION);
    dataset.setHomepage(uri(de.doering.dwca.ioc.ArchiveBuilder.HOMEPAGE));
    dataset.setLogoUrl(uri(de.doering.dwca.ioc.ArchiveBuilder.LOGO));
    dataset.setRights(de.doering.dwca.ioc.ArchiveBuilder.LICENSE);
    parseXmlMetadata();
    addExternalData(DOWNLOAD, null);
    addContact(null, CONTACT_FIRST, CONTACT_LAST, EMAIL);
  }
}
