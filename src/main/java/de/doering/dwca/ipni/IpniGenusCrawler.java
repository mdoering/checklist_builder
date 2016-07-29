package de.doering.dwca.ipni;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.doering.dwca.utils.HttpUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IpniGenusCrawler implements Runnable {
    private static Logger LOG = LoggerFactory.getLogger(IpniGenusCrawler.class);
    private static final String DELIMITER = "%";
    private static final String WILDCARD = "%";
    private static final int COL_FAMILY = 17;
    private static final int COL_GENUS = 17;
    private static final int COL_BASIONYM = 24;
    private static final int COL_REPL_SYNONYM = 25;
    private static final String SEARCH = "http://www.ipni.org/ipni/advPlantNameSearch.do?" +
            "output_format=delimited" +
            "&query_type=by_query" +
            "&find_rankToReturn={rank}" +
            "&find_genus={genus}";
    // find_modifiedSince=2005-08-01
    private static final Pattern REPLACE_LEADING_FAMILY = Pattern.compile("^[A-Z][a-z]+ ");
    private static final Joiner JOINER = Joiner.on(DELIMITER).useForNull("");

    enum RANK_PARAM {ALL, FAM, INFRAFAM, GEN, INFRAGEN, SPEC, INFRASPEC};

    private final CloseableHttpClient client;
    private final List<Character> atoz = Lists.newArrayList();
    private final Set<String> genera;
    private final File ipniDir;
    private Map<Source, File> files = Maps.newHashMap();


    public IpniGenusCrawler(CloseableHttpClient client, File ipniDir) {
        this(client, ipniDir, Sets.<String>newHashSet());
    }

    public IpniGenusCrawler(CloseableHttpClient client, File ipniDir, Set<String> genera) {
        this.client = client;
        this.ipniDir = ipniDir;
        if (ipniDir.exists()) {
            LOG.info("IPNI directory {} existed already and is being cleaned", ipniDir);
            try {
                FileUtils.cleanDirectory(ipniDir);
            } catch (IOException e) {
                LOG.error("Fail to clear existing IPNI directory {}", ipniDir);
            }
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            atoz.add(c);
        }
        this.genera = genera;
    }

    private String srcParam(Source source, boolean on) {
        return "&find_is" + source.name() + "Record=" + (on ? "on" : "false");
    }

    private URI buildQuery(RANK_PARAM rank, Source source, String genus) {
        StringBuilder sb = new StringBuilder();
        for (Source sp : Source.values()) {
            sb.append(srcParam(sp, source == null || sp == source));
        }
        return URI.create(SEARCH.replace("{rank}", rank.name().toLowerCase()).replace("{genus}", genus) + sb.toString());
    }

    private void retrieveGenera() throws IOException {
        LOG.info("Discover all IPNI genera");
        for (Character c : atoz) {
            URI query = buildQuery(RANK_PARAM.GEN, null, c + WILDCARD);
            LOG.debug("query: {}", query);
            HttpGet get = new HttpGet(query);

            // execute and keep result in memory
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(16*1024);
            try(CloseableHttpResponse response = client.execute(get)) {
                HttpEntity entity = response.getEntity();
                entity.writeTo(buffer);
            }
            LineIterator iter = new LineIterator(new StringReader(buffer.toString(Charsets.UTF_8.name())));
            Set<String> newGenera = Sets.newHashSet();
            boolean skipHeader = true;
            try {
                while (iter.hasNext()) {
                    String line = iter.next();
                    if (StringUtils.isBlank(line)) continue;
                    if (skipHeader) {
                        skipHeader = false;
                        continue;
                    }
                    String[] row = line.split(DELIMITER);
                    newGenera.add(StringUtils.strip(row[COL_GENUS]));
                }
            } catch (RuntimeException e) {
                LOG.error("Failed to retrieve families from response");
                LOG.debug("{}", buffer.toString(Charsets.UTF_8.name()));
            }
            LOG.info("Discovered {} families starting with {}", newGenera.size(), c);
            genera.addAll(newGenera);
        }
        LOG.info("Discovered {} genera", genera.size());
    }

    private void crawlGenus(Source src, String genus, FileOutputStream out) throws IOException {
        LOG.info("Crawl {}, genus {}", src, genus);
        URI query = buildQuery(RANK_PARAM.ALL, src, genus);
        HttpGet get = new HttpGet(query);

        try(CloseableHttpResponse response = client.execute(get)) {
            if (HttpUtils.success(response.getStatusLine())) {
                HttpEntity entity = response.getEntity();
                entity.writeTo(out);
            } else {
                LOG.error("Http error {} when crawling {}, genus {}", response.getStatusLine().getStatusCode(), src, genus);
            }
        }
    }

    @Override
    public void run() {
        try {
            if (genera.isEmpty()) {
                retrieveGenera();
            }
            for (Source sp : Source.values()) {
                // create output files per source
                File srcFile = new File(ipniDir, sp.name().toLowerCase() + ".txt");
                files.put(sp, srcFile);
                try (FileOutputStream out = new FileOutputStream(srcFile, false)) {
                    for (String genus : genera) {
                      crawlGenus(sp, genus, out);
                    }
                }
            }
            mergeFiles();

        } catch (Exception e) {
            LOG.error("Failed to crawl IPNI", e);
            throw new RuntimeException(e);
        }

    }

    /**
     * Merge all 3 source files into one adding a datasetID column.
     */
    private void mergeFiles() throws IOException {
        File mf = new File(ipniDir, "names.txt");
        try (FileWriter writer = new FileWriter(mf, false)) {
            for (Map.Entry<Source, File> entry : files.entrySet()) {
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(entry.getValue()), Charsets.UTF_8)) {
                    LineIterator iter = new LineIterator(reader);
                    while (iter.hasNext()) {
                        String line = iter.next();
                        // skip blank lines
                        if (StringUtils.isBlank(line)) continue;
                        // skip header lines
                        if (line.startsWith("Id%")) continue;
                        // remove family from basionym name
                        String[] row = line.split(DELIMITER);
                        if (row[COL_BASIONYM] != null) {
                            if (row[COL_BASIONYM].toLowerCase().startsWith("basionym not")) {
                                row[COL_BASIONYM] = null;
                            } else {
                                // remove prepended family
                                row[COL_BASIONYM] = REPLACE_LEADING_FAMILY.matcher(row[COL_BASIONYM]).replaceFirst("");
                            }
                        }
                        // prepend dataset column
                        writer.write(entry.getKey().name() + DELIMITER + JOINER.join(row) + '\n');
                    }
                }
            }
        }
    }

}
