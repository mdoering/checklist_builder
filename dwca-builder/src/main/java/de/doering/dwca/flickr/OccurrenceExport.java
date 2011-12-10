package de.doering.dwca.flickr;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.text.DwcaWriter;
import org.gbif.metadata.eml.Eml;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OccurrenceExport {

  private Logger log = LoggerFactory.getLogger(getClass());
  private DwcaWriter writer;
  private ImageWriter imgWriter;
  private final int THREADS = 10;

  @Inject
  public OccurrenceExport() {
  }

  private void export() throws IOException {
		// We will store the threads so that we can check if they are done
		List<Thread> threads = new ArrayList<Thread>();
    // setup threads
    for (int i = 0; i < THREADS; i++) {
			Runnable searcher = new FlickrOccurrenceSearch(i, THREADS, imgWriter);
      Thread worker = new Thread(searcher);
			// We can set the name of the thread
			worker.setName("searcher"+i);
			// Start the thread, never call method run() direct
      log.debug("Starting search thread " + i);
			worker.start();
			// Remember the thread for later usage
			threads.add(worker);
    }

		// Wait until all threads are finish
		int running = 0;
		do {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        // TODO: Handle exception
      }
      running = 0;
			for (Thread thread : threads) {
				if (thread.isAlive()) {
					running++;
				}
			}
			//log.debug("We have " + running + " running searches. ");
		} while (running > 0);
    log.info("Finished flickr export with {} records", writer.getRecordsWritten());
  }



  public File build() throws IOException {
    // new writer
    File dwcaDir = FileUtils.createTempDir("flickr-", "-dwca");
    File dwcaZip = new File(dwcaDir.getAbsoluteFile() + ".zip");
    log.info("Writing archive files to temporary folder " + dwcaDir);
    writer = new DwcaWriter(DwcTerm.Occurrence, dwcaDir);
    imgWriter = new ImageWriter(writer);

    // parse file
    export();

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