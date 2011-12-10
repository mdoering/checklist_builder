package de.doering.dwca.flickr;

import org.gbif.dwc.terms.ConceptTerm;
import org.gbif.dwc.terms.DwcTerm;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;

import com.aetrion.flickr.Flickr;
import com.aetrion.flickr.FlickrException;
import com.aetrion.flickr.REST;
import com.aetrion.flickr.Transport;
import com.aetrion.flickr.photos.Photo;
import com.aetrion.flickr.photos.PhotoList;
import com.aetrion.flickr.photos.SearchParameters;
import com.aetrion.flickr.tags.Tag;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlickrOccurrenceSearch {
  private Logger log = LoggerFactory.getLogger(getClass());
  private Pattern SPLIT_MTAG = Pattern.compile("([a-z]+:[a-z0-9_]+)=(.+)", Pattern.CASE_INSENSITIVE);
  private final int PAGESIZE = 100;
  private final int MAX_RETRIES = 2;
  private static final String API_KEY = "59c1f626e17ddc0e37160b56d7b21ea3";
  private static final String SECRET = "56cf7af06a966665";
  private static final SearchParameters PARAMS = new SearchParameters();

  private static final Map<String, ConceptTerm> TAG_MAPPING = Maps.newHashMap();
  private Flickr f;

  private static void addTagMappings(ConceptTerm term , String ... tags){
    for (String t : tags){
      TAG_MAPPING.put(t, term);
    }
  }

  static {
    String[] x = {"darwincore:scientificname","dwc:scientificname","taxonomy:binomial","taxonomy:trinomial","taxonomy:binominal","taxonomy:latinname"};
    List<String> tags = Lists.newArrayList();
    for (String t : x){
      tags.add(t+"=");
      TAG_MAPPING.put(t, DwcTerm.scientificName);
    }

    addTagMappings(DwcTerm.scientificNameAuthorship, "darwincore:scientificNameAuthorship","dwc:scientificNameAuthorship","taxonomy:author","taxonomy:authority","taxonomy:author");
    addTagMappings(DwcTerm.kingdom, "darwincore:kingdom","dwc:kingdom","taxonomy:kingdom","taxonomy:domain");
    addTagMappings(DwcTerm.phylum, "darwincore:phylum","dwc:phylum","taxonomy:phylum");
    addTagMappings(DwcTerm.classs, "darwincore:class","dwc:class","taxonomy:class");
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

    PARAMS.setMachineTags(tags.toArray(new String[tags.size()]));
    PARAMS.setLicense("1,2,3,4,5,6");
    Set<String> extras = Sets.newHashSet();
    extras.add("description");
    extras.add("date_taken");
    extras.add("owner_name");
    extras.add("license");
    extras.add("geo");
    extras.add("url_sq");
    extras.add("url_o");
    extras.add("url_l");
    PARAMS.setExtras(extras);
    PARAMS.setSafeSearch(Flickr.SAFETYLEVEL_MODERATE);
  }

  public FlickrOccurrenceSearch() {
    try {
      Transport transport = new REST();
      f = new Flickr(API_KEY, SECRET, transport);
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("Cant init flickr");
    }
  }

  public List<FlickrImage> list(int page) {
    return list(page, 0);
  }

  public List<FlickrImage> list(int page, int retries) {
    List<FlickrImage> images = Lists.newArrayList();

    try {
      PhotoList list = f.getPhotosInterface().search(PARAMS, PAGESIZE, page);
      log.debug("Found {} new images on page {}, extracting...", list.size(), page);
      for (Iterator iterator = list.iterator(); iterator.hasNext(); ) {
        Photo photo = (Photo) iterator.next();
        FlickrImage img = extract(photo);
        if (img!=null && !Strings.isNullOrEmpty(img.getScientificName())){
          images.add(img);
        }
      }
    } catch (Exception e) {
      if (retries < MAX_RETRIES){
        log.warn("Failed to search for photo page {} - try another time", page, e);
        retries++;
        images = list(page, retries);
      }else{
        log.error("Failed to search for photo page {} after "+retries+" retries", page, e);
      }
    }

    return images;
  }

  private FlickrImage extract(Photo photo) {
    FlickrImage img = new FlickrImage();
    img.setId(photo.getId());
    img.setLink(photo.getUrl());
    img.setTitle(photo.getTitle());
    img.setDescription(photo.getDescription());
    img.setLicense(photo.getLicense());
    img.setOwner(photo.getOwner().getRealName());
    img.setThumb(photo.getSmallSquareUrl());
    img.setDateRecorded(photo.getDateTaken());
    if (photo.hasGeoData()){
      img.setLongitude(photo.getGeoData().getLongitude());
      img.setLatitude(photo.getGeoData().getLatitude());
      img.setAccuracy(photo.getGeoData().getAccuracy());
    }
    try {
      img.setImage(photo.getOriginalUrl());
    } catch (FlickrException e) {
      img.setImage(photo.getLargeUrl());
    }

    Map<String, String> tags = buildTagMap(photo);
    for (String key: tags.keySet()){
      if (TAG_MAPPING.containsKey(key)){
        img.setAttribute(TAG_MAPPING.get(key), Strings.emptyToNull(tags.get(key).trim()));
      }
    }

    // only return images with a scientific name
    return img.getScientificName() != null ? img : null;
  }

  private Map<String, String> buildTagMap(Photo photo){
    Map<String, String> map = Maps.newHashMap();
    // call flickr for each photo again to get the raw list of tags :(
    try {
      Collection<Tag> tags = f.getTagsInterface().getListPhoto(photo.getId()).getTags();
      for (Tag t : tags){
        Matcher m = SPLIT_MTAG.matcher(t.getRaw());
        if (m.find()){
          map.put(m.group(1), m.group(2));
        }
      }
    } catch (Exception e) {
      log.warn("Cannot retrieve tags for photo {} {}", photo.getId(), photo.getUrl());
    }

    return map;
  }
}
