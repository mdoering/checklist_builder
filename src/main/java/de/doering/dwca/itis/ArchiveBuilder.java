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
package de.doering.dwca.itis;

import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.BuilderConfig;
import org.apache.commons.lang3.StringUtils;
import org.gbif.api.model.common.DOI;
import org.gbif.api.model.registry.Contact;
import org.gbif.api.vocabulary.ContactType;
import org.gbif.api.vocabulary.Country;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.License;
import org.gbif.dwc.terms.*;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;
import org.sqlite.SQLiteConfig;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.*;
import java.util.Date;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArchiveBuilder extends AbstractBuilder {

  private static final String DOWNLOAD = "https://www.itis.gov/downloads/itisSqlite.zip";
  // metadata
  private static final String TITLE = "Integrated Taxonomic Information System (ITIS)";
  private static final URI HOMEPAGE = URI.create("https://www.itis.gov/");
  private static final DOI DOI = new DOI("https://doi.org/10.5066/F7KH0KBK");
  private static final URI LOGO = URI.create("https://raw.githubusercontent.com/mdoering/checklist_builder/master/src/main/resources/itis/ITIS_logo.png");
  private static final String CONTACT_ORG = "Integrated Taxonomic Information System (ITIS)";
  private static final String CONTACT_CITY = "Washington, DC";
  private static final Country CONTACT_COUNTRY = Country.UNITED_STATES;
  private static final String CONTACT_EMAIL = "itiswebmaster@itis.gov";
  // see https://www.checklistbank.org/dataset/2144/about
  private static final List<Contact> EDITORS = List.of(
      contact("Sara", "Alexander", "alexandersar@si.edu", ContactType.EDITOR),
      contact("Alicia", "Hodson", "hodsona@si.edu", ContactType.EDITOR),
      contact("David", "Mitchell", "mitchelld@si.edu", "0000-0002-5418-244X", ContactType.EDITOR),
      contact("Dave", "Nicolson", "nicolsod@si.edu", "0000-0002-7987-0679", ContactType.EDITOR),
      contact("Thomas", "Orrell", "orrellt@si.edu", "0000-0003-1038-3028", ContactType.EDITOR),
      contact("Daniel", "Perez-Gelabert", "perezd@si.edu", "0000-0003-3270-9551", ContactType.EDITOR)
  );
  private static final Term TERM_ITIS_COMPLETE = new UnknownTerm(URI.create("http:///itis.org/terms/completeness"), false);
  private static final Term TERM_PAGES = new UnknownTerm(URI.create("http:///itis.org/terms/pages"), false);
  private static final Term TERM_ISBN = new UnknownTerm(URI.create("http:///itis.org/terms/ISBN"), false);
  private static final Term TERM_ISSN = new UnknownTerm(URI.create("http:///itis.org/terms/ISSN"), false);

  public ArchiveBuilder(BuilderConfig cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  /**
   * We prefer the excel sheet over the CSV file as the CSV contains bad encodings for some characters of the
   * distribution area names.
   */
  @Override
  protected void parseData() throws Exception {
    // download latest ITIS as sqlite
    LOG.info("Downloading latest ITIS from {}", DOWNLOAD);
    final File tmp = FileUtils.createTempDir();
    tmp.deleteOnExit();

    final File zip = File.createTempFile("itis", ".zip");
    zip.deleteOnExit();

    http.download(DOWNLOAD, zip);
    // decompress
    List<File> files = CompressionUtil.unzipFile(tmp, zip);
    // try to open sqlite
    Optional<File> sqlite = files.stream().filter(f -> f.getName().equalsIgnoreCase("ITIS.sqlite")).findFirst();
    if (!sqlite.isPresent()) {
      throw new IllegalStateException("No sqlite file found in ZIP: " + zip.getAbsolutePath());
    }

    //TODO: extract last dump date somehow
    dataset.setPubDate(new Date());

    processSql(sqlite.get());
  }

  private void processSql(File sqlite) {
    SQLiteConfig cfg = new SQLiteConfig();
    cfg.setReadOnly(true);
    int tsn=0;
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqlite.getAbsoluteFile().getAbsolutePath(), cfg.toProperties())) {
      Statement stmt = connection.createStatement();
      // taxon core
      String sqlCore = "SELECT t.tsn,	t.parent_tsn, group_concat(sl.tsn_accepted, ',') AS acceptedIds, " +
          "ln.completename, a.taxon_author, tut.rank_name," +
          "t.name_usage AS tax_status, t.unaccept_reason AS nom_status, t.completeness_rtng," +
          "p.publication_id, p.reference_author, p.title, p.publication_name, p.listed_pub_date, p.actual_pub_date, p.publisher, p.pages, p.isbn, p.issn" +
      " FROM taxonomic_units t" +
        " JOIN longnames ln ON t.tsn = ln.tsn" +
        " JOIN taxon_unit_types tut ON t.rank_id = tut.rank_id AND t.kingdom_id = tut.kingdom_id" +
        " LEFT JOIN taxon_authors_lkp a ON t.taxon_author_id = a.taxon_author_id" +
        " LEFT JOIN synonym_links sl ON sl.tsn = t.tsn" +
        " LEFT JOIN reference_links rl ON rl.tsn = t.tsn AND rl.doc_id_prefix = 'PUB' AND rl.original_desc_ind = 'Y'" +
        " LEFT JOIN publications p ON p.publication_id = rl.documentation_id " +
      " GROUP BY t.tsn" +
      " ORDER BY t.tsn";

      //TODO: link vernacular references via vern_ref_links
      PreparedStatement vpst = connection.prepareStatement("SELECT vernacular_name, language, approved_ind FROM vernaculars WHERE tsn = ?");
      PreparedStatement dpst = connection.prepareStatement("SELECT geographic_value FROM geographic_div WHERE tsn = ?");
      PreparedStatement rpst = connection.prepareStatement("SELECT p.publication_id, p.reference_author, p.title, p.publication_name, p.listed_pub_date, p.actual_pub_date, p.publisher, p.pages, p.isbn, p.issn " +
          " FROM reference_links rl JOIN publications p ON p.publication_id = rl.documentation_id" +
          " WHERE rl.doc_id_prefix = 'PUB' AND rl.tsn = ?");
      writer.addDefaultValue(GbifTerm.Distribution, DwcTerm.occurrenceStatus, "present");
      writer.addCoreMultiValueDelimiter(DwcTerm.acceptedNameUsageID, ",");

      ResultSet rs = stmt.executeQuery(sqlCore);
      while (rs.next()) {
        tsn = rs.getInt("tsn");

        writer.newRecord(Integer.toString(tsn));
        writer.addCoreColumn(DwcTerm.taxonID, String.valueOf(tsn));
        writer.addCoreColumn(DwcTerm.parentNameUsageID, no0(rs.getInt("parent_tsn")));
        writer.addCoreColumn(DwcTerm.acceptedNameUsageID, rs.getString("acceptedIds"));
        writer.addCoreColumn(DwcTerm.scientificName, rs.getString("completename"));
        writer.addCoreColumn(DwcTerm.scientificNameAuthorship, rs.getString("taxon_author"));
        writer.addCoreColumn(DwcTerm.taxonRank, rs.getString("rank_name"));
        writer.addCoreColumn(DwcTerm.taxonomicStatus, rs.getString("tax_status"));
        writer.addCoreColumn(DwcTerm.nomenclaturalStatus, rs.getString("nom_status"));
        writer.addCoreColumn(TERM_ITIS_COMPLETE, rs.getString("completeness_rtng"));
        writer.addCoreColumn(DwcTerm.namePublishedIn, assembleCitation(
            (Integer) rs.getObject("publication_id"),
            rs.getString("reference_author"),
            rs.getString("title"),
            rs.getString("publication_name"),
            rs.getString("listed_pub_date"),
            rs.getString("publisher"),
            rs.getString("pages"),
            rs.getString("isbn"),
            rs.getString("issn"))
        );
        writer.addCoreColumn(DwcTerm.namePublishedInYear, rs.getString("actual_pub_date"));

        // vernacular
        vpst.setInt(1, tsn);
        ResultSet vrs = vpst.executeQuery();
        while (vrs.next()) {
          Map<Term, String> data = new HashMap<Term, String>();
          data.put(DwcTerm.vernacularName, vrs.getString("vernacular_name"));
          data.put(DcTerm.language, vrs.getString("language"));
          writer.addExtensionRecord(GbifTerm.VernacularName, data);
        }

        // distributions
        dpst.setInt(1, tsn);
        ResultSet drs = dpst.executeQuery();
        while (drs.next()) {
          Map<Term, String> data = new HashMap<Term, String>();
          data.put(DwcTerm.locality, drs.getString("geographic_value"));
          writer.addExtensionRecord(GbifTerm.Distribution, data);
        }

        // references
        rpst.setInt(1, tsn);
        ResultSet rrs = rpst.executeQuery();
        while (rrs.next()) {
          Map<Term, String> data = new HashMap<Term, String>();
          Integer pubId = (Integer) rrs.getObject("publication_id");
          String author = rrs.getString("reference_author");
          String title = rrs.getString("title");
          String pubName = rrs.getString("publication_name");
          String date = rrs.getString("listed_pub_date");
          String publisher = rrs.getString("publisher");
          String pages = rrs.getString("pages");
          String isbn = rrs.getString("isbn");
          String issn = rrs.getString("issn");

          data.put(DcTerm.bibliographicCitation, assembleCitation(
              pubId,
              author,
              title,
              pubName,
              date,
              publisher,
              pages,
              isbn,
              issn
          ));
          data.put(DcTerm.creator, author);
          data.put(DcTerm.title, title);
          data.put(DcTerm.source, pubName);
          data.put(DcTerm.date, date);
          data.put(DcTerm.publisher, publisher);
          data.put(TERM_PAGES, pages);
          data.put(TERM_ISBN, isbn);
          data.put(TERM_ISSN, issn);
          writer.addExtensionRecord(GbifTerm.Reference, data);
        }
      }

    } catch (SQLException | IOException e) {
      System.err.println("Sth went wrong with TSN " + tsn);
      e.printStackTrace();
    }
  }

  private static String assembleCitation(Integer pubId, String reference_author, String title, String publication_name, @Nullable String listed_pub_date, String publisher, String pages, String isbn, String issn) {
    if (pubId != null) {
      StringBuilder sb = new StringBuilder();
      sb.append(reference_author);
      if (listed_pub_date != null) {
        Pattern YEAR = Pattern.compile("(\\d\\d\\d\\d)-");
        Matcher m = YEAR.matcher(listed_pub_date);
        if (m.find()) {
          sb.append(", ");
          sb.append(m.group(1));
        }
      }
      sb.append(": ");
      sb.append(title);
      if (!StringUtils.isBlank(publication_name)) {
        sb.append(". ");
        sb.append(publication_name);
      }
      if (!StringUtils.isBlank(pages)) {
        sb.append(". ");
        sb.append(pages);
      }
      sb.append(".");
      return sb.toString();
    }
    return null;
  }

  private String no0(int x) {
    return x == 0 ? null : String.valueOf(x);
  }

  @Override
  protected void addMetadata() {
    // metadata
    dataset.setTitle(TITLE);
    // https://www.itis.gov/privacy.html
    dataset.setLicense(License.CC0_1_0);
    dataset.setDoi(DOI);
    setDescription("itis/description.txt");
    dataset.setHomepage(HOMEPAGE);
    dataset.setLogoUrl(LOGO);
    Contact contact = new Contact();
    contact.setType(ContactType.POINT_OF_CONTACT);
    contact.setOrganization(CONTACT_ORG);
    contact.setCity(CONTACT_CITY);
    contact.setCountry(CONTACT_COUNTRY);
    contact.getEmail().add(CONTACT_EMAIL);
    addContact(contact);

    for (var ed : EDITORS) {
      dataset.getContacts().add(ed);
    }
  }
}
