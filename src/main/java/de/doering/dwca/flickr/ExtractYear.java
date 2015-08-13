package de.doering.dwca.flickr;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.DwcTerm;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
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

public class ExtractYear implements Runnable {
  private Logger log = LoggerFactory.getLogger(getClass());
  private Pattern SPLIT_MTAG = Pattern.compile("([a-z]+:[a-z0-9_]+)=(.+)", Pattern.CASE_INSENSITIVE);
  private final int PAGESIZE = 100;
  private final int MAX_PAGES = 10;
  private static final String API_KEY = "59c1f626e17ddc0e37160b56d7b21ea3";
  private static final String SECRET = "56cf7af06a966665";
  private final SearchParameters PARAMS = new SearchParameters();
  private final int year;
  private Date minSearched;
  private int currPage;
  private final ImageWriter imgWriter;

  private static final Map<String, Term> TAG_MAPPING = Maps.newHashMap();
  private Flickr f;

  private static void addTagMappings(Term term , String ... tags){
    for (String t : tags){
      TAG_MAPPING.put(t, term);
    }
  }

  static {
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


  /**
   *
   * @param year the year to scan
   * @param imgWriter
   */
  public ExtractYear(int year, ImageWriter imgWriter) {
    this.year = year;
    this.currPage  = 1;
    this.imgWriter = imgWriter;

    try {
      Transport transport = new REST();
      f = new Flickr(API_KEY, SECRET, transport);
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("Cant init flickr");
    }

    String[] x = {"darwincore:scientificname","dwc:scientificname","taxonomy:binomial","taxonomy:trinomial","taxonomy:binominal","taxonomy:latinname"};
    List<String> tags = Lists.newArrayList();
    for (String t : x){
      tags.add(t+"=");
      TAG_MAPPING.put(t, DwcTerm.scientificName);
    }
    PARAMS.setMachineTags(tags.toArray(new String[tags.size()]));
    PARAMS.setSafeSearch(Flickr.SAFETYLEVEL_MODERATE);
    PARAMS.setLicense("1,2,3,4,5,6");
    Set<String> extras = Sets.newHashSet();
    extras.add("description");
    extras.add("date_upload");
    extras.add("date_taken");
    extras.add("owner_name");
    extras.add("license");
    extras.add("geo");
    extras.add("url_sq");
    extras.add("url_o");
    extras.add("url_l");
    PARAMS.setExtras(extras);
    PARAMS.setMinUploadDate(new Date(year-1900,0,1));
    PARAMS.setMaxUploadDate(new Date(year-1899,0,1));
    PARAMS.setSort(SearchParameters.DATE_POSTED_DESC);
  }

  @Override
  public void run() {
    // call one search after the other until we cant find any more images
    while(true){
      List<FlickrImage> images = search();
      // no more pages?
      if (images == null){
        break;
      }
      // write images
      try {
        imgWriter.writeImages(images);
      } catch (IOException e) {
        log.error("Failed to write images of page {}", currPage);
      }
      currPage++;
      if (currPage % MAX_PAGES == 0){
        // modify search, set new minimum upload date
        PARAMS.setMaxUploadDate(minSearched);
        log.info("Rebuild query with maxUploadDate={}", minSearched);
      }
    }
    log.info("Finishing year {} with {} searched page in total", year, currPage);
  }



  /**
   *
   * @return list of populated images or null if no more images could be found.
   */
  private List<FlickrImage> search() {
    List<FlickrImage> images = Lists.newArrayList();
    try {
      log.debug("Searching page {}", currPage);
      PhotoList list = f.getPhotosInterface().search(PARAMS, PAGESIZE, currPage);
      if (list.isEmpty()){
        return null;
      }
      log.debug("Found {} new images on page {}, extracting...", list.size(), currPage);
      for (Iterator iterator = list.iterator(); iterator.hasNext(); ) {
        Photo photo = (Photo) iterator.next();
        // remember date uploaded
        Date newPosted = photo.getDatePosted();
        if (minSearched==null || newPosted.before(minSearched)){
          minSearched = newPosted;
        }

        FlickrImage img = extract(photo);
        if (img!=null && !Strings.isNullOrEmpty(img.getScientificName())){
          images.add(img);
        }
      }
    } catch (Exception e) {
      log.error("Failed to search for photo page {}", currPage, e);
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
