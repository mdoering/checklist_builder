package de.doering.dwca.flickr;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;

import java.time.Year;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.Transport;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photos.SearchParameters;
import com.flickr4java.flickr.tags.Tag;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.doering.dwca.CliConfiguration;
import de.doering.dwca.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractYear implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(ExtractYear.class);
  private Pattern SPLIT_MTAG = Pattern.compile("([a-z]+:[a-z0-9_]+)=(.+)", Pattern.CASE_INSENSITIVE);
  private final int PAGESIZE = 100;
  private final int MAX_PAGES = 10;
  private final SearchParameters PARAMS = new SearchParameters();
  private final Year year;
  private Date minSearched;
  private int currPage = 0;
  private int imgCounter = 0;
  private final ImageWriter imgWriter;

  private static final Map<String, Term> TAG_MAPPING = Maps.newHashMap();
  private Flickr f;

  private static void addTagMappings(Term term , String ... tags){
    for (String t : tags){
      TAG_MAPPING.put(t, term);
    }
  }

  private static final List<String> SCIENTIFIC_NAME_TAGS = Lists.newArrayList("darwincore:scientificname","dwc:scientificname","taxonomy:binomial","taxonomy:trinomial","taxonomy:binominal","taxonomy:latinname");
  static {
    addTagMappings(DwcTerm.scientificName, SCIENTIFIC_NAME_TAGS.toArray(new String[SCIENTIFIC_NAME_TAGS.size()]));
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
  private static final String[] SEARCH_TAGS = new String[SCIENTIFIC_NAME_TAGS.size()];
  static {
    int idx = 0;
    for (String t : SCIENTIFIC_NAME_TAGS) {
      SEARCH_TAGS[idx++] = t+"=";
    }
  }


  /**
   *
   * @param year the year to scan
   * @param imgWriter
   */
  public ExtractYear(CliConfiguration cfg, int year, ImageWriter imgWriter) {
    this.year = Year.of(year);
    this.currPage  = 1;
    this.imgWriter = imgWriter;

    Transport transport = new REST();
    f = new Flickr(cfg.flickrKey, cfg.flickrSecret, transport);

    PARAMS.setMachineTags(SEARCH_TAGS);
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
    PARAMS.setMinUploadDate(DateUtils.asDate(this.year.minusYears(1).atDay(1)));
    PARAMS.setMaxUploadDate(DateUtils.asDate(this.year.atDay(1)));
    PARAMS.setSort(SearchParameters.DATE_POSTED_DESC);
  }

  @Override
  public void run() {
    LOG.info("Start flickr export job for year {}", year);
    // call one search after the other until we cant find any more images
    List<FlickrImage> images = search();
    while(images != null){
      // write images
      int imgCounterPrev = imgCounter;
      for (FlickrImage img : images){
        if (img == null) continue;
        try {
          LOG.debug("Writing image {} for year {}", img.getLink(), year);
          if (imgWriter.writeImage(img)) {
            imgCounter++;
          } else {
            LOG.debug("image {} failed for year {}", img.getLink(), year);
          }
        } catch (Exception e) {
          LOG.error("Failed to write {} images for year {} of page {}", images.size(), year, currPage, e);
        }
      }
      LOG.debug("Written {} new images for year {}, {} in total", imgCounter-imgCounterPrev, year, imgCounter);

      currPage++;
      if (currPage % MAX_PAGES == 0){
        // modify search to please Flickr, set new minimum upload date
        PARAMS.setMaxUploadDate(minSearched);
        LOG.info("Rebuild {} query with maxUploadDate={}", year, minSearched);
      }
      images = search();
    }
    LOG.info("Finishing year {} with {} images and {} searched page in total", year, imgCounter, currPage);
  }



  /**
   *
   * @return list of populated images or null if no more images could be found.
   */
  private List<FlickrImage> search() {
    List<FlickrImage> images = Lists.newArrayList();
    try {
      LOG.debug("Searching {} with page {}", year, currPage);
      PhotoList list = f.getPhotosInterface().search(PARAMS, PAGESIZE, currPage);
      if (list.isEmpty()){
        return null;
      }
      LOG.debug("Found {} new images for year {} on page {}, loading photo details.", list.size(), year, currPage);
      for (Iterator iterator = list.iterator(); iterator.hasNext(); ) {
        Photo photo = (Photo) iterator.next();
        // remember date uploaded
        Date newPosted = photo.getDatePosted();
        if (minSearched==null || newPosted.before(minSearched)){
          minSearched = newPosted;
        }

        FlickrImage img = extract(photo);
        if (img!=null){
          images.add(img);
        } else {
          LOG.debug("No suitable image for year {}: {}", year, photo.getUrl());
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to search {} for photo page {}", year, currPage, e);
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
      LOG.warn("Cannot retrieve tags for {} photo {} {}", year, photo.getId(), photo.getUrl());
    }

    return map;
  }

}
