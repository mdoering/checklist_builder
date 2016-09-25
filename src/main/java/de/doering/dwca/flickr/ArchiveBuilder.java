package de.doering.dwca.flickr;

import org.gbif.api.vocabulary.DatasetType;

import java.io.IOException;
import java.time.Year;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.Transport;
import com.flickr4java.flickr.machinetags.MachinetagsInterface;
import com.flickr4java.flickr.machinetags.Namespace;
import com.flickr4java.flickr.machinetags.NamespacesList;
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;

public class ArchiveBuilder extends AbstractBuilder {
  private final int MIN_YEAR = 1980;
  private final ExecutorService exec;

  @Inject
  public ArchiveBuilder(CliConfiguration cfg) {
    super(DatasetType.OCCURRENCE, cfg);
    exec = Executors.newFixedThreadPool(cfg.threads);
  }

  private void showMachineTags() throws FlickrException {
    Transport transport = new REST();
    Flickr f = new Flickr(cfg.flickrKey, cfg.flickrSecret, transport);

    MachinetagsInterface mi = f.getMachinetagsInterface();

    int page = 0;
    NamespacesList<Namespace> nsl = null;
    while (nsl == null || !nsl.isEmpty()) {
      nsl = mi.getNamespaces(null, 25, page++);
      for (Namespace ns : nsl) {
        LOG.info("{}, usage={}, predicates={}", ns.getUsage(), ns.getPredicates(), ns.getValue());
      }
    }
  }

  @Override
  protected void parseData() throws IOException, FlickrException {
    ImageWriter imgWriter = new ImageWriter(writer);
    // loop over years
    int year = Year.now().getValue();
    while (year >= MIN_YEAR) {
      exec.submit(new ExtractYear(cfg, year, imgWriter));
      year--;
    }

    // Wait until all jobs have completed
    exec.shutdown();
    try {
      exec.awaitTermination(31, TimeUnit.DAYS);
    } catch (InterruptedException e) {
      LOG.error("Flickr export interrupted", e);
    }
    LOG.info("Finished flickr export with {} records", writer.getRecordsWritten());
  }

  @Override
  protected void addMetadata() {
    dataset.setTitle("Flickr species observations");
    dataset.setDescription("A full dump of all public flickr images that are machine tagged with a species name. "
        + "Only images with using various open creative commons licenses are included. "
        + "Machine tags recognised include the EOL taxonomy namespace and the dwc/darwincore namespace.");
    dataset.setHomepage(uri("http://www.flickr.com"));
    addContact(null, "Markus", "Döring", "mdoering@gbif.org");
  }

}