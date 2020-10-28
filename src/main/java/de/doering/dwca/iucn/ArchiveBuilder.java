/*
 * Copyright 2020 Global Biodiversity Information Facility (GBIF)
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
package de.doering.dwca.iucn;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.BuilderConfig;
import org.gbif.api.model.registry.Contact;
import org.gbif.api.vocabulary.ContactType;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.Language;
import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.common.parsers.LanguageParser;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.IucnTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;
import org.gbif.utils.file.tabular.TabularDataFileReader;
import org.gbif.utils.file.tabular.TabularFiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ArchiveBuilder extends AbstractBuilder {

  // metadata
  private static final String TITLE = "The IUCN Red List of Threatened Species";
  private static final URI HOMEPAGE = URI.create("https://www.iucnredlist.org/");
  private static final URI LOGO = URI.create("https://raw.githubusercontent.com/mdoering/checklist_builder/master/src/main/resources/iucn/IUCN_Red_List.svg");
  private static final String CONTACT_ORG = "International Union for Conservation of Nature";
  private static final String CITATION_FORMAT = "IUCN ({YEAR}). The IUCN Red List of Threatened Species. " +
    "Version {VERSION}. https://www.iucnredlist.org. Downloaded on {DL_DATE}. https://doi.org/10.15468/0qnb58";

  // This is a download from the IUCN Red List site, created using a search from https://www.iucnredlist.org/search
  //
  // Unfortunately, it seems not to be possible to link to the search.
  // • Geographical Scope: Global
  // • Include: Species; Subspecies and varieties
  // Then it is downloaded in "Search Summary" format, as the alternative download formats are restricted.
  //
  // Commit 97f9555cb58a9a57b88c5b57d8b0d71f895bceb4 used the IUCN API, which included the DOIs for
  // each assessment.  These are unfortunately not included in the downloads.
  private static final String DOWNLOAD = "https://hosted-datasets.gbif.org/datasets/iucn/redlist_species_data_565c8122-6fa3-497d-b873-9aa807a7bf05.zip";
  private static final String VERSION_URL = "https://apiv3.iucnredlist.org/api/v3/version";

  // columns for taxonomy.csv
  private static final int TAX_TAXON_ID = 0;
  private static final int TAX_SCIENTIFIC_NAME = 1;
  private static final int TAX_KINGDOM_NAME = 2;
  private static final int TAX_PHYLUM_NAME = 3;
  private static final int TAX_ORDER_NAME = 4;
  private static final int TAX_CLASS_NAME = 5;
  private static final int TAX_FAMILY_NAME = 6;
  private static final int TAX_GENUS_NAME = 7;
  private static final int TAX_SPECIES_NAME = 8;
  private static final int TAX_INFRA_TYPE = 9;
  private static final int TAX_INFRA_NAME = 10;
  private static final int TAX_INFRA_AUTHORITY = 11;
  private static final int TAX_SUBPOPULATION_NAME = 12;
  private static final int TAX_AUTHORITY = 13;
  private static final int TAX_TAXONOMIC_NOTES = 14;
  private static final int TAX_COL_MIN = TAX_TAXONOMIC_NOTES;

  // columns for assessments.csv
  private static final int ASS_ASSESSMENT_ID = 0;
  private static final int ASS_INTERNAL_TAXON_ID = 1;
  private static final int ASS_SCIENTIFIC_NAME = 2;
  private static final int ASS_REDLIST_CATEGORY = 3;
  private static final int ASS_REDLIST_CRITERIA = 4;
  private static final int ASS_YEAR_PUBLISHED = 5;
  private static final int ASS_ASSESSMENT_DATE = 6;
  private static final int ASS_CRITERIA_VERSION = 7;
  private static final int ASS_LANGUAGE = 8;
  private static final int ASS_RATIONALE = 9;
  private static final int ASS_HABITAT = 10;
  private static final int ASS_THREATS = 11;
  private static final int ASS_POPULATION = 12;
  private static final int ASS_POPULATION_TREND = 13;
  private static final int ASS_RANGE = 14;
  private static final int ASS_USE_TRADE = 15;
  private static final int ASS_SYSTEMS = 16;
  private static final int ASS_CONSERVATION_ACTIONS = 17;
  private static final int ASS_REALM = 18;
  private static final int ASS_YEAR_LAST_SEEN = 19;
  private static final int ASS_POSSIBLY_EXTINCT = 20;
  private static final int ASS_POSSIBLY_EXTINCT_IN_THE_WILD = 21;
  private static final int ASS_SCOPES = 22;
  private static final int ASS_COL_MIN = ASS_SCOPES;

  // columns for common_names.csv
  private static final int COM_INTERNAL_TAXON_ID = 0;
  private static final int COM_SCIENTIFIC_NAME = 1;
  private static final int COM_NAME = 2;
  private static final int COM_LANGUAGE = 3;
  private static final int COM_MAIN = 4;

  // columns for synonyms.csv
  private static final int SYN_INTERNAL_TAXON_ID = 0;
  private static final int SYN_SCIENTIFIC_NAME = 1;
  private static final int SYN_NAME = 2;
  private static final int SYN_GENUS_NAME = 3;
  private static final int SYN_SPECIES_NAME = 4;
  private static final int SYN_SPECIES_AUTHOR = 5;
  private static final int SYN_INFRA_TYPE = 6;
  private static final int SYN_INFRA_RANK_AUTHOR = 7;

  private String version;

  public ArchiveBuilder(BuilderConfig cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  @Override
  protected void parseData() throws Exception {
    // IUCN Red List version, e.g. 2020-2
    IucnVersion versionList = http.readJson(VERSION_URL, IucnVersion.class);
    version = versionList.version;

    // Publication date of this checklist
    dataset.setPubDate(new Date());

    final File tmp = FileUtils.createTempDir();
    tmp.deleteOnExit();

    final File zip = File.createTempFile("iucn", ".zip");
    zip.deleteOnExit();
    http.download(DOWNLOAD, zip);
    List<File> files = CompressionUtil.unzipFile(tmp, zip);

    // The simple_summary.csv file was probably sufficient, but isn't used.
    // Optional<File> simple_summary = files.stream().filter(f -> f.getName().equals("simple_summary.csv")).findFirst();

    // Index assessments by taxon key
    Multimap<String, List<String>> assessmentsMap =
      indexByColumn(files.stream().filter(f -> f.getName().equals("assessments.csv")).findFirst().get(), ASS_INTERNAL_TAXON_ID);

    // Index common names by taxon key
    Multimap<String, List<String>> commonNamesMap =
      indexByColumn(files.stream().filter(f -> f.getName().equals("common_names.csv")).findFirst().get(), COM_INTERNAL_TAXON_ID);

    // Index synonyms by taxon key
    Multimap<String, List<String>> synonymsMap =
      indexByColumn(files.stream().filter(f -> f.getName().equals("synonyms.csv")).findFirst().get(), SYN_INTERNAL_TAXON_ID);

    // Iterate through the taxonomy
    Optional<File> taxonomy = files.stream().filter(f -> f.getName().equals("taxonomy.csv")).findFirst();
    TabularDataFileReader<List<String>> reader = TabularFiles.newTabularFileReader(
      new InputStreamReader(new FileInputStream(taxonomy.get()), "UTF-8"),
      ',', "\n", '"', true
    );

    List<String> taxon;
    while ((taxon = reader.read()) != null) {
      final String taxonKey = taxon.get(TAX_TAXON_ID);

      // Skip subpopulation assessments, in case these have been included in the download
      if (!Strings.isNullOrEmpty(taxon.get(TAX_SUBPOPULATION_NAME))) {
        LOG.info("Skipping {}, which is a subpopulation taxon", taxonKey);
        continue;
      }

      final String authority = taxon.get(TAX_AUTHORITY).replace("&amp;", "&");

      writer.newRecord(taxonKey);
      writer.addCoreColumn(DwcTerm.scientificName, taxon.get(TAX_SCIENTIFIC_NAME) + ' ' + authority);
      writer.addCoreColumn(DwcTerm.kingdom, taxon.get(TAX_KINGDOM_NAME));
      writer.addCoreColumn(DwcTerm.phylum, taxon.get(TAX_PHYLUM_NAME));
      writer.addCoreColumn(DwcTerm.class_, taxon.get(TAX_CLASS_NAME));
      writer.addCoreColumn(DwcTerm.order, taxon.get(TAX_ORDER_NAME));
      writer.addCoreColumn(DwcTerm.family, taxon.get(TAX_FAMILY_NAME));
      writer.addCoreColumn(DwcTerm.genus, taxon.get(TAX_GENUS_NAME));
      writer.addCoreColumn(DwcTerm.specificEpithet, taxon.get(TAX_SPECIES_NAME));
      writer.addCoreColumn(DwcTerm.scientificNameAuthorship, authority);
      writer.addCoreColumn(DwcTerm.taxonRank, taxon.get(TAX_INFRA_TYPE));
      writer.addCoreColumn(DwcTerm.infraspecificEpithet, TAX_INFRA_NAME);
      writer.addCoreColumn(DwcTerm.taxonomicStatus, TaxonomicStatus.ACCEPTED);
      writer.addCoreColumn(DwcTerm.acceptedNameUsageID, taxonKey);
      writer.addCoreColumn(DcTerm.references, "https://apiv3.iucnredlist.org/api/v3/taxonredirect/" + taxonKey);

      for (List<String> assessment : assessmentsMap.get(taxonKey)) {
        Map<Term, String> globalDistribution = new HashMap<>();
        globalDistribution.put(DwcTerm.locality, "Global");
        globalDistribution.put(IucnTerm.threatStatus, assessment.get(ASS_REDLIST_CATEGORY));
        // What about criteria? Population trend?
        switch (assessment.get(ASS_REDLIST_CATEGORY)) {
          case "Extinct":
          case "Extinct in the Wild":
            globalDistribution.put(DwcTerm.occurrenceStatus, "Absent");
            break;

          case "Critically Endangered":
          case "Endangered":
          case "Vulnerable":
          case "Near Threatened":
          case "Data Deficient":
          case "Least Concern":
          case "Lower Risk/conservation dependent":
          case "Lower Risk/near threatened":
          case "Lower Risk/least concern":
            globalDistribution.put(DwcTerm.occurrenceStatus, "Present");
            break;

          case "Not Evaluated": // Not used.
            globalDistribution.put(DwcTerm.occurrenceStatus, "Unknown");
            break;

          default:
            throw new Exception("Unknown assessment category " + assessment.get(ASS_REDLIST_CATEGORY) + " on " + taxonKey);
        }
        globalDistribution.put(DwcTerm.countryCode, null);
        globalDistribution.put(DwcTerm.establishmentMeans, null);
        writer.addExtensionRecord(GbifTerm.Distribution, globalDistribution);
      }

      for (List<String> commonName : commonNamesMap.get(taxonKey)) {
        Map<Term, String> vernacularName = new HashMap<>();

        String language = commonName.get(COM_LANGUAGE);
        ParseResult<Language> parsedLanguage = LanguageParser.getInstance().parse(language);
        if (parsedLanguage.isSuccessful()) {
          vernacularName.put(DcTerm.language, parsedLanguage.getPayload().getIso3LetterCode());
        } else {
          vernacularName.put(DcTerm.language, language);
        }
        vernacularName.put(DwcTerm.vernacularName, commonName.get(COM_NAME));
        vernacularName.put(GbifTerm.isPreferredName, commonName.get(COM_MAIN));
        writer.addExtensionRecord(GbifTerm.VernacularName, vernacularName);
      }

      // Synonyms make new records, so they must be last.
      int synonym_index = 0;
      for (List<String> synonym : synonymsMap.get(taxonKey)) {
        synonym_index++;

        String synonymName = synonym.get(SYN_NAME).replace("&amp;", "&");
        String synonymAuthority = synonym.get(SYN_SPECIES_AUTHOR).replace("&amp;", "&");

        writer.newRecord(String.valueOf(taxonKey) + "_" + synonym_index);
        writer.addCoreColumn(DwcTerm.scientificName, synonymName);
        writer.addCoreColumn(DwcTerm.kingdom, taxon.get(TAX_KINGDOM_NAME)); // Assume synonym is same kingdom as accepted name
        writer.addCoreColumn(DwcTerm.scientificNameAuthorship, synonymAuthority);
        writer.addCoreColumn(DwcTerm.taxonomicStatus, TaxonomicStatus.SYNONYM);
        writer.addCoreColumn(DwcTerm.acceptedNameUsageID, taxonKey);
      }

      LOG.info("  Taxon {} ({}) with {} synonyms completed.", taxonKey, taxon.get(TAX_SCIENTIFIC_NAME), synonym_index);
    }
  }

  public static class IucnVersion {
    public String version;
  }

  private String getCitation() {
    return CITATION_FORMAT
      .replace("{YEAR}", version.substring(0, 4))
      .replace("{VERSION}", version)
      .replace("{DL_DATE}", LocalDate.now(ZoneOffset.UTC).toString());
  }

  // Index a CSV file by a column.
  private Multimap<String, List<String>> indexByColumn(File source, int indexColumn) throws IOException, ParseException {
    Multimap<String, List<String>> map = HashMultimap.create();

    TabularDataFileReader<List<String>> reader = TabularFiles.newTabularFileReader(
      new InputStreamReader(new FileInputStream(source), "UTF-8"),
      ',', "\n", '"', true
    );

    List<String> row;
    while ((row = reader.read()) != null) {
      map.put(row.get(indexColumn), row);
    }

    return map;
  }

  @Override
  protected void addMetadata() {
    dataset.setTitle(TITLE);
    dataset.setVersion(version);

    setCitation(getCitation());
    setDescription("iucn/description.txt");
    dataset.setRights("https://www.iucnredlist.org/terms/terms-of-use");
    dataset.setHomepage(HOMEPAGE);
    dataset.setLogoUrl(LOGO);

    Contact craig = new Contact();
    craig.setType(ContactType.ADMINISTRATIVE_POINT_OF_CONTACT);
    craig.setOrganization(CONTACT_ORG);
    craig.setFirstName("Craig");
    craig.setLastName("Hilton-Taylor");
    craig.getEmail().add("Craig.HILTON-TAYLOR@iucn.org");
    addContact(craig);

    Contact ackbar = new Contact();
    ackbar.setType(ContactType.TECHNICAL_POINT_OF_CONTACT);
    ackbar.setOrganization(CONTACT_ORG);
    ackbar.setFirstName("Ackbar");
    ackbar.setLastName("Joolia");
    ackbar.setPosition(Lists.newArrayList("Biodiversity Systems Manager"));
    ackbar.getEmail().add("Ackbar.JOOLIA@iucn.org");
    addContact(ackbar);
  }

  protected void addMetadataProvider() {
    addContact("GBIF", "Matthew", "Blissett", "mblissett@gbif.org", ContactType.PROGRAMMER);
  }
}
