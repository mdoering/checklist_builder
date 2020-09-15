package de.doering.dwca.ipni;

import com.google.common.base.Strings;
import org.gbif.api.model.checklistbank.ParsedName;
import org.gbif.dwc.DwcaWriter;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.NameParser;
import org.gbif.nameparser.UnparsableException;
import org.gbif.utils.file.CompressionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Manual util to parseSynonyms names in a postgres db with the gbif parser and store the canonical and authors.
 */
public class IpniPostgres {
  private static Logger LOG = LoggerFactory.getLogger(IpniPostgres.class);
  private static final int BATCH_SIZE = 1000;
  private NameParser parser = new NameParser();
  private Connection con;
  private static final String BASE_LINK2 = "http://www.ipni.org/ipni/idPlantNameSearch.do?id=";
  private static final String BASE_LINK = "http://ipni.org/urn:lsid:ipni.org:names:";
  private static final String BASE_BIOSTOR = "http://biostor.org/reference/";
  private static final String BASE_JSTOR = "http://www.jstor.org/stable/";
  private static final String BASE_BHL = "http://www.biodiversitylibrary.org/page/";

  public IpniPostgres() throws SQLException {
    String url = "jdbc:postgresql://localhost/ipni";
    Properties props = new Properties();
    props.setProperty("user","postgres");
    props.setProperty("password","pogo");
    //props.setProperty("ssl","true");
    con = DriverManager.getConnection(url, props);
    con.setAutoCommit(false);
  }

  void parseSynonyms() throws SQLException {
    PreparedStatement upd = con.prepareStatement("UPDATE synonym set canonical=?, authors=? WHERE id=?");

    Statement st = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
    st.setFetchSize(BATCH_SIZE);
    System.out.println("\n\nQuery synonyms...");
    ResultSet rs = st.executeQuery("select id, synonym from synonym");
    int counter = 0;
    while (rs.next()) {
      try {
        ParsedName pn = parser.parse(rs.getString(2), null);
        System.out.println(rs.getString(2));

        upd.setString(1, pn.canonicalNameWithMarker());
        upd.setString(2, pn.authorshipComplete());
        upd.setInt(3, rs.getInt(1));
        upd.execute();

        if (counter++ % BATCH_SIZE == 0) {
          System.out.println("committing "+counter);
          con.commit();
        }
      } catch (UnparsableException e) {
        System.err.println(e.type + ": " + e.name);
      }
    }
    con.commit();
    rs.close();
    st.close();
    upd.close();
    System.out.println("PARSED ALL SYNONYMS. DONE!");
  }

  void parseBasionyms() throws SQLException {
    PreparedStatement upd = con.prepareStatement("UPDATE name set basionym_canonical=?, basionym_authors=? WHERE id=?");

    Statement st = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
    st.setFetchSize(BATCH_SIZE);
    System.out.println("\n\nQuery basionyms...");
    ResultSet rs = st.executeQuery("select id, basionym from name where basionym is not null");
    int counter = 0;
    while (rs.next()) {
      try {
        ParsedName pn = parser.parse(rs.getString(2), null);
        System.out.println(rs.getString(2));

        upd.setString(1, pn.canonicalNameWithMarker());
        upd.setString(2, pn.authorshipComplete());
        upd.setString(3, rs.getString(1));
        upd.execute();

        if (counter++ % BATCH_SIZE == 0) {
          System.out.println("committing "+counter);
          con.commit();
        }
      } catch (UnparsableException e) {
        System.err.println(e.type + ": " + e.name);
      }
    }
    con.commit();
    rs.close();
    st.close();
    upd.close();
    System.out.println("PARSED ALL BASIONYMS. DONE!");
  }

  void export(File dir) {
    for (Source src : Source.values()) {
      try {
        export(dir, src);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void appendIfNotNull(StringBuilder sb, String preprefixIfNotEmpty, String prefix, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      if (sb.length()>0) {
        sb.append(Strings.nullToEmpty(preprefixIfNotEmpty));
      }
      sb.append(Strings.nullToEmpty(prefix));
      sb.append(Strings.nullToEmpty(value.trim()));
    }
  }

  void export(File dir, Source source) throws IOException, SQLException {
    File dwca = new File(dir, source.name());
    DwcaWriter writer = new DwcaWriter(DwcTerm.Taxon, dwca, false);
    // dwca defaults, all plants
    writer.addDefaultValue(DwcTerm.Taxon, DwcTerm.kingdom, "Plantae");

    // export this source only
    Statement st = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    st.setFetchSize(BATCH_SIZE);

    ResultSet rs = st.executeQuery("select n.id, n.basionym_id, n.family, n.rank, n.canonical, n.authors, n.name_status, n.nomenclatural_synonym, n.replaced_synonym, n.remarks, n.publication_year_full as year, n.publication || ' ' || n.collation_ as published_in, coalesce('doi:'||n.doi, url, pdf, '"+BASE_BHL+"'||bhl, '"+BASE_JSTOR+"'||jstor, '"+BASE_BIOSTOR+"'||biostor) as published_id from name n WHERE source='" + source.name() + "'");
    int counter = 0;
    while (rs.next()) {
      counter++;
      writer.newRecord(rs.getString("id"));
      writer.addCoreColumn(DcTerm.references, BASE_LINK + rs.getString("id"));
      writer.addCoreColumn(DwcTerm.taxonID, rs.getString("id"));
      writer.addCoreColumn(DwcTerm.originalNameUsageID, rs.getString("basionym_id"));
      writer.addCoreColumn(DwcTerm.family, rs.getString("family"));
      writer.addCoreColumn(DwcTerm.taxonRank, rs.getString("rank"));
      writer.addCoreColumn(DwcTerm.scientificName, rs.getString("canonical"));
      writer.addCoreColumn(DwcTerm.scientificNameAuthorship, rs.getString("authors"));
      writer.addCoreColumn(DwcTerm.nomenclaturalStatus, rs.getString("name_status"));
      writer.addCoreColumn(DwcTerm.namePublishedIn, rs.getString("published_in"));
      writer.addCoreColumn(DwcTerm.namePublishedInID, rs.getString("published_id"));
      writer.addCoreColumn(DwcTerm.namePublishedInYear, rs.getString("year"));
      // build remarks
      StringBuilder remarks = new StringBuilder();
      appendIfNotNull(remarks, null, null, rs.getString("remarks"));
      appendIfNotNull(remarks, ". ", "Nomenclatural synonyms: ", rs.getString("nomenclatural_synonym"));
      appendIfNotNull(remarks, ". ", "Replaced synonyms: ", rs.getString("replaced_synonym"));
      writer.addCoreColumn(DwcTerm.taxonRemarks, remarks.toString() );

      // publication links
      Map<Term, String> data = new HashMap<Term, String>();
      data.put(DcTerm.bibliographicCitation, rs.getString("published_in"));
      data.put(DcTerm.date, rs.getString("year"));
      data.put(DcTerm.identifier, rs.getString("published_id"));
      data.put(DcTerm.type, "original description");
      if (!Strings.isNullOrEmpty(data.get(DcTerm.bibliographicCitation)) || !Strings.isNullOrEmpty(data.get(DcTerm.identifier))) {
        writer.addExtensionRecord(GbifTerm.Reference, data);
      }
    }

    // finish archive and zip it
    LOG.info("Bundling archive at {}", dwca.getAbsolutePath());
    writer.close();

    // compress
    File zip = new File(dwca.getParentFile(), dwca.getName() + ".zip");
    CompressionUtil.zipDir(dwca, zip);
    LOG.info("Dwc archive completed for {} with {} records at {} !", source, counter, zip);
  }

  public void close() throws SQLException {
    con.close();
  }
  public static void main(String[] args) throws Exception {
    IpniPostgres ipni = new IpniPostgres();
    //ipni.parseSynonyms();
    //ipni.parseBasionyms();
    ipni.export(new File("/Users/markus/Downloads/ipni"));
    ipni.close();
  }
}
