package de.doering.dwca.flickr;

import org.gbif.api.vocabulary.DatasetType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;

public class ArchiveBuilder extends AbstractBuilder {

    private ImageWriter imgWriter;
    private final int MIN_YEAR = 1980;
    private final int THREADS = 10;
    private List<Thread> threads = new ArrayList<Thread>();

    @Inject
    public ArchiveBuilder(CliConfiguration cfg) {
        super(DatasetType.OCCURRENCE, cfg);
        imgWriter = new ImageWriter(writer);
    }

    private void searchYear(int year) {
        if (threads.size() < THREADS) {
            threads.add(startThread(year));
        } else {
            // wait until one thread is finished
            LOG.debug("Waiting for a thread to finish");
            do {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                Iterator<Thread> iter = threads.iterator();
                while (iter.hasNext()) {
                    Thread t = iter.next();
                    if (!t.isAlive()) {
                        LOG.debug("Thread " + t.getName() + " finished");
                        iter.remove();
                    }
                }
            } while (threads.size() == THREADS);

            threads.add(startThread(year));
        }
    }

    private Thread startThread(int year) {
        Runnable searcher = new ExtractYear(year, imgWriter);
        Thread worker = new Thread(searcher);
        // We can set the name of the thread
        worker.setName("searcher" + year);
        // Start the thread, never call method run() direct
        LOG.debug("Searching year " + year);
        worker.start();
        return worker;
    }

    @Override
    protected void parseData() throws IOException {
        // loop over years
        int year = 1901 + new Date().getYear();
        while (year >= MIN_YEAR) {
            searchYear(year);
            year--;
        }

        // Wait until all threads are finish
      int running = 0;
        do {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            running = 0;
            for (Thread thread : threads) {
                if (thread.isAlive()) {
                    running++;
                }
            }
            //LOG.debug("We have " + running + " running searches. ");
        } while (running > 0);
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