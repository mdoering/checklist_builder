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
package de.doering.dwca.dsmz;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.text.DwcaWriter;
import org.gbif.metadata.eml.Agent;
import org.gbif.metadata.eml.Eml;
import org.gbif.utils.HttpUtil;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChecklistBuilder {

  private static Logger LOG = LoggerFactory.getLogger(ChecklistBuilder.class);
  private DwcaWriter writer;
  private static final String VERSION = "July 2013";
  private static final String DOWNLOAD = "http://www.dsmz.de/fileadmin/Bereiche/ChiefEditors/BacterialNomenclature/DSMZ_bactnames.zip";
  // metadata
  private static final String TITLE = "Prokaryotic Nomenclature Up-to-date";
  private static final String HOMEPAGE = "http://www.dsmz.de/bacterial-diversity/prokaryotic-nomenclature-up-to-date.html";
  private static final String LANGUAGE = "en";
  private static final String LOGO = "http://www.dsmz.de/fileadmin/templates/gfx/logo.gif";
  private static final String DESCRIPTION = "\"Prokaryotic Nomenclature up-to-date\" is a compilation of all names of Bacteria and Archaea which have been validly published according to the Bacteriological Code since 1. Jan. 1980, and nomenclatural changes which have been validly published since. It will be updated with the publication of each new issue of the Int. J. Syst. Evol. Microbiol. (IJSEM). \"Prokaryotic Nomenclature up-to-date\" is published by the Leibniz-Institut DSMZ - Deutsche Sammlung von Mikroorganismen und Zellkulturen GmbH. This checklist is based on the complete list from " + VERSION;
  private static final String DSMZ_ORG = "Leibniz Institute DSMZ-German Collection of Microorganisms and Cell Cultures";
  private static final String CONTACT_FIRST_NAME = "Dorothea";
  private static final String CONTACT_LAST_NAME = "Gleim";
  private static final String CONTACT_EMAIL = "Dorothea.Gleim@dsmz.de";
  private static final List<String> CONTRIBUTORS = Lists.newArrayList("N. Weiss", "M. Kracht");

  //GENUS	SPECIES	SUBSPECIES	REFERENCE	STATUS	AUTHORS	REMARKS	RISK_GRP	TYPE_STRAINS	RECORD_NO	RECORD_LNK
  private static final int COL_ID = 9;
  private static final int COL_VALID_ID = 10;
  private static final int COL_GENUS = 0;
  private static final int COL_SPECIES = 1;
  private static final int COL_SUBSPECIES = 2;
  private static final int COL_REFERENCE = 3;
  private static final int COL_STATUS = 4;
  private static final int COL_AUTHORS = 5;
  private static final int COL_REMARKS = 6;
  private static final String NOM_CODE = "ICNB";
  private static final String IJSEM = "Int. J. Syst. Evol. Microbiol. ";
  private static final String IJSB = "Int. J. Syst. Bacteriol. ";
  private static final Integer LAST_IJSB_VOLUME = 49;

  @Inject
  public ChecklistBuilder() {
  }

  private void parseData() throws IOException {
    // get excel sheet
    LOG.info("Downloading latest data from {}", DOWNLOAD);

    // download zipped xls
    final File tmp = FileUtils.createTempDir();
    final File zip = new File(tmp, "dsmz.zip");
    zip.deleteOnExit();
    HttpUtil http = new HttpUtil();
    http.download(DOWNLOAD, zip);
    // decompress
    List<File> files = CompressionUtil.unzipFile(tmp, zip);
    if (files.isEmpty()) {
      throw new IllegalStateException("No files found in ZIP: " + zip.getAbsolutePath());
    } else if (files.size() > 1) {
      throw new IllegalStateException("More than one file found in ZIP: " + zip.getAbsolutePath());
    }
    parseData(files.get(0));
  }

  // parse XLS
  private void parseData(File xls) throws IOException {

    Workbook wb = new HSSFWorkbook(new FileInputStream(xls));
    Sheet sheet = wb.getSheetAt(0);
    int rows = sheet.getPhysicalNumberOfRows();
    LOG.info("{} rows found in excel sheet", rows);

    final Pattern NOM_PARSE_PATTERN = Pattern.compile(" *\\( *(VL|VP|AL) *\\)");
    final Pattern REF_PARSE_PATTERN = Pattern.compile("(\\d+)(:(\\d+))?");

    Iterator<Row> iter = sheet.rowIterator();
    while( iter.hasNext() ) {
      Row row = iter.next();
      String id = col(row, COL_ID);
      String genus = col(row, COL_GENUS);
      String species = col(row, COL_SPECIES);
      String subspecies = col(row, COL_SUBSPECIES);
      if (Strings.isNullOrEmpty(id) || Strings.isNullOrEmpty(genus) || "GENUS".equalsIgnoreCase(genus)) continue;
      writer.newRecord(id);
      writer.addCoreColumn(DwcTerm.acceptedNameUsageID, col(row, COL_VALID_ID));
      writer.addCoreColumn(DwcTerm.genus, genus);
      writer.addCoreColumn(DwcTerm.specificEpithet, species);
      writer.addCoreColumn(DwcTerm.infraspecificEpithet, subspecies);
      writer.addCoreColumn(DwcTerm.taxonRank, Strings.isNullOrEmpty(subspecies) ? (Strings.isNullOrEmpty(species) ? "genus" : "species") : "subspecies");
      writer.addCoreColumn(DwcTerm.scientificNameAuthorship, col(row, COL_AUTHORS));
      writer.addCoreColumn(DwcTerm.nomenclaturalCode, NOM_CODE);
      // we need to parse these a little
      /*
column D: a shortened reference which includes IJSB/IJSEM volume and page number separated by a colon

with asterix (*): page nummer of the description in an IJSB/IJSEM original publication.
For example, 63:1538* means: In volume 63 of the IJSEM, page 1538, you will find the species description of Acinetobacter boissieri Álvarez-Pérez et al. 2013 within an article.

without asterix: page nummer of the citation in the Approved Lists of Bacterial Names (AL) or the announcement in an IJSB/IJSEM validation list.
For example, 63:798 means: in volume 63 of the IJSEM, page 798, you will find the name Polaribacter reichenbachii Nedashkovskaya et al. 2013 cited in a validation list; the description of this species has appeared in another publication.


column E: valid publication
AL= by citation in the Approved Lists of Bacterial Names
VL= by the announcement in an IJSB/IJSEM validation list
VP= by an original publication in the IJSB/IJSEM
       */
      StringBuilder remark = new StringBuilder();
      remark.append(col(row, COL_REMARKS));

      String status = col(row, COL_STATUS);
      Matcher m = NOM_PARSE_PATTERN.matcher(status);
      if (m.find()) {
        if (m.group(1).equalsIgnoreCase("AL")) {
          remark.append("valid publication by citation in the Approved Lists of Bacterial Names");
        } else if (m.group(1).equalsIgnoreCase("VL")) {
          remark.append("valid publication by the announcement in an IJSB/IJSEM validation list");
        } else if (m.group(1).equalsIgnoreCase("VP")) {
          remark.append("valid publication by an original publication in the IJSB/IJSEM");
        }
        status = m.replaceAll("");
      }
      writer.addCoreColumn(DwcTerm.nomenclaturalStatus, status);
      // Note that, from 1966 to 1999, the journal was called International Journal of Systematic Bacteriology
      m = REF_PARSE_PATTERN.matcher(col(row, COL_REFERENCE));
      if (m.find()) {
        Integer vol = Integer.parseInt(m.group(1));
        String page = "";
        if (!Strings.isNullOrEmpty(m.group(2))) {
         page = ":" + m.group(2);
        }
        if (vol <= LAST_IJSB_VOLUME) {
          writer.addCoreColumn(DwcTerm.namePublishedIn, IJSB + vol + page);
        } else {
          writer.addCoreColumn(DwcTerm.namePublishedIn, IJSEM + vol + page);
        }
      } else {
        LOG.warn("Reference cannot be parsed: {}", col(row, COL_REFERENCE));
      }
      writer.addCoreColumn(DwcTerm.taxonRemarks, remark.toString());
    }
  }

  private String col(Row row, int column) {
    Cell c = row.getCell(column);
    switch (c.getCellType()) {
      case Cell.CELL_TYPE_NUMERIC:
        return String.valueOf((int) c.getNumericCellValue());
      case Cell.CELL_TYPE_STRING:
        return c.getStringCellValue();
    }

    return c.toString();
  }

  public File build() throws IOException {
    // new writer
    File dwcaDir = FileUtils.createTempDir("dsmz-", "");
    File dwcaZip = new File(dwcaDir.getAbsoluteFile() + ".zip");
    LOG.info("Writing archive files to temporary folder " + dwcaDir);
    writer = new DwcaWriter(DwcTerm.Taxon, dwcaDir);

    // metadata
    Eml eml = new Eml();
    eml.setTitle(TITLE);
    eml.setDescription(DESCRIPTION);
    eml.setLanguage(LANGUAGE);
    eml.setHomepageUrl(HOMEPAGE);
    eml.setLogoUrl(LOGO);
    eml.setDistributionUrl(DOWNLOAD);

    Agent creator = new Agent();
    creator.setOrganisation(DSMZ_ORG);
    eml.setResourceCreator(creator);

    Agent contact = new Agent();
    contact.setOrganisation(DSMZ_ORG);
    contact.setFirstName(CONTACT_FIRST_NAME);
    contact.setLastName(CONTACT_LAST_NAME);
    contact.setEmail(CONTACT_EMAIL);
    eml.setContact(contact);

    for (String c : CONTRIBUTORS) {
      String[] name = c.split(" ");
      Agent trib = new Agent();
      trib.setOrganisation(DSMZ_ORG);
      trib.setRole("Editor");
      trib.setFirstName(name[0]);
      trib.setLastName(name[1]);
      eml.addAssociatedParty(trib);
    }
    // parse file and some metadata
    parseData(new File("/Users/mdoering/Downloads/bactname0713.xls"));
    //parseData();

    // finish archive and zip it
    LOG.info("Bundling archive at {}", dwcaZip);
    writer.setEml(eml);
    writer.close();

    // compress
    CompressionUtil.zipDir(dwcaDir, dwcaZip);
    // remove temp folder
    org.apache.commons.io.FileUtils.deleteDirectory(dwcaDir);

    LOG.info("Dwc archive completed at {} !", dwcaZip);

    return dwcaZip;
  }

  public static void main(String[] args) throws IOException {
    ChecklistBuilder builder = new ChecklistBuilder();
    File archive = builder.build();
    System.out.println("Archive generated at " + archive.getAbsolutePath());
  }
}
