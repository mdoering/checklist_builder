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
import com.google.common.collect.MultimapBuilder;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.BuilderConfig;
import org.gbif.api.model.registry.Contact;
import org.gbif.api.vocabulary.ContactType;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.Language;
import org.gbif.api.vocabulary.License;
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
import java.net.URISyntaxException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An archive builder for the IUCN Red List checklist.
 *
 * This converts archives retrieved using bulk downloads from the IUCN website to Darwin Core archives.
 *
 * The resulting archive should be publicly accessible.
 *
 * An earlier version (commit 97f9555cb58a9a57b88c5b57d8b0d71f895bceb4) used the IUCN API, but for
 * performance was replaced with the downloads.  The API could be used once more, if synonyms,
 * author names and citations are added to the "bulk list" endpoint.
 */
public class ArchiveBuilder extends AbstractBuilder {

  // metadata
  private static final String TITLE = "The IUCN Red List of Threatened Species";
  private static final URI HOMEPAGE = URI.create("https://www.iucnredlist.org/");
  private static final URI LOGO = URI.create("https://raw.githubusercontent.com/mdoering/checklist_builder/master/src/main/resources/iucn/IUCN_Red_List.svg");
  private static final String CONTACT_ORG = "International Union for Conservation of Nature";
  private static final String CITATION_FORMAT = "IUCN ({YEAR}). The IUCN Red List of Threatened Species. " +
    "Version {VERSION}. https://www.iucnredlist.org. Downloaded on {DL_DATE}. https://doi.org/10.15468/0qnb58";

  public static final Pattern DX_DOI = Pattern.compile("https?://dx\\.doi\\.org/");

  // These are five downloads from the IUCN Red List site, created using searches from https://www.iucnredlist.org/search
  //
  // They are "Search Results" downloads, but with the download options under the account profile
  // edited to add additional tables: common names, credits, references, synonyms, threats, DOIs.
  //
  // The searches have
  // • Geographical Scope: Global
  // • Include: Species; Subspecies and varieties
  // • Taxonomy:
  //   • Chromista, Fungi, Plantae except Magnoliopsida:
  //     2020-3: https://www.iucnredlist.org/search?dl=true&permalink=ec87b4a8-ae17-456d-b7b0-34c2401e0d49
  //     2021-2: https://www.iucnredlist.org/search?dl=true&permalink=0209d652-b530-4370-b1dd-0f6060ceb2a2
  //     2021-3: https://www.iucnredlist.org/search?dl=true&permalink=24e91ec3-04dc-4a2b-a7f5-a0ed43745eb2
  //     2022-1: https://www.iucnredlist.org/search?dl=true&permalink=2b8593b2-4e46-4ebc-9a46-e63627f2b54c
  //     2024-2: https://www.iucnredlist.org/search?dl=true&permalink=34d65136-e15c-423a-8d6f-d2b8da3e1fca
  //     2025-1: https://www.iucnredlist.org/search?dl=true&permalink=a84bbd4d-cff0-48e7-b35d-d6e840e54c63
  //   • Magnoliopsida:
  //     2020-3: https://www.iucnredlist.org/search?dl=true&permalink=774a66fc-ab93-4f38-b7cf-be12e3991867
  //     2021-2: https://www.iucnredlist.org/search?dl=true&permalink=b43be10f-29f0-4578-ba9d-9d36c013d5ed
  //     2021-3: https://www.iucnredlist.org/search?dl=true&permalink=31177ed1-1200-49d4-97e9-aede9dd43c56
  //     2022-1: https://www.iucnredlist.org/search?dl=true&permalink=f66e8cce-8332-482d-bc24-ca807f8f06b2
  //     2024-2: https://www.iucnredlist.org/search?dl=true&permalink=72846f1a-333f-4c46-9e9a-cb2020c732f3
  //     2025-1: https://www.iucnredlist.org/search?dl=true&permalink=eff73c99-5c5c-4a87-a48d-8e7073938d76
  //   • Animalia except Chordata:
  //     2020-3: https://www.iucnredlist.org/search?dl=true&permalink=7a668685-6d45-456e-bf20-e68206970be2
  //     2021-2: https://www.iucnredlist.org/search?dl=true&permalink=04ae69d8-62e5-43b3-ad3d-205d249bba05
  //     2021-3: https://www.iucnredlist.org/search?dl=true&permalink=52a0ba93-b58f-41f1-8f84-c8c5775f23ac
  //     2022-1: https://www.iucnredlist.org/search?dl=true&permalink=f6b2fec8-5de2-4440-8922-a153a48d457b
  //     2024-2: https://www.iucnredlist.org/search?dl=true&permalink=94225c42-c643-43a9-84ac-372a9b39df4e
  //     2025-1: https://www.iucnredlist.org/search?dl=true&permalink=59b9b612-f23c-42a0-9831-66fb76f11ae3
  //   • Chordata except Passeriformes:
  //     2020-3: https://www.iucnredlist.org/search?dl=true&permalink=1975aa7a-d2df-44fc-846b-b9dcb22da748
  //     2021-2: https://www.iucnredlist.org/search?dl=true&permalink=6957ecea-6987-46b8-858b-46c88dd52a66
  //     2021-3: https://www.iucnredlist.org/search?dl=true&permalink=75c228ba-d760-4aeb-80a2-249327e8e21d
  //     2022-1: https://www.iucnredlist.org/search?dl=true&permalink=75302296-51fa-4d15-a1c1-772232e12944
  //     2024-2: https://www.iucnredlist.org/search?dl=true&permalink=dfd8a856-0567-4ccd-be2c-57c8f9b9843c
  //     2025-1: https://www.iucnredlist.org/search?dl=true&permalink=063a6746-0229-45eb-89d6-43788990e513
  //   • Passeriformes:
  //     2020-3: https://www.iucnredlist.org/search?dl=true&permalink=4bde9672-b4c3-4cd3-b082-cad5070cc0a0
  //     2021-2: https://www.iucnredlist.org/search?dl=true&permalink=0d602296-1d48-427b-ad5c-71f232b0874b
  //     2021-3: https://www.iucnredlist.org/search?dl=true&permalink=1fad223e-cd64-4430-a0f8-703158e4ea24
  //     2022-1: https://www.iucnredlist.org/search?dl=true&permalink=a5792c2f-2312-4048-b344-5fe2f06fc7ff
  //     2024-2: https://www.iucnredlist.org/search?dl=true&permalink=1b262de2-e7ec-45e4-a9d8-5e7785e1d34d
  //     2025-1: https://www.iucnredlist.org/search?dl=true&permalink=368b8121-10d8-49fb-8bc4-0a9278d6fa14
  // Then it is downloaded in "Search Results" format.  Splitting the birds in two is necessary
  // to enable use of the "Search Results" format, and splitting everything else into parts is
  // needed to avoid overwhelming the IUCN service — without this, dois.txt is usually blank.
  //
  // These searches are for the listed releases.  Future releases might have different phyla,
  // classes etc, so they need to be checked.  (If the bug isn't fixed.  Even if it is fixed,
  // we were told it was necessary to do a Passeriformes + everything-else downloads to avoid a
  // different limitation.)
  //
  // Downloads take about 2 hours to complete.
  //
  // Expected total results
  private static final int EXPECTED_TOTAL = 13955 + 64189 + 28940 + 57717 + 6694;

  // Red List version
  private static final String VERSION = "2025-1";

  // The downloads are stored in a private location in accordance with the IUCN terms and conditions.
  private static final String[] DOWNLOADS = new String[]{
    "https://hosted-datasets.gbif.org/datasets/protected/iucn/2025-1/redlist_species_data_0d5680cd-1219-4d4e-ba0a-00257aa1ea72.zip", // Chromista, Fungi, Plantae except Magnoliopsida
    "https://hosted-datasets.gbif.org/datasets/protected/iucn/2025-1/redlist_species_data_6f9c1e7b-067c-4039-9ebc-10927a9bf636.zip", // Magnoliopsida
    "https://hosted-datasets.gbif.org/datasets/protected/iucn/2025-1/redlist_species_data_c40b3b2b-6cfb-4074-b58e-b42e7590d1f8.zip", // Animalia except Chordata
    "https://hosted-datasets.gbif.org/datasets/protected/iucn/2025-1/redlist_species_data_73742d23-0088-49e8-9fd2-5ef7cfdb0d30.zip", // Chordata except Passeriformes
    "https://hosted-datasets.gbif.org/datasets/protected/iucn/2025-1/redlist_species_data_0015ea20-00da-45a6-b157-9b14399073af.zip"  // Passeriformes
  };

  // columns for taxonomy.csv
  private static final int TAX_TAXON_ID = 0;
  private static final int TAX_SCIENTIFIC_NAME = 1;
  private static final int TAX_KINGDOM_NAME = 2;
  private static final int TAX_PHYLUM_NAME = 3;
  private static final int TAX_CLASS_NAME = 4;
  private static final int TAX_ORDER_NAME = 5;
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

  // columns for dois.csv
  private static final int DOI_ASSESSMENT_ID = 0;
  private static final int DOI_SCIENTIFIC_NAME = 1;
  private static final int DOI_INTERNAL_TAXON_ID = 2;
  private static final int DOI_DOI = 3;

  // columns for references.csv
  private static final int REF_ASSESSMENT_ID = 0;
  private static final int REF_INTERNAL_TAXON_ID = 1;
  private static final int REF_SCIENTIFIC_NAME = 2;
  private static final int REF_AUTHOR = 3;
  private static final int REF_CITATION = 4;
  private static final int REF_YEAR = 5;
  private static final int REF_TITLE = 6;

  // columns for credits.csv
  private static final int CRE_ASSESSMENT_ID = 0;
  private static final int CRE_INTERNAL_TAXON_ID = 1;
  private static final int CRE_SCIENTIFIC_NAME = 2;
  private static final int CRE_TYPE = 3;
  private static final int CRE_TEXT = 4;
  private static final int CRE_FULL = 5;
  private static final int CRE_VALUE = 6;
  private static final int CRE_ORDER = 7;

  // columns for synonyms.csv
  private static final int SYN_INTERNAL_TAXON_ID = 0;
  private static final int SYN_SCIENTIFIC_NAME = 1;
  private static final int SYN_NAME = 2;
  private static final int SYN_GENUS_NAME = 3;
  private static final int SYN_SPECIES_NAME = 4;
  private static final int SYN_SPECIES_AUTHOR = 5;
  private static final int SYN_INFRA_TYPE = 6;
  private static final int SYN_INFRA_RANK_AUTHOR = 7;

  public ArchiveBuilder(BuilderConfig cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  @Override
  protected void parseData() throws Exception {
    // Publication date of this checklist
    dataset.setPubDate(new Date());

    int count = 0;
    for (String downloadFile : DOWNLOADS) {
      final File tmp = FileUtils.createTempDir();
      tmp.deleteOnExit();

      final File zip = File.createTempFile("iucn", ".zip");
      zip.deleteOnExit();
      http.download(downloadFile, zip);
      List<File> files = CompressionUtil.unzipFile(tmp, zip);

      // The simple_summary.csv file was probably sufficient, but isn't used.
      // Optional<File> simple_summary = files.stream().filter(f -> f.getName().equals("simple_summary.csv")).findFirst();

      // Index assessments by taxon key
      Multimap<String, List<String>> assessmentsMap =
        indexByColumn(files.stream().filter(f -> f.getName().equals("assessments.csv")).findFirst().get(), ASS_INTERNAL_TAXON_ID);

      // Index common names by taxon key
      Multimap<String, List<String>> commonNamesMap =
        indexByColumn(files.stream().filter(f -> f.getName().equals("common_names.csv")).findFirst().get(), COM_INTERNAL_TAXON_ID);

      // Index DOIs by taxon key
      Multimap<String, List<String>> doisMap =
        indexByColumn(files.stream().filter(f -> f.getName().equals("dois.csv")).findFirst().get(), DOI_INTERNAL_TAXON_ID);

      // Index references by taxon key
      Multimap<String, List<String>> referencesMap =
        indexByColumn(files.stream().filter(f -> f.getName().equals("references.csv")).findFirst().get(), REF_INTERNAL_TAXON_ID);

      // Index credits by taxon key
      Multimap<String, List<String>> creditsMap =
        indexByColumn(files.stream().filter(f -> f.getName().equals("credits.csv")).findFirst().get(), CRE_INTERNAL_TAXON_ID);

      // Index synonyms by taxon key, sorting by name + author + infraAuthor.
      Comparator<List<String>> synonymComparator = Comparator.comparing(o -> o.get(SYN_NAME) + o.get(SYN_SPECIES_AUTHOR) + o.get(SYN_INFRA_RANK_AUTHOR));
      Multimap<String, List<String>> synonymsMap =
        indexByColumn(files.stream().filter(f -> f.getName().equals("synonyms.csv")).findFirst().get(), SYN_INTERNAL_TAXON_ID, synonymComparator);

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

        // Authority
        final String authority = taxon.get(TAX_AUTHORITY).replace("&amp;", "&").trim();
        assert (!Strings.isNullOrEmpty(authority));

        // I wanted citations like
        // "Blanc, J. 2008. Loxodonta africana. The IUCN Red List of Threatened Species 2008: e.T12392A3339343. https://dx.doi.org/10.2305/IUCN.UK.2008.RLTS.T12392A3339343.en. Downloaded on 15 January 2021."
        // which is visible on https://www.iucnredlist.org/species/12392/3339343, but they are not available through
        // a download, and it would be too many requests to use the API for this (and IUCN have been very reluctant
        // about using the API.)
        //
        // For the moment, we will just use the DOI.
        //String reference = null;
        //for (List<String> ref : referencesMap.get(taxonKey)) {
        //  reference = ref.get(REF_CITATION);
        //}
        //assert (!Strings.isNullOrEmpty(reference));

        String citationAuthor = null;
        for (List<String> credit : creditsMap.get(taxonKey)) {
          if ("RedListAssessors".equals(credit.get(CRE_TYPE))) {
            citationAuthor = credit.get(CRE_TEXT);
            if (Strings.isNullOrEmpty(citationAuthor)) {
              citationAuthor = credit.get(CRE_FULL);
            }
          }
        }
        assert (!Strings.isNullOrEmpty(citationAuthor));

        String citationYear = null;
        for (List<String> assessment : assessmentsMap.get(taxonKey)) {
          citationYear = assessment.get(ASS_YEAR_PUBLISHED);
        }
        assert (!Strings.isNullOrEmpty(citationYear));

        String citationScientificName = taxon.get(TAX_SCIENTIFIC_NAME) + ' ' + authority;

        String citationDoi = null;
        for (List<String> doi : doisMap.get(taxonKey)) {
          citationDoi = DX_DOI.matcher(doi.get(DOI_DOI)).replaceAll("https://doi.org/");
        }
        assert (!Strings.isNullOrEmpty(citationDoi));

        String citation = String.format("%s %s. %s. The IUCN Red List of Threatened Species %s: %s", citationAuthor, citationYear, citationScientificName, citationYear, citationDoi);

        // Calculate rank
        String rank = "species";
        if (!Strings.isNullOrEmpty(taxon.get(TAX_INFRA_TYPE))) {
          rank = taxon.get(TAX_INFRA_TYPE);
        }
        assert(!Strings.isNullOrEmpty(rank));

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
        writer.addCoreColumn(DwcTerm.taxonRank, rank);
        writer.addCoreColumn(DwcTerm.infraspecificEpithet, taxon.get(TAX_INFRA_NAME));
        writer.addCoreColumn(DwcTerm.taxonomicStatus, TaxonomicStatus.ACCEPTED);
        writer.addCoreColumn(DwcTerm.acceptedNameUsageID, taxonKey);
        writer.addCoreColumn(DcTerm.bibliographicCitation, citation);

        String assessmentId = null;

        for (List<String> assessment : assessmentsMap.get(taxonKey)) {
          assessmentId = assessment.get(ASS_ASSESSMENT_ID);

          Map<Term, String> globalDistribution = new HashMap<>();
          globalDistribution.put(DwcTerm.locality, "Global");
          globalDistribution.put(IucnTerm.threatStatus, assessment.get(ASS_REDLIST_CATEGORY).replace("Lower Risk/", ""));
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
            case "Unknown":
              globalDistribution.put(DwcTerm.occurrenceStatus, "Unknown");
              break;

            default:
              throw new Exception("Unknown assessment category " + assessment.get(ASS_REDLIST_CATEGORY) + " on " + taxonKey);
          }
          globalDistribution.put(DwcTerm.countryCode, null);
          globalDistribution.put(DwcTerm.establishmentMeans, null);
          globalDistribution.put(DcTerm.source, citation);
          writer.addExtensionRecord(GbifTerm.Distribution, globalDistribution);
        }
        writer.addCoreColumn(DcTerm.references, "https://www.iucnredlist.org/species/" + taxonKey + "/" + assessmentId);

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

          writer.newRecord(taxonKey + "_" + synonym_index);
          writer.addCoreColumn(DwcTerm.scientificName, synonymName);
          writer.addCoreColumn(DwcTerm.kingdom, taxon.get(TAX_KINGDOM_NAME)); // Assume synonym is same kingdom as accepted name
          writer.addCoreColumn(DwcTerm.scientificNameAuthorship, synonymAuthority);
          writer.addCoreColumn(DwcTerm.taxonomicStatus, TaxonomicStatus.SYNONYM);
          writer.addCoreColumn(DwcTerm.acceptedNameUsageID, taxonKey);
          writer.addCoreColumn(DcTerm.bibliographicCitation, citation);
          writer.addCoreColumn(DcTerm.references, "https://www.iucnredlist.org/species/" + taxonKey + "/" + assessmentId);
        }

        LOG.info("  Taxon {} ({}) with {} synonyms completed.", taxonKey, taxon.get(TAX_SCIENTIFIC_NAME), synonym_index);

        count++;
      }

    }
    if (count == EXPECTED_TOTAL) {
      LOG.info("Processed {} taxa (as expected) from the IUCN downloads", count);
    } else {
      LOG.error("TOTAL DOES NOT MATCH: processed {} taxa from the IUCN downloads, but expected {}", count, EXPECTED_TOTAL);
    }
  }

  private String getCitation() {
    return CITATION_FORMAT
      .replace("{YEAR}", VERSION.substring(0, 4))
      .replace("{VERSION}", VERSION)
      .replace("{DL_DATE}", LocalDate.now(ZoneOffset.UTC).toString());
  }

  // Index a CSV file by a column.
  private Multimap<String, List<String>> indexByColumn(File source, int indexColumn, Multimap<String, List<String>> map) throws IOException, ParseException {
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

  // Index a CSV file by a column.
  private Multimap<String, List<String>> indexByColumn(File source, int indexColumn) throws IOException, ParseException {
    Multimap<String, List<String>> map = HashMultimap.create();
    return indexByColumn(source, indexColumn, map);
  }

  // Index a CSV file by a column, sorted using the comparator
  private Multimap<String, List<String>> indexByColumn(File source, int indexColumn, Comparator<List<String>> comparator) throws IOException, ParseException {
    Multimap<String, List<String>> map = MultimapBuilder.hashKeys().treeSetValues(comparator).build();
    return indexByColumn(source, indexColumn, map);
  }

  @Override
  protected void addMetadata() {
    dataset.setTitle(TITLE);
    dataset.setVersion(VERSION);

    setCitation(getCitation());
    setDescription("iucn/description.txt");
    dataset.setRights("https://www.iucnredlist.org/terms/terms-of-use");
    dataset.setHomepage(HOMEPAGE);
    dataset.setLogoUrl(LOGO);
    // Email 2023-08-01 (Helpdesk), Craig Hilton-Taylor: "The taxonomy and the Red List Categories can be treated as being under a CC BY license"
    dataset.setLicense(License.CC_BY_4_0);

    Contact iucn = new Contact();
    iucn.setType(ContactType.ORIGINATOR);
    iucn.setOrganization(CONTACT_ORG);
    try {
      iucn.setHomepage(List.of(new URI("https://www.iucn.org/")));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    iucn.setLastName(CONTACT_ORG);
    addContact(iucn);

    Contact craig = new Contact();
    craig.setType(ContactType.ADMINISTRATIVE_POINT_OF_CONTACT);
    craig.setOrganization(CONTACT_ORG);
    craig.setFirstName("Craig");
    craig.setLastName("Hilton-Taylor");
    craig.getEmail().add("Craig.HILTON-TAYLOR@iucn.org");
    addContact(craig);

    Contact simon = new Contact();
    simon.setType(ContactType.TECHNICAL_POINT_OF_CONTACT);
    simon.setOrganization(CONTACT_ORG);
    simon.setFirstName("Simon");
    simon.setLastName("Tarr");
    simon.setPosition(Lists.newArrayList("Biodiversity Systems Manager"));
    simon.getEmail().add("Simon.TARR@iucn.org");
    addContact(simon);

    Contact matthew = new Contact();
    matthew.setType(ContactType.PROGRAMMER);
    matthew.setOrganization("GBIF");
    matthew.setFirstName("Matthew");
    matthew.setLastName("Blissett");
    matthew.setPosition(Lists.newArrayList("Software Developer"));
    matthew.getEmail().add("mblissett@gbif.org");
    matthew.addUserId("https://orcid.org/", "0000-0003-0623-6682");
    addContact(matthew);
  }

  protected void addMetadataProvider() {
    Contact matthew = new Contact();
    matthew.setType(ContactType.METADATA_AUTHOR);
    matthew.setOrganization("GBIF");
    matthew.setFirstName("Matthew");
    matthew.setLastName("Blissett");
    matthew.setPosition(Lists.newArrayList("Software Developer"));
    matthew.getEmail().add("mblissett@gbif.org");
    matthew.addUserId("https://orcid.org/", "0000-0003-0623-6682");
    addContact(matthew);
  }
}
