package de.doering.dwca.flickr;

import org.gbif.dwc.terms.ConceptTerm;
import org.gbif.dwc.terms.DwcTerm;

import java.util.Date;
import java.util.Map;

import com.google.common.collect.Maps;

public class FlickrImage {
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
  private Map<ConceptTerm,String> attributes = Maps.newHashMap();

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

  public Map<ConceptTerm, String> getAttributes() {
    return attributes;
  }

  public String getAttribute(ConceptTerm term) {
    return attributes.get(term);
  }

  public void setAttribute(ConceptTerm term, String value) {
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
