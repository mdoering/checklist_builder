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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.BuilderConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.gbif.api.model.registry.Contact;
import org.gbif.api.vocabulary.ContactType;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.Language;
import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.dwc.terms.*;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ArchiveBuilder extends AbstractBuilder {

  // metadata
  private static final String TITLE = "The IUCN Red List of Threatened Species";
  private static final URI HOMEPAGE = URI.create("https://www.iucnredlist.org/");
  private static final URI LOGO = URI.create("https://raw.githubusercontent.com/mdoering/checklist_builder/master/src/main/resources/iucn/IUCN_Red_List.svg");
  private static final String CONTACT_ORG = "International Union for Conservation of Nature";
  private static final String CITATION_FORMAT = "IUCN ({YEAR}). The IUCN Red List of Threatened Species. " +
    "Version {VERSION}. https://www.iucnredlist.org. Downloaded on {DL_DATE}. https://doi.org/10.15468/0qnb58";

  public static final Pattern SPAN_ITALIC = Pattern.compile("<span style=\"font-style: italic;\">(.*?)</span>");
  public static final Pattern SPAN_STRONG = Pattern.compile("<span style=\"font-weight: bold;\">(.*?)</span>");
  public static final Pattern DX_DOI = Pattern.compile("https?://dx\\.doi\\.org/");

  private ObjectMapper jsonMapper = new ObjectMapper();
  // This is just the documentation/demo token.
  private static final String TOKEN = "9bb4facb6d23f48efbf424bb05c0c1ef1cf6f468393bc745d42179ac4aca5fee";

  private static final String VERSION = "https://apiv3.iucnredlist.org/api/v3/version";
  private static final String SPECIES = "https://apiv3.iucnredlist.org/api/v3/species/id/{KEY}?token="+TOKEN;
  private static final String CITATION = "https://apiv3.iucnredlist.org/api/v3/species/citation/id/{KEY}?token="+TOKEN;
  private static final String COMMON_NAME = "https://apiv3.iucnredlist.org/api/v3/species/common_names/{NAME}?token="+TOKEN;
  private static final String SYNONYM = "https://apiv3.iucnredlist.org/api/v3/species/synonym/{NAME}?token="+TOKEN;

  private String version;

  public ArchiveBuilder(BuilderConfig cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  @Override
  protected void parseData() throws Exception {
    // IUCN Red List version, e.g. 2020-2
    IucnVersion versionList = http.readJson(VERSION, IucnVersion.class);
    version = versionList.version;

    // Publication date of this checklist
    dataset.setPubDate(new Date());

    writer.addDefaultValue(GbifTerm.Description, DcTerm.language, Language.ENGLISH.getIso3LetterCode());
    writer.addDefaultValue(GbifTerm.Description, DcTerm.rightsHolder, "IUCN");

    // TODO: Page through all species records.
    List<String> taxa = Lists.newArrayList("12392", "103636217", "75123278", "80231759");


    for (String taxonKey : taxa) {
      LOG.info("Requesting taxon {}", taxonKey);
      List<IucnSpecies> speciesList = http.readJsonResult(SPECIES.replace("{KEY}", taxonKey), IucnSpecies.class);

      List<IucnCitation> citationList = http.readJsonResult(CITATION.replace("{KEY}", taxonKey), IucnCitation.class);
      String citation = DX_DOI.matcher(citationList.get(0).citation).replaceAll("https://doi.org/")
        .replace(" .Downloaded", ". Downloaded");

      for (IucnSpecies species : speciesList) {
        species.authority = species.authority.replace("&amp;", "&");

        writer.newRecord(String.valueOf(species.taxonid));
        writer.addCoreColumn(DwcTerm.scientificName, species.scientific_name + ' ' + species.authority);
        writer.addCoreColumn(DwcTerm.kingdom, species.kingdom);
        writer.addCoreColumn(DwcTerm.phylum, species.phylum);
        writer.addCoreColumn(DwcTerm.class_, species.class_);
        writer.addCoreColumn(DwcTerm.order, species.order);
        writer.addCoreColumn(DwcTerm.family, species.family);
        writer.addCoreColumn(DwcTerm.genus, species.genus);
        writer.addCoreColumn(DwcTerm.vernacularName, species.main_common_name);
        writer.addCoreColumn(DwcTerm.scientificNameAuthorship, species.authority);
        writer.addCoreColumn(DwcTerm.taxonomicStatus, TaxonomicStatus.ACCEPTED);
        writer.addCoreColumn(DwcTerm.acceptedNameUsageID, species.taxonid);
        writer.addCoreColumn(DcTerm.bibliographicCitation, citation);
        writer.addCoreColumn(DcTerm.references, "https://apiv3.iucnredlist.org/api/v3/taxonredirect/" + species.taxonid);

        Map<Term, String> globalDistribution = new HashMap<>();
        globalDistribution.put(DwcTerm.locality, "Global");
        globalDistribution.put(IucnTerm.threatStatus, species.category);
        // What about criteria? Population trend?
        switch (species.category) {
          case "EX":
          case "EW":
            globalDistribution.put(DwcTerm.occurrenceStatus, "Absent");
            break;

          case "CR":
          case "EN":
          case "VU":
          case "NT":
          case "DD":
          case "LC":
            globalDistribution.put(DwcTerm.occurrenceStatus, "Present");
            break;

          case "NE":
            globalDistribution.put(DwcTerm.occurrenceStatus, "Unknown");
            break;

          default:
            throw new Exception("Unknown species.category "+species.category+" on "+species.taxonid);
        }
        globalDistribution.put(DwcTerm.countryCode, null);
        globalDistribution.put(DwcTerm.establishmentMeans, null);
        globalDistribution.put(DcTerm.source, citation.replace(" .Downloaded", ". Downloaded"));
        writer.addExtensionRecord(GbifTerm.Distribution, globalDistribution);

        /*
         * Including the country information requires a permission waiver from the IUCN.
         * (Email, mblissett/arodrigues/mgrosjean; 2020-09-10.)
        List<IucnCountry> countriesList = http.readJsonResult(COUNTRY, IucnCountry.class);
        for (IucnCountry country : countriesList) {
          Map<Term, String> countryDistribution = new HashMap<>();
          countryDistribution.put(DwcTerm.countryCode, country.code);
          countryDistribution.put(DwcTerm.locality, country.country);
          countryDistribution.put(DwcTerm.occurrenceStatus, country.distribution_code);
          countryDistribution.put(DwcTerm.establishmentMeans, country.origin);
          switch (country.distribution_code) {
            case "Regionally Extinct":
              countryDistribution.put(IucnTerm.threatStatus, "EX"); // TODO: Set to regionally extinct (RE)
              countryDistribution.put(DwcTerm.occurrenceStatus, "Extinct");
              break;
            case "Native":
              break;
            case "Reintroduced":
              countryDistribution.put(DwcTerm.occurrenceStatus, "Present");
              break;
            default:
              System.out.println("Unknown distribution_code for " + species.taxonid + "“" + country.distribution_code + "”.");
          }
          countryDistribution.put(DcTerm.source, citation);
          writer.addExtensionRecord(GbifTerm.Distribution, countryDistribution);
        }
         */

        String urlName = species.scientific_name.replace(" ", "%20");

        List<IucnCommonName> commonNameList = http.readJsonResult(COMMON_NAME.replace("{NAME}", urlName), IucnCommonName.class);
        for (IucnCommonName commonName : commonNameList) {
          Map<Term, String> vernacularName = new HashMap<>();
          vernacularName.put(DcTerm.language, commonName.language);
          vernacularName.put(DwcTerm.vernacularName, commonName.taxonname);
          writer.addExtensionRecord(GbifTerm.VernacularName, vernacularName);
        }

        /*
         * Including the narrative requires a permission waiver from the IUCN.
         * (Email, mblissett/arodrigues/mgrosjean; 2020-09-10.)
        List<IucnNarrative> narrativeList = http.readJsonResult(NARRATIVE, IucnNarrative.class);
        for (IucnNarrative narrative : narrativeList) {
          addDescription("general", narrative.taxonomicnotes, citation);
          addDescription("conservation", narrative.rationale, citation);
          addDescription("distribution", narrative.geographicrange, citation);
          addDescription("population", narrative.population, citation);
          // addDescription(, narrative.populationtrend, citation); Single word like "increasing"
          addDescription("habitat", narrative.habitat, citation);
          addDescription("threats", narrative.threats, citation);
          addDescription("conservation", narrative.conservationmeasures, citation);
          addDescription("use", narrative.usetrade, citation);
        }
         */

        // Synonyms make new records, so they must be last.
        List<IucnSynonym> synonymList = http.readJsonResult(SYNONYM.replace("{NAME}", urlName), IucnSynonym.class);
        int synonym_index = 0;
        for (IucnSynonym synonym : synonymList) {
          synonym_index++;

          synonym.syn_authority = synonym.syn_authority.replace("&amp;", "&");

          writer.newRecord(String.valueOf(species.taxonid) + "_" + synonym_index);
          writer.addCoreColumn(DwcTerm.scientificName, synonym.synonym + ' ' + synonym.syn_authority);
          writer.addCoreColumn(DwcTerm.kingdom, species.kingdom); // Assume synonym is same kingdom as accepted name
          writer.addCoreColumn(DwcTerm.scientificNameAuthorship, synonym.syn_authority);
          writer.addCoreColumn(DwcTerm.taxonomicStatus, TaxonomicStatus.SYNONYM);
          writer.addCoreColumn(DwcTerm.acceptedNameUsageID, species.taxonid);
        }

        LOG.info("  Taxon {} ({}) with {} synonyms completed.", taxonKey, species.scientific_name, synonym_index);
      }
    }
  }

  private void addDescription(String type, String descr, String citation) throws IOException {
    if (!Strings.isNullOrEmpty(descr)) {
      Map<Term, String> description = new HashMap<>();
      String cleanDescr = SPAN_ITALIC.matcher(descr).replaceAll("<i>$1</i>");
      cleanDescr = SPAN_STRONG.matcher(cleanDescr).replaceAll("<strong>$1</strong>");
      description.put(DcTerm.description, cleanDescr);
      description.put(DcTerm.type, type);
      description.put(DcTerm.source, citation);
      writer.addExtensionRecord(GbifTerm.Description, description);
    }
  }

  public static class IucnVersion {
    public String version;
  }

  public static class IucnSpecies {
    public Long taxonid;
    public String scientific_name;
    public String kingdom;
    public String phylum;
    @JsonProperty("class")
    public String class_;
    public String order;
    public String family;
    public String genus;
    public String main_common_name;
    public String authority;
    public Integer published_year;
    public String assessment_date;
    public String category;
    public String criteria;
    public String population_trend;
    public Boolean marine_system;
    public Boolean freshwater_system;
    public Boolean terrestrial_system;
    public String assessor;
    public String reviewer;
    public String aoo_km2;
    public String eoo_km2;
    public String elevation_upper;
    public String elevation_lower;
    public String depth_upper;
    public String depth_lower;
    public String errata_flag;
    public String errata_reason;
    public String amended_flag;
    public String amended_reason;
  }

  public static class IucnCitation {
    public String citation;
  }

  public static class IucnCountry {
    public String code;
    public String country;
    public String presence;
    public String origin;
    public String distribution_code;
  }

  public static class IucnCommonName {
    public String taxonname;
    public Boolean primary;
    public String language;
  }

  public static class IucnSynonym {
    public Long accepted_id;
    public String accepted_name;
    public String authority;
    public String synonym;
    public String syn_authority;
  }

  public static class IucnNarrative {
    public Long species_id;
    public String taxonomicnotes;
    public String rationale;
    public String geographicrange;
    public String population;
    public String populationtrend;
    public String habitat;
    public String threats;
    public String conservationmeasures;
    public String usetrade;
  }

  private String getCitation() {
    return CITATION_FORMAT
      .replace("{YEAR}", version.substring(0, 4))
      .replace("{VERSION}", version)
      .replace("{DL_DATE}", LocalDate.now(ZoneOffset.UTC).toString());
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
