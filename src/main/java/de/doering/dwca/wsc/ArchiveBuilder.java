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
package de.doering.dwca.wsc;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.BuilderConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.gbif.api.model.common.DOI;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;
import org.gbif.utils.file.tabular.TabularDataFileReader;
import org.gbif.utils.file.tabular.TabularFiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * Please use ColDP uploads into CLB:
 * https://www.checklistbank.org/dataset/1029/imports
 *
 * or newer ColDP generator
 * https://github.com/CatalogueOfLife/coldp-generator
 */
@Deprecated
public class ArchiveBuilder extends AbstractBuilder {
  private static final String DOWNLOAD = String.format("https://wsc.nmbe.ch/resources/species_export_%s.csv",
      new SimpleDateFormat("yyyyMMdd").format(new Date()) // 20230629
  );

  // metadata
  private static final String ORG = "Natural History Museum Bern";
  private static final String CONTACT_EMAIL = "wsc@nmbe.ch";

  public ArchiveBuilder(BuilderConfig cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  protected void parseData() throws Exception {
    // download latest ITIS as sqlite
    LOG.info("Downloading latest WSC species file from {}", DOWNLOAD);
    final File tmp = FileUtils.createTempDir();
    tmp.deleteOnExit();

    final File csv = File.createTempFile("wsc", ".csv");
    csv.deleteOnExit();

    http.download(DOWNLOAD, csv);
    // map to dwca, mostly change authorship field
    TabularDataFileReader<List<String>> reader = TabularFiles.newTabularFileReader(
        new InputStreamReader(new FileInputStream(csv), "UTF-8"),
        ',', "\n", '"', true
    );

    writer.addDefaultValue(DwcTerm.Taxon, DwcTerm.kingdom, "Animalia");
    writer.addDefaultValue(DwcTerm.Taxon, DwcTerm.phylum, "Arthropoda");
    writer.addDefaultValue(DwcTerm.Taxon, DwcTerm.class_, "Arachnida");
    List<String> row;
    while ((row = reader.read()) != null) {
      writer.newRecord(row.get(1));
      writer.addCoreColumn(DwcTerm.taxonID, row.get(1));
      writer.addCoreColumn(DwcTerm.family,  row.get(2));
      writer.addCoreColumn(DwcTerm.genus,   row.get(3));
      String sp = row.get(4);
      String ssp = row.get(5);
      writer.addCoreColumn(DwcTerm.specificEpithet,   sp);
      writer.addCoreColumn(DwcTerm.infraspecificEpithet, ssp);
      Rank rank;
      if (!StringUtils.isBlank(ssp)) {
        rank = Rank.SUBSPECIES;
      } else if (!StringUtils.isBlank(sp)) {
        rank = Rank.SPECIES;
      } else {
        rank = Rank.UNRANKED;
      }
      writer.addCoreColumn(DwcTerm.taxonRank, rank);
      StringBuilder authorship = new StringBuilder();
      if ("1".equals(row.get(8))) {
        authorship.append("(");
      }
      authorship.append(row.get(6));
      authorship.append(", ");
      authorship.append(row.get(7));
      if ("1".equals(row.get(8))) {
        authorship.append(")");
      }
      if (authorship.length() > 0) {
        writer.addCoreColumn(DwcTerm.scientificNameAuthorship, authorship.toString());
      }
    }
  }

  @Override
  protected void addMetadata() {
    // metadata
    dataset.setDoi(new DOI("10.24436/2"));
    dataset.setTitle("World Spider Catalog");
    dataset.setAlias("WSC");
    dataset.setDescription("The World Spider Catalog is the first fully searchable online database covering spider taxonomy, but it has a longer history of predecessors which started with Pierre Bonnet (University of Toulouse, France) and Carl Friedrich Roewer (Bremen, Germany). Bonnet's seven scholarly books of his Bibliographia araneorum, published in three volumes 1945-1961, were fully comprehensive and covered literature on all aspects of spider biology through 1939, on more than 6400 pages. Roewer's Katalog der Araneae von 1758 bis 1940 (three books, published in two volumes, more than 2700 pages) were published 1942-1955 and covered the taxonomically useful literature through 1940 or 1954 (depending on the taxon).\n"
       + "\n"
       + "The next important step was performed by Paolo M. Brignoli (University of Aquila, Italy) with his Catalogue of the Araneae described between 1940 and 1981, published 1983. This 750 pages volume filled many of the post-Roewer gaps (through 1980, with scattered coverage of later papers as well). Brignoli intended to issue Catalogue supplements at periodic intervals but this stopped due to his untimely death in 1986. Fortunately, Brignoli’s idea could be continued because Norman I. Platnick (American Museum of Natural History, New York) accepted the challenge to take over the task of preparing supplements to Brignoli's volume. In the next decade, three supplement volumes (1989, 1993, 1997) of Advances in Spider Taxonomy with together 2500 pages were published, covering the literature from 1981 through 1995 and including all synonyms, transfers, and re-descriptions from 1940 to 1980.\n"
       + "\n"
       + "By the end of the 20th century it became obvious that the increasing quantity of taxonomic information could no longer be managed in the conventional way. So far more than 10’000 catalog pages and (currently) an annual influx of more than 300 taxonomic publications with descriptions of ca. 900 new species need an internet based solution. Platnick started this task with a first online version of his World Spider Catalog in 2000 and continued through 2014, with two updated versions per year, a total of 30 updates. The catalog was hosted at the American Museum of Natural History and served as HTML files per family. You can find a complete archive.\n"
       + "\n"
       + "With the retirement of Platnick in 2014, the Natural History Museum Bern (Switzerland) accepted to continue Platnick’s work and took over the World Spider Catalog. All data provided by the catalog version 14.5 has been processed in order to fit into a relational database. One of the major achievements of a true database is that it is fully searchable over the complete content of spider taxonomy since 1757 when the first now acknowledged 68 spider species were described by Carl Clerck. Another important novelty is the link to the World Spider Catalog Association (WSCA) which intends to provide access to more than 12’000 taxonomic publications which are behind this database information.\n"
       + "\n"
       + "The World Spider Catalog considers all taxonomically useful published work. Unpublished statements – even if correct – will not be taken over here. Also contents of websites that are not published elsewhere are not considered. Roewer and Platnick set standards for the Catalog that persist largely until today. Basically, this includes all descriptions of new species, transfers, synonymies and all taxonomically useful (i.e., illustrated) references to previously described taxa. Electronic supplements can be considered in combination with the corresponding main article. Not included are subfamilial or subgeneric divisions and allocations, or mentions of taxa in purely faunistic works (unless accompanied by useful illustrations).\n"
       + "\n"
       + "The catalog entries for literature prior to 1940 do not reflect a complete re-check of the classical literature. Roewer's listings based on the classical literature have largely been accepted, and only discrepancies detected between Roewer's and Bonnet's treatments have been re-checked and resolved. These listings are not intended to supplant either Roewer's or Bonnet's volumes, but rather to provide a quick, electronically searchable guide to the most important literature on spider systematics, worldwide. Investigators doing original research should still check the listings in Roewer and Bonnet; we hope that omissions are few, but no project of this magnitude could ever be error-free.\n"
       + "\n"
       + "In certain cases, published nomenclatural or taxonomical changes will not be taken over by the catalog. This includes for example cases with violations of the provisions of the International Code of Zoological Nomenclature. In debatable cases (e.g. purely typological genus-splitting without phylogenetic reasons), an expert board will decide the case democratically. However, if some published alterations are not taken over by the catalog, the respective information and reference is given anyway.\n"
       + "\n"
       + "The following abbreviations are used: Male or female signs (m or f) alone indicate that palpal or epigynal illustrations are included (hence figure references without such annotations include only somatic characters, generally through scanning electron micrographs; citations are not provided for cases where authors supplied only a general view of the body). The letter D indicates an original description, either of a taxon or of a previously unknown sex. The letter T indicates that one or both sexes have been transferred from a specified genus to the one under consideration; tentative statements indicating that a species \"possibly belongs\" or \"may belong\" elsewhere are not included as transfers (or synonymies). The letter S indicates that details of one or more new synonymies can be found immediately under the generic listing; an S followed by a male or female sign indicates that a previously unknown sex has been added through a synonymy. The type species of each genus is marked with an asterisk (*).\n"
       + "\n"
       + "The organization of the entries is hierarchically determined; hence synonymies at the generic level are indicated under the family (and cross-referenced under the appropriate generic) listings, but affected species are listed separately only if there are significant references to them in particular. Similarly, synonymies at the species level are listed under generic, rather than familial, headings. The brief descriptions of geographic ranges are provided only as a general guide; no attempt has been made to ensure that they are comprehensive.\n"
       + "\n"
       + "Users who detect errors, of any sort, are urged to bring them to our attention (email to wsc(at)nmbe.ch)."
    );
    dataset.setHomepage(uri("https://wsc.nmbe.ch"));
    setPubDate(DateTimeFormatter.ISO_DATE.format(LocalDate.now()));
    addExternalData(DOWNLOAD, null);
    addContact(ORG, CONTACT_EMAIL);
  }
}
