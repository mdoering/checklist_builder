package de.doering.dwca.flickr;

import org.gbif.dwc.terms.ConceptTerm;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.dwc.text.DwcaWriter;
import org.gbif.metadata.eml.Eml;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OccurrenceExport {

  private Logger log = LoggerFactory.getLogger(getClass());
  private FlickrOccurrenceSearch search = new FlickrOccurrenceSearch();
  private DwcaWriter writer;
  private final ConceptTerm thumbnail = new UnknownTerm("http://flickr.com/terms/smallSquareUrl","smallSquareUrl");
  private final ConceptTerm flickrid = new UnknownTerm("http://flickr.com/terms/photoId","photoId");
  private final Cache<String, Integer> cache = CacheBuilder.newBuilder().maximumSize(10000).build(new CacheLoader<String, Integer>() {
             public Integer load(String key) {
               return 1;
             }
           });

  @Inject
  public OccurrenceExport() {
  }

  private void export() throws IOException {
    int page = 0;
    // search flickr
    List<FlickrImage> images = search.list(page);
    while (!images.isEmpty()){
      // write to archive
      writeImages(images);
      // search flickr
      page++;
      images = search.list(page);
    }
    log.info("Finished flickr export with {} records and {} search pages", writer.getRecordsWritten(), page+1);
  }

  private void writeImages(List<FlickrImage> images) throws IOException {
    log.debug("Writing {} new images to archive with {} records", images.size(), writer.getRecordsWritten());
    for (FlickrImage img : images){
      if (img == null) continue;
      // encountered this image before?
      if (cache.asMap().containsKey(img.getId())){
        log.debug("Image {} processed before already", img.getId());
        continue;
      }
      try {
        cache.get(img.getId());
      } catch (ExecutionException e) {
        // ignore, should not happen
      }
      writer.newRecord(img.getId());
      writer.addCoreColumn(DcTerm.source, img.getLink());
      writer.addCoreColumn(DwcTerm.scientificName, img.getScientificName());
      writer.addCoreColumn(DwcTerm.basisOfRecord, "HumanObservation");
      writer.addCoreColumn(DwcTerm.recordedBy, img.getPhotographer());

      // potentially null
      if (img.getDateRecorded()!=null){
        writer.addCoreColumn(DwcTerm.eventDate, img.getDateRecorded().toString());
      }
      if (img.getLongitude()!=null){
        writer.addCoreColumn(DwcTerm.decimalLongitude, img.getLongitude().toString());
      }
      if (img.getLatitude()!=null){
        writer.addCoreColumn(DwcTerm.decimalLatitude, img.getLatitude().toString());
      }
      if (img.getAccuracy()!=null){
        writer.addCoreColumn(DwcTerm.coordinatePrecision, img.getAccuracy().toString());
      }
      // additional, optional dynamic properties
      for (ConceptTerm t : img.getAttributes().keySet()){
        writer.addCoreColumn(t, img.getAttribute(t));
      }

      // add image extension
      Map<ConceptTerm, String> data = new HashMap<ConceptTerm, String>();
      data.put(flickrid,img.getId());
      data.put(DcTerm.references, img.getLink());
      data.put(DcTerm.identifier, img.getImage());
      data.put(thumbnail,img.getThumb());
      data.put(DcTerm.license, img.getLicense());
      data.put(DcTerm.rightsHolder, img.getOwner());
      if (img.getDateRecorded()!=null){
        data.put(DcTerm.created, img.getDateRecorded().toString());
      }
      data.put(DcTerm.title, img.getTitle());
      data.put(DcTerm.description, img.getDescription());
      writer.addExtensionRecord(GbifTerm.Image, data);
    }
  }

  public File build() throws IOException {
    // new writer
    File dwcaDir = FileUtils.createTempDir("flickr-", "-dwca");
    File dwcaZip = new File(dwcaDir.getAbsoluteFile() + ".zip");
    log.info("Writing archive files to temporary folder " + dwcaDir);
    writer = new DwcaWriter(DwcTerm.Occurrence, dwcaDir);

    // parse file
    export();

    ;
    // finish archive and zip it
    log.info("Bundling archive at {}", dwcaZip);
    writer.setEml(buildEml());
    writer.finalize();

    // compress
    CompressionUtil.zipDir(dwcaDir, dwcaZip);
    // remove temp folder
    org.apache.commons.io.FileUtils.deleteDirectory(dwcaDir);

    log.info("Dwc archive completed at {} !", dwcaZip);

    return dwcaZip;
  }


  private Eml buildEml() {
    Eml eml = new Eml();
    eml.setTitle("Flickr species observations", "en");
    eml.setAbstract("A full dump of all public flickr images that are machine tagged with a species name. "
                    + "Only images with using various open creative commons licenses are included. "
                    + "Machine tags recognised include the EOL taxonomy namespace and the dwc/darwincore namespace.");
    eml.setHomeUrl("http://www.flickr.com");
    org.gbif.metadata.eml.Agent contact = new org.gbif.metadata.eml.Agent();
    contact.setFirstName("Markus");
    contact.setLastName("Döring");
    contact.setEmail("mdoering@gbif.org");
    eml.setContact(contact);
    return eml;
  }

  public static void main(String[] args) throws IOException {
    OccurrenceExport builder = new OccurrenceExport();
    File archive = builder.build();
    System.out.println("Archive generated at " + archive.getAbsolutePath());
  }
}