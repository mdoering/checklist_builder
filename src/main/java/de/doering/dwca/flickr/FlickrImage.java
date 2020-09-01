package de.doering.dwca.flickr;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;

import java.util.Date;
import java.util.Map;
import java.util.Set;

public class FlickrImage {
  public static final Map<String, Term> TAG_MAPPING = Maps.newHashMap();

  private static void addTagMappings(Term term , String ... tags){
    for (String t : tags){
      TAG_MAPPING.put(t, term);
    }
  }

  public static Set<String> getTagMappings(Term term){
    Set<String> tags = Sets.newHashSet();
    for (String t : TAG_MAPPING.keySet()){
      if (term.equals(TAG_MAPPING.get(t))){
        tags.add(t);
      }
    }
    return tags;
  }

  static {
    addTagMappings(DwcTerm.scientificName, "darwincore:scientificname","dwc:scientificname","taxonomy:binomial","taxonomy:trinomial","taxonomy:binominal","taxonomy:latinname");
    addTagMappings(DwcTerm.scientificNameAuthorship, "darwincore:scientificNameAuthorship","dwc:scientificNameAuthorship","taxonomy:author","taxonomy:authority","taxonomy:author");
    addTagMappings(DwcTerm.kingdom, "darwincore:kingdom","dwc:kingdom","taxonomy:kingdom","taxonomy:domain");
    addTagMappings(DwcTerm.phylum, "darwincore:phylum","dwc:phylum","taxonomy:phylum");
    addTagMappings(DwcTerm.class_, "darwincore:class","dwc:class","taxonomy:class");
    addTagMappings(DwcTerm.order, "darwincore:order","dwc:order","taxonomy:order");
    addTagMappings(DwcTerm.family, "darwincore:family","dwc:family","taxonomy:family");
    addTagMappings(DwcTerm.genus, "darwincore:genus","dwc:genus","taxonomy:genus");
    addTagMappings(DwcTerm.country, "darwincore:country","dwc:country","geo:country");
    addTagMappings(DwcTerm.stateProvince, "darwincore:stateProvince","dwc:stateProvince","geo:state");
    addTagMappings(DwcTerm.vernacularName, "darwincore:vernacularname","dwc:vernacularname","taxonomy:common","taxonomy:common_name","taxonomy:commonname");
    addTagMappings(DwcTerm.sex, "darwincore:sex","dwc:sex","taxonomy:sex");
    addTagMappings(DwcTerm.coordinatePrecision, "darwincore:coordinatePrecision","dwc:coordinatePrecision","taxonomy:accuracy");
    addTagMappings(DwcTerm.decimalLatitude, "darwincore:decimalLatitude","dwc:decimalLatitude","taxonomy:decimalLatitude","geo:lat");
    addTagMappings(DwcTerm.decimalLongitude, "darwincore:decimalLongitude","dwc:decimalLongitude","taxonomy:decimalLongitude","geo:lon");
    addTagMappings(DwcTerm.maximumElevationInMeters, "darwincore:maximumElevationInMeters","dwc:maximumElevationInMeters", "darwincore:minimumElevationInMeters","dwc:minimumElevationInMeters","geo:alt","geo:altitude");
  }
  private String id;
  private String link;
  private String image;
  private String thumb;
  private String title;
  private String description;
  private Float longitude;
  private Float latitude;
  private Integer accuracy;
  private String scientificName;
  private Date dateRecorded;
  private String photographer;
  private String owner;
  private String license;
  private Map<Term,String> attributes = Maps.newHashMap();

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  public Date getDateRecorded() {
    return dateRecorded;
  }

  public void setDateRecorded(Date dateRecorded) {
    this.dateRecorded = dateRecorded;
  }

  public String getLicense() {
    return license;
  }

  public void setLicense(String license) {
    this.license = license;
  }

  public String getPhotographer() {
    return photographer;
  }

  public void setPhotographer(String photographer) {
    this.photographer = photographer;
  }

  public Map<Term, String> getAttributes() {
    return attributes;
  }

  public String getAttribute(Term term) {
    return attributes.get(term);
  }

  public void setAttribute(Term term, String value) {
    if (term instanceof DwcTerm){
      DwcTerm dwc = (DwcTerm) term;
      try {
        switch (dwc) {
          case decimalLatitude: setLatitude(Float.valueOf(value)); return;
          case decimalLongitude: setLongitude(Float.valueOf(value)); return;
          case coordinatePrecision: setAccuracy(Integer.valueOf(value)); return;
          case scientificName: setScientificName(value); return;
        }
      } catch (NumberFormatException e) {
        // dont use this value at all
        return;
      }
    }

    this.attributes.put(term, value);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getThumb() {
    return thumb;
  }

  public void setThumb(String thumb) {
    this.thumb = thumb;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

 public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public Float getLongitude() {
    return longitude;
  }

  public void setLongitude(Float longitude) {
    this.longitude = longitude;
  }

  public Float getLatitude() {
    return latitude;
  }

  public void setLatitude(Float latitude) {
    this.latitude = latitude;
  }

  public Integer getAccuracy() {
    return accuracy;
  }

  public void setAccuracy(Integer accuracy) {
    this.accuracy = accuracy;
  }
}
