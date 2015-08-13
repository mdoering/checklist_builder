package de.doering.dwca.iocwbn;

import org.gbif.api.model.registry.Citation;
import org.gbif.api.model.registry.Dataset;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.common.parsers.date.DateParseUtils;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwca.io.DwcaWriter;
import org.gbif.dwca.io.SimpleSaxHandler;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *
 */
public class IocXmlHandler extends SimpleSaxHandler {

  private Splitter commaSplit = Splitter.on(',').omitEmptyStrings().trimResults();

  private final DwcaWriter writer;
  private final Dataset eml;

  private boolean meta=false;
  private int id = 1000;

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

  public IocXmlHandler(DwcaWriter writer, Dataset eml) throws IOException {
    this.writer = writer;
    this.eml = eml;

    // Root classification
    current = new Taxon();
    current.id=1;
    current.name="Animalia";
    current.rank="kingdom";
    writeCurrent();

    parents.add(current);
    current = new Taxon();
    current.id=10;
    current.name="Aves";
    current.rank="class";
    writeCurrent();

    parents.add(current);
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    super.startElement(uri, localName, qName, attributes);

    if ("meta".equalsIgnoreCase(qName)) {
      meta = true;
    }

    if ("order".equalsIgnoreCase(qName) || "family".equalsIgnoreCase(qName) || "genus".equalsIgnoreCase(qName) || "species".equalsIgnoreCase(qName)){
      // append current to parents list
      if (current!=null){
        parents.add(current);
      }
      // start new current
      current = new Taxon();
      current.id = id++;
      current.rank=qName;
    }

  }

  private void writeCurrent() throws IOException {
    if (current == null) {
      return;
    }

    writer.newRecord(current.id.toString());
    writer.addCoreColumn(DwcTerm.scientificName, current.name);
    writer.addCoreColumn(DwcTerm.taxonRank, current.rank);
    writer.addCoreColumn(DwcTerm.taxonRemarks, current.note);
    if (!parents.isEmpty()){
      writer.addCoreColumn(DwcTerm.parentNameUsageID, parents.getLast().id.toString());
    }

    Map<Term, String> data = new HashMap<Term, String>();
    data.put(DwcTerm.vernacularName, current.englishName);
    data.put(DcTerm.language, "en");
    writer.addExtensionRecord(GbifTerm.VernacularName, data);

    // distribution only for higher region;
    if (current.breedingRegions != null){
      for (String area : commaSplit.split(current.breedingRegions)){
        if (areaLookup.containsKey(area.toUpperCase())){
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
    if (current.breedingRegions != null){
      distribution.append("Breeding regions are ");
      distribution.append(current.breedingRegions);
      distribution.append(". ");
    }
    if (current.breedingSubregions != null){
      distribution.append("Breeding subregions are ");
      distribution.append(current.breedingSubregions);
      distribution.append(". ");
    }
    if (current.nonbreedingRegions != null){
      distribution.append("Non breeding regions are ");
      distribution.append(current.nonbreedingRegions);
      distribution.append(". ");
    }
    String d = distribution.toString().trim();
    if (!d.isEmpty()){
      data = new HashMap<Term, String>();
      data.put(DcTerm.description, d);
      data.put(DcTerm.type, "Distribution");
      writer.addExtensionRecord(GbifTerm.Description, data);
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {
    super.endElement(uri, localName, qName);

    if ("meta".equalsIgnoreCase(qName)) {
      meta = false;
    }

    if (meta){
      if ("title".equalsIgnoreCase(qName)) {
        eml.setTitle(content);
      }else if ("cite".equalsIgnoreCase(qName)) {
          Citation cite = new Citation();
          cite.setText(content);
          eml.setCitation(cite);
      }else if ("generated".equalsIgnoreCase(qName)) {
        ParseResult<Date> result = DateParseUtils.parse(content);
        if (result.isSuccessful()) {
            eml.setPubDate(result.getPayload());
        } else {
            log.warn("Cannot parse generated date");
        }
      }else if ("version".equalsIgnoreCase(qName)) {
      }else if ("species_count".equalsIgnoreCase(qName)) {
      }

    } else {

      if ("order".equalsIgnoreCase(qName) || "family".equalsIgnoreCase(qName) || "genus".equalsIgnoreCase(qName) || "species".equalsIgnoreCase(qName)){
        // closing current, write taxon to file
        try {
          writeCurrent();
        } catch (IOException e) {
          throw new SAXException(e);
        }
        // retrieve last from stack
        if (parents.isEmpty()){
          current = null;
        } else {
          current = parents.removeLast();
        }

      } else if ("latin_name".equalsIgnoreCase(qName)){
        if ("species".equalsIgnoreCase(current.rank)){
          current.name = parents.getLast().name + " " + content;
        } else {
          current.name = content;
        }
      }else if ("english_name".equalsIgnoreCase(qName)){
        current.englishName = content;
      }else if ("breeding_regions".equalsIgnoreCase(qName)){
        current.breedingRegions = content;
      }else if ("breeding_subregions".equalsIgnoreCase(qName)){
        current.breedingSubregions = content;
      }else if ("nonbreeding_regions".equalsIgnoreCase(qName)){
        current.nonbreedingRegions = content;
      }else if ("code".equalsIgnoreCase(qName)){
        current.code = content;
      }else if ("note".equalsIgnoreCase(qName)){
        current.note = content;
      }
    }

  }

}
