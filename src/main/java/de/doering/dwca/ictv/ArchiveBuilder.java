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
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.BuilderConfig;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ArchiveBuilder extends AbstractBuilder {
  // to be updated manually to current version !!!
  // https://talk.ictvonline.org/files/master-species-lists/
  private static final int DOWNLOAD_KEY = 12314;
  private static final String PUBDATE = "2021-05-18";
  private static final String VERSION = "2020.v1";
  private static final String DOWNLOAD = "http://talk.ictvonline.org/files/master-species-lists/m/msl/"+DOWNLOAD_KEY+"/download";

  // metadata
  private static final String ORG = " International Committee on Taxonomy of Viruses (ICTV)";
  private static final String CONTACT_EMAIL = "info@ictvonline.org";
  private static final String NOM_CODE = "ICTV";

  // SPREADSHEET FORMAT
  private static final int SHEET_IDX = 2;
  private static final int SKIP_ROWS = 1;
  //	Realm	Subrealm	Kingdom	Subkingdom	Phylum	Subphylum	Class	Subclass	Order	Suborder	Family	Subfamily	Genus	Subgenus	Species	Type Species?	Genome Composition	Last Change	MSL of Last Change	Proposal for Last Change 	Taxon History URL
  private static final int COL_PHYLUM    = 5;
  private static final int COL_CLASS     = 7;
  private static final int COL_ORDER     = 9;
  private static final int COL_FAMILY    = 11;
  private static final int COL_GENUS     = 13;
  private static final int COL_SUBGENUS  = 14;
  private static final int COL_SPECIES   = 15;

  private static final int COL_TYPE_SPECIES = 16;
  private static final int COL_COMPOSITION = 17;
  private static final int COL_LINK = 22;

  public ArchiveBuilder(BuilderConfig cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  protected void parseData() throws Exception {
    // get excel sheet
    parseData(downloadXls());
    //parseData(new File("/Users/markus/Downloads/ICTV Master Species List 2018b.v2.xlsx"));
  }

  private File downloadXls() throws Exception {
    // get excel sheet
    LOG.info("Downloading latest data from {}", DOWNLOAD);

    // download xls
    final File xls = File.createTempFile("ictv", ".xls");
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
  
      String name = col(row, COL_SPECIES);
      if (Strings.isNullOrEmpty(name)) continue;

      writer.newRecord(name);
      final String genus = col(row, COL_GENUS);
      writer.addCoreColumn(DwcTerm.kingdom, "Viruses");
      writer.addCoreColumn(DwcTerm.phylum,   col(row, COL_PHYLUM));
      writer.addCoreColumn(DwcTerm.class_,   col(row, COL_CLASS));
      writer.addCoreColumn(DwcTerm.order,    col(row, COL_ORDER));
      writer.addCoreColumn(DwcTerm.family,   col(row, COL_FAMILY));
      writer.addCoreColumn(DwcTerm.genus, genus);
      writer.addCoreColumn(DwcTerm.subgenus, col(row, COL_SUBGENUS));
      writer.addCoreColumn(DwcTerm.scientificName, col(row, COL_SPECIES));
      writer.addCoreColumn(DwcTerm.taxonRank, "species");
      writer.addCoreColumn(DwcTerm.nomenclaturalCode, NOM_CODE);
      writer.addCoreColumn(DcTerm.references, link(row, COL_LINK));
      writer.addCoreColumn(DwcTerm.taxonRemarks, col(row, COL_COMPOSITION));

      boolean isType = toBool(col(row, COL_TYPE_SPECIES));
      if (isType && !genera.contains(genus)) {
        // there are a few cases when a genus has 2 type species listed :(
        genera.add(genus);

        // also create a genus record with this type species
        writer.newRecord(genus);
        writer.addCoreColumn(DwcTerm.kingdom, "Viruses");
        writer.addCoreColumn(DwcTerm.phylum,   col(row, COL_PHYLUM));
        writer.addCoreColumn(DwcTerm.class_,   col(row, COL_CLASS));
        writer.addCoreColumn(DwcTerm.order,    col(row, COL_ORDER));
        writer.addCoreColumn(DwcTerm.family,   col(row, COL_FAMILY));
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
    dataset.setHomepage(uri("https://talk.ictvonline.org/taxonomy/w/ictv-taxonomy"));
    dataset.setLogoUrl(uri("https://raw.githubusercontent.com/mdoering/checklist_builder/master/src/main/resources/ictv/ictv-logo.png"));
    setPubDate(PUBDATE);
    addExternalData(DOWNLOAD, null);
    addContact(ORG, CONTACT_EMAIL);
  }
}
