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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import de.doering.dwca.utils.ParagraphBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.gbif.api.vocabulary.ContactType;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.Rank;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;

import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prokaryotic Nomenclature Up-to-date (PNU) / LPSN
 */
public class ArchiveBuilder extends AbstractBuilder {

  private static final String VERSION_PAGE = "https://www.dsmz.de/services/online-tools/prokaryotic-nomenclature-up-to-date/downloads";
  private static final String DOWNLOAD = "https://www.dsmz.de/fileadmin/Bereiche/ChiefEditors/BacterialNomenclature/DSMZ_bactnames.xlsx";
  private static final String API_HOST = "bacdive.dsmz.de";
  private static final String API_URL = "https://"+API_HOST+"/api/pnu/";

  // metadata
  private static final String TITLE = "Prokaryotic Nomenclature Up-to-Date (PNU)";
  private static final String HOMEPAGE = "https://www.dsmz.de/services/online-tools/prokaryotic-nomenclature-up-to-date";
  private static final String LOGO = "http://www.dsmz.de/fileadmin/templates/gfx/logo.gif";
  private static final String CITATION = "Leibniz Institute DSMZ-German Collection of Microorganisms and Cell Cultures GmbH, Germany, Prokaryotic Nomenclature Up-to-date ";
  private static final String DESCRIPTION = "Prokaryotic Nomenclature Up-to-Date (PNU) is a compilation of all names of Bacteria and Archaea " +
      "which have been validly published according to the Bacteriological Code since 1. Jan. 1980, " +
      "and nomenclatural changes which have been validly published since. " +
      "It will be updated with the publication of each new issue of the International Journal of Systematic and Evolutionary Microbiology (IJSEM). " +
      "In February 2020 PNU was merged with the List of Prokaryotic names with Standing in Nomenclature (LPSN).";
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
  public ArchiveBuilder(CliConfiguration cfg) {
    super(DatasetType.CHECKLIST, cfg, "mdoering@gbif.org", "NzFhs9MAC44L");
  }

  private void parseReleaseDate() {
    //TODO. grep from VERSION_PAGE
    //Pattern publishDate = Pattern.compile("(\\d\\d)(\\d\\d)");
    //Matcher m = publishDate.matcher(xls.getName());
    //dataset.setPubDate(cal.getTime());
    setCitation(CITATION + "August 2019");
  }

  protected void parseData() throws Exception {
    LOG.info("Opening spreadsheet from {}", DOWNLOAD);
    URL xls = new URL(DOWNLOAD);
    Workbook wb = WorkbookFactory.create(xls.openStream());
    Sheet sheet = wb.getSheetAt(0);
    int rows = sheet.getPhysicalNumberOfRows();
    LOG.info("{} rows found in excel sheet", rows);

    final Pattern NOM_PARSE_PATTERN = Pattern.compile(" *\\( *(VL|VP|AL) *\\)");
    final Pattern REF_PARSE_PATTERN = Pattern.compile("(\\d+)(:(\\d+))?");

    Iterator<Row> iter = sheet.rowIterator();
    while (iter.hasNext()) {
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
      Rank rank = Strings.isNullOrEmpty(subspecies) ? (Strings.isNullOrEmpty(species) ? Rank.GENUS : Rank.SPECIES) : Rank.SUBSPECIES;
      writer.addCoreColumn(DwcTerm.taxonRank, rank.name());
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
      ParagraphBuilder remark = new ParagraphBuilder();
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
      if (rank == Rank.GENUS) {
        addGenusClassification(id, genus);
      }
    }

    parseReleaseDate();
  }

  public static class DsmzGenus {
    public String pnu_no;
    public String genus;
    public String authors;
    public String familia;
    public String classis;
    public String phylum;
    public String regio;
    public String reference;
    public String type_species;
    public List<DsmzReference> literature;
  }
  public static class DsmzReference {
    public String pubmed;
    public String reference;
  }

  private void addGenusClassification(String id, String name) {
    try {
      DsmzGenus genus = http.readJson(API_URL + "genus/" + id + "/", DsmzGenus.class);
      if (genus == null) {
        LOG.error("Failed to retrieve genus {} with id {} from DSMZ API", name, id);
      } else {
        writer.addCoreColumn(DwcTerm.family, cleanTaxon(genus.familia));
        writer.addCoreColumn(DwcTerm.class_, cleanTaxon(genus.classis));
        writer.addCoreColumn(DwcTerm.phylum, cleanTaxon(genus.phylum));
        writer.addCoreColumn(DwcTerm.kingdom, cleanTaxon(genus.regio));

        writer.addCoreColumn(DwcTerm.namePublishedIn, StringUtils.trimToNull(genus.reference));
        // literature
        if (genus.literature != null) {
          for (DsmzReference ref : genus.literature) {
            if (StringUtils.isBlank(ref.reference)) continue;
            Map<Term, String> rec = new HashMap<>();
            rec.put(DcTerm.bibliographicCitation, StringUtils.trimToNull(ref.reference));
            writer.addExtensionRecord(GbifTerm.Reference, rec);
          }
        }
      }

    } catch (Exception e) {
      LOG.error("Error retrieving genus {} with id {} from DSMZ API: {}", name, id, e.getMessage());
    }
  }

  private String cleanTaxon(String taxon) {
    if (taxon == null || taxon.contains("not assigned")) {
      return null;
    }
    return Strings.emptyToNull(taxon.trim());
  }


  @Override
  protected void addMetadata() {
    // metadata
    dataset.setTitle(TITLE);
    dataset.setDescription(DESCRIPTION);
    dataset.setHomepage(uri(HOMEPAGE));
    dataset.setLogoUrl(uri(LOGO));
    addExternalData(DOWNLOAD, null);
    addContact(DSMZ_ORG, CONTACT_EMAIL, ContactType.ORIGINATOR);
    addContact(DSMZ_ORG, CONTACT_FIRST_NAME, CONTACT_LAST_NAME, CONTACT_EMAIL, ContactType.POINT_OF_CONTACT);
    for (String c : CONTRIBUTORS) {
      String[] name = c.split(" ");
      addContact(DSMZ_ORG, name[0], name[1], null, ContactType.EDITOR);
    }
  }

  //public static void main(String[] args) {
  //  CliConfiguration cfg = new CliConfiguration();
  //  ArchiveBuilder ab = new ArchiveBuilder(cfg);
  //  ab.addGenusClassification("514991", "Acetobacter");
  //}
}
