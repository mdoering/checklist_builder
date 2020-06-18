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
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.codehaus.jackson.map.ObjectMapper;
import org.gbif.api.model.registry.Contact;
import org.gbif.api.vocabulary.ContactType;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.Language;
import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.IucnTerm;
import org.gbif.dwc.terms.Term;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ArchiveBuilder extends AbstractBuilder {

  // metadata
  private static final String TITLE = "IUCN Red List of Threatened Species";
  private static final URI HOMEPAGE = URI.create("https://www.iucnredlist.org/");
  private static final URI LOGO = URI.create("https://raw.githubusercontent.com/mdoering/checklist_builder/master/src/main/resources/iucn/IUCN_Red_List.svg");
  private static final String CONTACT_ORG = "International Union for Conservation of Nature";

  public static final Pattern SPAN_ITALIC = Pattern.compile("<span style=\"font-style: italic;\">(.*?)</span>");
  public static final Pattern SPAN_STRONG = Pattern.compile("<span style=\"font-weight: bold;\">(.*?)</span>");
  public static final Pattern DX_DOI = Pattern.compile("https?://dx\\.doi\\.org/");

  private ObjectMapper jsonMapper = new ObjectMapper();
  // This is just the documentation/demo token.
  private static final String TOKEN = "9bb4facb6d23f48efbf424bb05c0c1ef1cf6f468393bc745d42179ac4aca5fee";

  //  private static final String SPECIES = "https://apiv3.iucnredlist.org/api/v3/species/id/{KEY}?token="+TOKEN;
  private static final String SPECIES = "http://mb.gbif.org/iucn/species.json";
  private static final String CITATION = "http://mb.gbif.org/iucn/citation.json";
  private static final String COUNTRY = "http://mb.gbif.org/iucn/countries.json";
  private static final String COMMON_NAME = "http://mb.gbif.org/iucn/common.json";
  private static final String NARRATIVE = "http://mb.gbif.org/iucn/narrative.json";

  @Inject
  public ArchiveBuilder(CliConfiguration cfg) {
    super(DatasetType.CHECKLIST, cfg);
  }

  /**
   */
  @Override
  protected void parseData() throws Exception {
    dataset.setPubDate(new Date());

    writer.addDefaultValue(GbifTerm.Description, DcTerm.language, Language.ENGLISH.getIso3LetterCode());
    writer.addDefaultValue(GbifTerm.Description, DcTerm.rightsHolder, "IUCN");

    // TODO: Page through all species records.
    List<String> taxa = Lists.newArrayList("12392");

    for (String taxonKey : taxa) {
      String url = SPECIES.replace("{KEY}", taxonKey);
      List<IucnSpecies> speciesList = http.readJsonResult(url, IucnSpecies.class);

      List<IucnCitation> citationList = http.readJsonResult(CITATION, IucnCitation.class);
      String citation = DX_DOI.matcher(citationList.get(0).citation).replaceAll("https://doi.org/");

      // Taxon.rights?
      // Taxon.rightsHolder?

      // License?

      for (IucnSpecies species : speciesList) {
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
        writer.addCoreColumn(DwcTerm.namePublishedInYear, species.published_year);
        writer.addCoreColumn(DwcTerm.taxonomicStatus, TaxonomicStatus.ACCEPTED);
        writer.addCoreColumn(DcTerm.bibliographicCitation, citation);
        writer.addCoreColumn(DcTerm.references, "https://apiv3.iucnredlist.org/api/v3/taxonredirect/" + species.taxonid);

        Map<Term, String> globalDistribution = new HashMap<>();
        globalDistribution.put(DwcTerm.locality, "Global");
        globalDistribution.put(IucnTerm.threatStatus, species.category);
        switch (species.category) {
          case "EX":
          case "EW":
            globalDistribution.put(DwcTerm.occurrenceStatus, "Absent");
            break;

          case "CR":
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
        globalDistribution.put(DcTerm.source, citation);
        writer.addExtensionRecord(GbifTerm.Distribution, globalDistribution);

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

        List<IucnCommonName> commonNameList = http.readJsonResult(COMMON_NAME, IucnCommonName.class);
        for (IucnCommonName commonName : commonNameList) {
          Map<Term, String> vernacularName = new HashMap<>();
          vernacularName.put(DcTerm.language, commonName.language);
          vernacularName.put(DwcTerm.vernacularName, commonName.taxonname);
          vernacularName.put(DcTerm.source, citation);
          writer.addExtensionRecord(GbifTerm.VernacularName, vernacularName);
        }

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

  @Override
  protected void addMetadata() {
    // metadata
    dataset.setTitle(TITLE);
    setCitation(CITATION);
    setDescription("iucn/description.txt");
    dataset.setRights("Unknown?");
    dataset.setHomepage(HOMEPAGE);
    dataset.setLogoUrl(LOGO);
    Contact contact = new Contact();
    contact.setType(ContactType.ORIGINATOR);
    contact.setOrganization(CONTACT_ORG);
//    contact.getAddress().add(CONTACT_ADDRESS);
//    contact.getAddress().add(CONTACT_ADDRESS2);
//    contact.setCity(CONTACT_CITY);
//    contact.setPostalCode(CONTACT_ZIP);
//    contact.setCountry(CONTACT_COUNTRY);
//    contact.setFirstName(CONTACT_FIRST_NAME);
//    contact.setLastName(CONTACT_LAST_NAME);
//    contact.setPostalCode(CONTACT_POSITION);
//    contact.getEmail().add(CONTACT_EMAIL);
    addContact(contact);
  }

  protected void addMetadataProvider() {
    addContact("GBIF", "Matthew", "Blissett", "mblissett@gbif.org", ContactType.METADATA_AUTHOR);
  }
}
