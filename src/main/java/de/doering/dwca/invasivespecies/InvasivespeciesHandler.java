package de.doering.dwca.invasivespecies;

import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwca.io.DwcaWriter;
import org.gbif.dwca.io.SimpleSaxHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *
 */
public class InvasivespeciesHandler extends SimpleSaxHandler {

  protected static Pattern abbrevGenus = Pattern.compile("^[A-Z]\\.");
  protected static Pattern extractSynonym= Pattern.compile("\\(=(.+)\\)");
  private Pattern removeBrakets = Pattern.compile("[\\(\\)]");
  private Pattern removeHyphen = Pattern.compile("^[ -]+");
  private Pattern idPattern = Pattern.compile("si=([0-9]+)");
  private Splitter commaSplit = Splitter.on(',').omitEmptyStrings().trimResults();
  private DwcaWriter writer;
  private boolean started = false;
  private boolean inDt = false;
  private String id;
  private String sciname;
  private String link;
  private List<String> vernaculars = Lists.newArrayList();
  private String synonym;
  private Map<Term, String> classification;
  private Attributes attributes;
  private String baseUrl;

  private Set<String> missingCategories = Sets.newHashSet();
  private static Map<String, Map<Term,String>> classificationLookup = Maps.newHashMap();
  static {
    classificationLookup.put("reptile", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Chordata", DwcTerm.class_, "Reptilia"));
    classificationLookup.put("mammal", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Chordata", DwcTerm.class_, "Mammalia"));
    classificationLookup.put("bird", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Chordata", DwcTerm.class_, "Aves"));
    classificationLookup.put("amphibian", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Chordata", DwcTerm.class_, "Amphibia"));
    classificationLookup.put("fish", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia"));
    classificationLookup.put("jellyfish", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Cnidaria"));
    classificationLookup.put("mollusc", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Mollusca"));
    classificationLookup.put("insect", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Arthropoda", DwcTerm.class_, "Insecta"));
    classificationLookup.put("arachnid", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Arthropoda", DwcTerm.class_, "Arachnida"));
    classificationLookup.put("insect, arachnid", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Arthropoda", DwcTerm.class_, "Arachnida"));
    classificationLookup.put("crustacean", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Arthropoda"));
    classificationLookup.put("flatworm", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Platyhelminthes"));
    classificationLookup.put("annelid", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Annelida"));
    classificationLookup.put("bryozoan", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Bryozoa"));

    classificationLookup.put("comb jelly", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Ctenophora"));
    classificationLookup.put("sea star", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Echinodermata", DwcTerm.class_, "Asteroidea"));
    classificationLookup.put("tunicate", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Chordata"));
    classificationLookup.put("nematode", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Nematoda"));
    classificationLookup.put("coral", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Cnidaria", DwcTerm.class_, "Anthozoa"));
    classificationLookup.put("sponge", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Porifera"));
    classificationLookup.put("centipede/millipede", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Arthropoda"));
    classificationLookup.put("collembola, springtail", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Animalia", DwcTerm.phylum, "Arthropoda", DwcTerm.class_, "Entognatha"));

    classificationLookup.put("grass", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.order, "Poales"));
    classificationLookup.put("grass, tree", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.order, "Poales"));
    classificationLookup.put("aquatic plant, grass", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.order, "Poales"));
    classificationLookup.put("sedge", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.family, "Cyperaceae"));
    classificationLookup.put("aquatic plant, sedge", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.family, "Cyperaceae"));
    classificationLookup.put("rush", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.family, "Juncaceae"));
    classificationLookup.put("fern", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.phylum, "Pteridophyta"));
    classificationLookup.put("herb, fern",ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.phylum, "Pteridophyta"));
    classificationLookup.put("vine, climber, fern",ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.phylum, "Pteridophyta"));
    classificationLookup.put("aquatic plant, fern",ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.phylum, "Pteridophyta"));

    classificationLookup.put("palm", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.family, "Arecaceae"));
    classificationLookup.put("tree, palm", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.family, "Arecaceae"));

    classificationLookup.put("aquatic plant", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("aquatic plant, herb", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("aquatic plant, succulent", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("vine, climber", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("herb", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("herb, vine, climber", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("tree", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("tree, shrub", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("tree, shrub, succulent", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("vine, climber, shrub", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("herb, succulent", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("aquatic plant, tree, shrub", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("vine, climber, tree, shrub", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("herb, shrub", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));
    classificationLookup.put("herb, shrub", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));

    classificationLookup.put("succulent", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae", DwcTerm.phylum, "Magnoliophyta"));
    classificationLookup.put("shrub", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Plantae"));

    classificationLookup.put("fungus", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Fungi"));
    classificationLookup.put("alga", ImmutableMap.<Term, String>of());
    classificationLookup.put("aquatic plant, alga", ImmutableMap.<Term, String>of());
    classificationLookup.put("oomycete", ImmutableMap.<Term, String>of(DwcTerm.kingdom, "Chromista", DwcTerm.phylum, "Oomycota"));
    classificationLookup.put("micro-organism", ImmutableMap.<Term, String>of());
  }

  public InvasivespeciesHandler(DwcaWriter writer, String baseUrl) {
    this.writer = writer;
    this.baseUrl = baseUrl;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    super.startElement(uri, localName, qName, attributes);

    if ("dl".equalsIgnoreCase(qName)) {
      started = true;
    }
    if (started) {
      // remember for end method
      this.attributes = attributes;
    }
    if (started && "dt".equalsIgnoreCase(qName)) {
      // new name, write old one
      try {
        writeName();
      } catch (IOException e) {
        throw new SAXException(e);
      }

      // reset
      inDt = true;
      id = null;
      sciname = null;
      link = null;
      synonym = null;
      vernaculars.clear();
      classification = null;

    }
    if (started && "a".equalsIgnoreCase(qName)) {
      link = baseUrl + Strings.emptyToNull(attributes.getValue("href"));
      Matcher m = idPattern.matcher(link);
      if (m.find()){
        id = m.group(1);
      };
    }

  }

  private void writeName() throws IOException {
    if (sciname == null || id == null) {
      log.warn("Cant find scientific name or id, skip record {}", id);
      return;
    }

    writer.newRecord(id);
    writer.addCoreColumn(DwcTerm.scientificName, sciname);
    writer.addCoreColumn(DcTerm.source, link);

    for (String v : vernaculars) {
      Map<Term, String> data = new HashMap<Term, String>();
      data.put(DwcTerm.vernacularName, v);
      writer.addExtensionRecord(GbifTerm.VernacularName, data);
    }

    boolean classified=false;
    if (classification != null) {
      for (Term t : classification.keySet()){
        if (!Strings.isNullOrEmpty(classification.get(t))){
          classified=true;
          writer.addCoreColumn(t, classification.get(t));
        }
      }
    }
    if (!classified){
      writer.addCoreColumn(DwcTerm.kingdom, "Incertae sedis");
    }

    if (synonym != null){
      writer.newRecord(id+"-syn");
      writer.addCoreColumn(DwcTerm.scientificName, synonym);
      writer.addCoreColumn(DwcTerm.taxonomicStatus, "synonym");
      writer.addCoreColumn(DwcTerm.acceptedNameUsageID, id);
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {
    super.endElement(uri, localName, qName);

    if ("dt".equalsIgnoreCase(qName)) {
      inDt = false;
    }
    if (started && "i".equalsIgnoreCase(qName)) {
      sciname = Strings.emptyToNull(content);
      // check for synonyms
      Matcher m = extractSynonym.matcher(sciname);
      if (m.find()){
        synonym = m.group(1).trim();
        sciname = m.replaceFirst("").trim();
        log.debug("Synonym for {} found: {}", sciname, synonym);
        // allow abbreviated genera
        Matcher abbrev = abbrevGenus.matcher(synonym);
        if (abbrev.find()){
          String[] parts = StringUtils.split(sciname, " ");
          String oldSyn = synonym;
          synonym = abbrev.replaceFirst(parts[0]);
          log.debug("Abbreviated genus found: {} => {}", oldSyn, synonym);
        }

      }
    }
    if (started && inDt && "span".equalsIgnoreCase(qName) && attributes != null && "ListDetail" .equalsIgnoreCase(attributes.getValue("class"))) {
      String category = Strings.emptyToNull(removeBrakets.matcher(Strings.nullToEmpty(content)).replaceAll("").trim().toLowerCase());
      if (classificationLookup.containsKey(category)){
        classification = classificationLookup.get(category);
      } else if (!missingCategories.contains(category)) {
        missingCategories.add(category);
        log.warn("Unknown category {}", category);
      }
    }
    if (started && !inDt && "span".equalsIgnoreCase(qName) && attributes != null && "ListDetail"
      .equalsIgnoreCase(attributes.getValue("class"))) {
      for (String v : commaSplit.split(Strings.nullToEmpty(content))) {
        vernaculars.add(removeHyphen.matcher(v.trim()).replaceAll(""));
      }
    }
  }
}
