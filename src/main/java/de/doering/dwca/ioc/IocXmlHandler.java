package de.doering.dwca.ioc;

import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwca.io.DwcaWriter;
import org.gbif.dwca.io.SimpleSaxHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *
 */
public class IocXmlHandler extends SimpleSaxHandler {

  private Splitter commaSplit = Splitter.on(',').omitEmptyStrings().trimResults();

  private final DwcaWriter writer;
  private String version;
  private String year;

  private int id = 1000;

  private Set<String> rankTags = ImmutableSet.of("order", "family", "genus", "species", "subspecies");

  private LinkedList<Taxon> parents = Lists.newLinkedList();
  private Taxon current;

  private static Map<String, String> areaLookup = Maps.newHashMap();

  static {
    areaLookup.put("NA", "North America");
    areaLookup.put("MA", "Middle America");
    areaLookup.put("SA", "South America");
    areaLookup.put("LA", "Latin America");
    areaLookup.put("AF", "Africa");
    areaLookup.put("EU", "Eurasia");
    areaLookup.put("OR", "Oriental Region");
    areaLookup.put("AU", "Australasia");
    areaLookup.put("AO", "Atlantic Ocean");
    areaLookup.put("PO", "Pacific Ocean");
    areaLookup.put("IO", "Indian Ocean");
    areaLookup.put("TRO", "Tropical Ocean");
    areaLookup.put("TO", "Temperate Ocean");
    areaLookup.put("NO", "Northern Oceans");
    areaLookup.put("SO", "Southern Oceans");
    areaLookup.put("AN", "Antarctica");
    areaLookup.put("SO. CONE", "Southern Cone");
  }

  public IocXmlHandler(DwcaWriter writer) throws IOException {
    this.writer = writer;

    // Root classification
    current = new Taxon();
    current.id = 1;
    current.name = "Animalia";
    current.rank = "kingdom";
    writeCurrent();

    parents.add(current);
    current = new Taxon();
    current.id = 10;
    current.name = "Aves";
    current.rank = "class";
    writeCurrent();
  }

  public String getVersion() {
    return version;
  }

  public String getYear() {
    return year;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    super.startElement(uri, localName, qName, attributes);

    if ("ioclist".equalsIgnoreCase(qName)) {
      year = attributes.getValue("year");
      version = attributes.getValue("version");
    }

    if (rankTags.contains(qName)) {
      // append current to parents list
      if (current != null) {
        parents.add(current);
      }
      // start new current
      current = new Taxon();
      current.id = id++;
      current.rank = qName;
      current.extinct = Strings.nullToEmpty(attributes.getValue("extinct")).equalsIgnoreCase("yes");
    }
  }

  private void writeCurrent() throws IOException {
    if (current == null || writer == null) {
      return;
    }

    writer.newRecord(current.id.toString());
    writer.addCoreColumn(DwcTerm.scientificName, current.name);
    writer.addCoreColumn(DwcTerm.scientificNameAuthorship, current.authority);
    writer.addCoreColumn(DwcTerm.taxonRank, current.rank);
    writer.addCoreColumn(DwcTerm.taxonRemarks, current.note);
    if (!parents.isEmpty()) {
      writer.addCoreColumn(DwcTerm.parentNameUsageID, parents.getLast().id.toString());
    }

    Map<Term, String> data = new HashMap<Term, String>();
    if (!Strings.isNullOrEmpty(current.englishName)) {
      data.put(DwcTerm.vernacularName, current.englishName);
      data.put(DcTerm.language, "en");
      writer.addExtensionRecord(GbifTerm.VernacularName, data);
    }

    // distribution only for higher region;
    if (current.breedingRegions != null) {
      for (String area : commaSplit.split(current.breedingRegions)) {
        if (areaLookup.containsKey(area.toUpperCase())) {
          area = areaLookup.get(area.toUpperCase());
        }
        data = new HashMap<Term, String>();
        data.put(DwcTerm.locality, area);
        data.put(DwcTerm.occurrenceStatus, "present");
        data.put(DwcTerm.occurrenceRemarks, "Breeding region");
        writer.addExtensionRecord(GbifTerm.Distribution, data);
      }
    }


    // distribution description
    StringBuffer distribution = new StringBuffer();
    if (current.breedingRegions != null) {
      distribution.append("Breeding regions are ");
      distribution.append(current.breedingRegions);
      distribution.append(". ");
    }
    if (current.breedingSubregions != null) {
      distribution.append("Breeding subregions are ");
      distribution.append(current.breedingSubregions);
      distribution.append(". ");
    }
    if (current.nonbreedingRegions != null) {
      distribution.append("Non breeding regions are ");
      distribution.append(current.nonbreedingRegions);
      distribution.append(". ");
    }
    String d = distribution.toString().trim();
    if (!d.isEmpty()) {
      data = new HashMap<Term, String>();
      data.put(DcTerm.description, d);
      data.put(DcTerm.type, "Distribution");
      writer.addExtensionRecord(GbifTerm.Description, data);
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {
    super.endElement(uri, localName, qName);

    if (rankTags.contains(qName)) {
      // closing current, write taxon to file
      try {
        writeCurrent();
      } catch (IOException e) {
        throw new SAXException(e);
      }
      // retrieve last from stack
      if (parents.isEmpty()) {
        current = null;
      } else {
        current = parents.removeLast();
      }

    } else {

      if ("latin_name".equalsIgnoreCase(qName)) {
        if (ImmutableSet.of("species", "subspecies").contains(current.rank)) {
          current.name = parents.getLast().name + " " + content;
        } else if (current.rank.equalsIgnoreCase("order")) {
          current.name = StringUtils.capitalize(content.toLowerCase());
        } else {
          current.name = content;
        }

      } else if ("authority".equalsIgnoreCase(qName)) {
        current.authority = content;

      } else if ("english_name".equalsIgnoreCase(qName)) {
        current.englishName = content;

      } else if ("breeding_regions".equalsIgnoreCase(qName)) {
        current.breedingRegions = content;

      } else if ("breeding_subregions".equalsIgnoreCase(qName)) {
        current.breedingSubregions = content;

      } else if ("nonbreeding_regions".equalsIgnoreCase(qName)) {
        current.nonbreedingRegions = content;

      } else if ("code".equalsIgnoreCase(qName)) {
        current.code = content;

      } else if ("note".equalsIgnoreCase(qName)) {
        current.note = content;
      }
    }
  }

}
