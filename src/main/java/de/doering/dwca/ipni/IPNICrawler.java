package de.doering.dwca.ipni;

import org.gbif.utils.HttpUtil;

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

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IPNICrawler implements Runnable {
    private static Logger LOG = LoggerFactory.getLogger(IPNICrawler.class);
    private static final String DELIMITER = "%";
    private static final String WILDCARD = "*";
    private static final int HEADER_ROWS = 1;
    private static final int ID_COLUMN = ArchiveBuilder.COL_ID;
    private static final int FAMILY_COLUMN = 17;
    private static final String SEARCH = "http://www.ipni.org/ipni/advPlantNameSearch.do?" +
            "output_format=delimited" +
            "&query_type=by_query" +
            "&find_rankToReturn={rank}" +
            "&find_family={family}" +
            "&find_genus={genus}";
    private static final Set<String> LARGE_FAMILIES = Sets.newHashSet("Asteraceae", "Fabaceae", "Orchidaceae");
    enum RANK_PARAM {ALL, FAM, INFRAFAM, GEN, INFRAGEN, SPEC, INFRASPEC};
    enum SOURCE {IK, GCI, APNI};
    private final CloseableHttpClient client;
    private final List<Character> atoz = Lists.newArrayList();
    private final Set<String> families;
    private final File ipniDir;
    private Map<SOURCE, File> files = Maps.newHashMap();


    public IPNICrawler(CloseableHttpClient client, File ipniDir) {
        this(client, ipniDir, Sets.<String>newHashSet());
    }

    public IPNICrawler(CloseableHttpClient client, File ipniDir, Set<String> families) {
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
        this.families = families;
    }

    private String srcParam(SOURCE source, boolean on) {
        return "&find_is" + source.name() + "Record=" + (on ? "on" : "false");
    }

    private URI buildQuery(RANK_PARAM rank, SOURCE source, String family, String genus) {
        StringBuilder sb = new StringBuilder();
        for (SOURCE sp : SOURCE.values()) {
            sb.append(srcParam(sp, source == null || sp == source));
        }
        return URI.create(SEARCH.replace("{rank}", rank.name().toLowerCase()).replace("{family}", family).replace("{genus}", genus) + sb.toString());
    }

    private void retrieveFamilies() throws IOException {
        LOG.info("Discover all IPNI families");
        for (Character c : atoz) {
            URI query = buildQuery(RANK_PARAM.FAM, null, c + WILDCARD, "");
            LOG.debug("query: {}", query);
            HttpGet get = new HttpGet(query);

            // execute and keep result in memory
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(16*1024);
            try(CloseableHttpResponse response = client.execute(get)) {
                HttpEntity entity = response.getEntity();
                entity.writeTo(buffer);
            }
            LineIterator iter = new LineIterator(new StringReader(buffer.toString(Charsets.UTF_8.name())));
            Set<String> newFamilies = Sets.newHashSet();
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
                    newFamilies.add(StringUtils.strip(row[FAMILY_COLUMN]));
                }
            } catch (RuntimeException e) {
                LOG.error("Failed to retrieve families from response");
                LOG.debug("{}", buffer.toString(Charsets.UTF_8.name()));
            }
            LOG.info("Discovered {} families starting with {}", newFamilies.size(), c);
            families.addAll(newFamilies);
        }
        LOG.info("Discovered {} families", families.size());
    }

    private void crawlFamily(SOURCE src, String family, FileOutputStream out) throws IOException {
        if (LARGE_FAMILIES.contains(family)) {
            LOG.info("Crawl large family {} by individual genera", src, family);
            for (Character c : atoz) {
                crawlFamilyByGenus(src, family, c+WILDCARD, out);
            }
        } else {
            crawlFamilyByGenus(src, family, "", out);
        }
    }

    private void crawlFamilyByGenus(SOURCE src, String family, String genus, FileOutputStream out) throws IOException {
        LOG.info("Crawl {}, family {}, genus {}", src, family, genus);
        URI query = buildQuery(RANK_PARAM.ALL, src, family, genus);
        HttpGet get = new HttpGet(query);

        try(CloseableHttpResponse response = client.execute(get)) {
            if (HttpUtil.success(response.getStatusLine())) {
                HttpEntity entity = response.getEntity();
                entity.writeTo(out);
            } else {
                LOG.error("Http error {} when crawling {}, family {}, genus {}", response.getStatusLine().getStatusCode(), src, family, genus);
            }
        }
    }

    @Override
    public void run() {
        try {
            if (families.isEmpty()) {
                retrieveFamilies();
            }
            for (SOURCE sp : SOURCE.values()) {
                // create output files per source
                File srcFile = new File(ipniDir, sp.name().toLowerCase() + ".txt");
                files.put(sp, srcFile);
                FileUtils.forceMkdir(srcFile.getParentFile());
                try (FileOutputStream out = new FileOutputStream(srcFile, false)) {
                    for (String family : families) {
                        crawlFamily(sp, family, out);
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
            for (Map.Entry<SOURCE, File> entry : files.entrySet()) {
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(entry.getValue()), Charsets.UTF_8)) {
                    LineIterator iter = new LineIterator(reader);
                    while (iter.hasNext()) {
                        String line = iter.next();
                        // skip blank lines
                        if (StringUtils.isBlank(line)) continue;
                        // skip header lines
                        if (line.startsWith("Id%")) continue;
                        // prepend dataset column
                        writer.write(entry.getKey().name() + DELIMITER + line + '\n');
                    }
                }
            }
        }
    }

}
