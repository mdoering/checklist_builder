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
package de.doering.dwca.ictv;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.utils.file.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ArchiveBuilder extends AbstractBuilder {
  // to be updated manually to current version !!!
  private static final String DOWNLOAD = "https://talk.ictvonline.org/files/master-species-lists/m/msl/7185/download";
  private static final File FILE = new File("/Users/markus/Downloads/ICTV Master Species List 2016 v1.3.xlsx");
  private static final String PUBDATE = "2017-05-25";
  private static final String VERSION = "2016 v1.3";

  // metadata
  private static final String ORG = " International Committee on Taxonomy of Viruses (ICTV)";
  private static final String CONTACT_EMAIL = "info@ictvonline.org";
  private static final String NOM_CODE = "ICTV";

  // SPREADSHEET FORMAT
  private static final int SHEET_IDX = 2;
  private static final int SKIP_ROWS = 1;
  // Order	Family	Subfamily	Genus	Species	Type Species?	Exemplar Accession Number	Exemplar Isolate	Genome Composition	Last Change	MSL of Last Change	Proposal	Taxon History URL
  private static final int COL_ORDER = 0;
  private static final int COL_FAMILY = 1;
  private static final int COL_GENUS = 3;
  private static final int COL_SCI_NAME = 4;

  private static final int COL_TYPE_SPECIES = 5;
  private static final int COL_GENBANK_ACCESSION = 6;
  private static final int COL_COMPOSITION = 8;
  private static final int COL_LINK = 12;

  @Inject
  public ArchiveBuilder(CliConfiguration cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  protected void parseData() throws IOException, InvalidFormatException {
    // get excel sheet
    parseData(downloadXls());
    //parseData(FILE);
  }

  private File downloadXls() throws IOException, InvalidFormatException {
    // get excel sheet
    LOG.info("Downloading latest data from {}", DOWNLOAD);

    // download xls
    final File xls = FileUtils.createTempDir();
    xls.deleteOnExit();
    http.download(DOWNLOAD, xls);

    return xls;
  }

  // parse XLS
  private void parseData(File xls) throws IOException, InvalidFormatException {

    Workbook wb = WorkbookFactory.create(xls);
    Sheet sheet = wb.getSheetAt(SHEET_IDX);
    int rows = sheet.getPhysicalNumberOfRows();
    LOG.info("{} rows found in excel sheet", rows);

    Set<String> genera = Sets.newHashSet();
    Iterator<Row> iter = sheet.rowIterator();
    while (iter.hasNext()) {
      Row row = iter.next();
      if (row.getRowNum()+1 <= SKIP_ROWS) continue;

      String name = col(row, COL_SCI_NAME);
      if (Strings.isNullOrEmpty(name)) continue;

      writer.newRecord(name);
      final String genus = col(row, COL_GENUS);
      writer.addCoreColumn(DwcTerm.kingdom, "Viruses");
      writer.addCoreColumn(DwcTerm.order, col(row, COL_ORDER));
      writer.addCoreColumn(DwcTerm.family, col(row, COL_FAMILY));
      writer.addCoreColumn(DwcTerm.genus, genus);
      writer.addCoreColumn(DwcTerm.scientificName, col(row, COL_SCI_NAME));
      writer.addCoreColumn(DwcTerm.taxonRank, "species");
      writer.addCoreColumn(DwcTerm.nomenclaturalCode, NOM_CODE);
      writer.addCoreColumn(DcTerm.references, col(row, COL_LINK));
      writer.addCoreColumn(DwcTerm.taxonRemarks, col(row, COL_COMPOSITION));

      boolean isType = toBool(col(row, COL_TYPE_SPECIES));
      if (isType && !genera.contains(genus)) {
        // there are a few cases when a genus has 2 type species listed :(
        genera.add(genus);

        // also create a genus record with this type species
        writer.newRecord(genus);
        writer.addCoreColumn(DwcTerm.kingdom, "Viruses");
        writer.addCoreColumn(DwcTerm.order, col(row, COL_ORDER));
        writer.addCoreColumn(DwcTerm.family, col(row, COL_FAMILY));
        writer.addCoreColumn(DwcTerm.scientificName, genus);
        writer.addCoreColumn(DwcTerm.taxonRank, "genus");
        writer.addCoreColumn(DwcTerm.nomenclaturalCode, NOM_CODE);

        Map<Term, String> ext = Maps.newHashMap();
        ext.put(DwcTerm.typeStatus, "type species");
        ext.put(DwcTerm.scientificName, name);
        ext.put(DwcTerm.taxonRank, "species");
        writer.addExtensionRecord(GbifTerm.TypesAndSpecimen, ext);
      }
    }
  }

  private boolean toBool(String x) {
    return x != null && x.trim().equals("1");
  }

  @Override
  protected void addMetadata() {
    // metadata
    dataset.setTitle("ICTV Master Species List " + VERSION);
    dataset.setDescription("Official lists of all ICTV-approved taxa.\n" +
        "\n" +
        "The creation or elimination, (re)naming, and (re)assignment of a virus species, genus, (sub)family, or order are all taxonomic acts that require public scrutiny and debate, leading to formal approval by the full membership of the ICTV. " +
        "In contrast, the naming of a virus isolate and its assignment to a pre-existing species are not considered taxonomic acts and therefore do not require formal ICTV approval. " +
        "Instead they will typically be accomplished by publication of a paper describing the virus isolate in the peer-reviewed virology literature.\n" +
        "\n" +
        "Descriptions of virus satellites, viroids and the agents of spongiform encephalopathies (prions) of humans and several animal and fungal species are included.\n"
    );
    dataset.setHomepage(uri("http://www.ictvonline.org/virusTaxInfo.asp"));
    dataset.setLogoUrl(uri("https://raw.githubusercontent.com/mdoering/checklist_builder/master/src/main/resources/ictv/ictv-logo.png"));
    setPubDate(PUBDATE);
    addExternalData(DOWNLOAD, null);
    addContact(ORG, CONTACT_EMAIL);
  }
}
