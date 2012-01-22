/*
 * Copyright 2011 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.doering.dwca.dbpedia;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.text.DwcaWriter;
import org.gbif.metadata.eml.Citation;
import org.gbif.metadata.eml.Eml;
import org.gbif.utils.file.CompressionUtil;
import org.gbif.utils.file.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import com.google.inject.Inject;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChecklistBuilder {

  private Logger log = LoggerFactory.getLogger(getClass());
  private Pattern cleanFamily = Pattern.compile("^([^ ,(]+)");
  private DwcaWriter writer;
  private static final String SPARQL_ENDPOINT = "http://dbpedia.org/sparql";
  // metadata
  private static final String TITLE = "DBPedia";
  private static final String HOMEPAGE = "http://www.dbpedia.org/";
  private static final String CITATION = "";
  private static final String LOGO = null;
  private static final String DESCRIPTION = "";

  @Inject
  public ChecklistBuilder() {
  }

  private void parseData() throws IOException {
    String sparqlQueryString = "select distinct ?Concept where {[] a ?Concept } LIMIT 10";
    Query query = QueryFactory.create(sparqlQueryString);
    QueryExecution qexec = QueryExecutionFactory.sparqlService(" http://dbpedia.org/sparql", query);

    try {
        ResultSet results = qexec.execSelect();
        for ( ; results.hasNext() ; ){
          QuerySolution soln = results.nextSolution() ;
          String x = soln.get("Concept").toString();
          System.out.print(x +"\n");
        }
    }finally {
      qexec.close();
    }
  }

  public File build() throws IOException {
    // new writer
    File dwcaDir = FileUtils.createTempDir("dbpedia-", "");
    File dwcaZip = new File(dwcaDir.getAbsoluteFile() + ".zip");
    log.info("Writing archive files to temporary folder " + dwcaDir);
    writer = new DwcaWriter(DwcTerm.Taxon, dwcaDir);

    // metadata
    Eml eml = new Eml();
    eml.setTitle(TITLE);
    Citation cite = new Citation();
    cite.setCitation(CITATION);
    eml.setCitation(cite);
    eml.setDescription(DESCRIPTION);
    eml.setHomepageUrl(HOMEPAGE);
    eml.setLogoUrl(LOGO);

    // parse file and some metadata
    parseData();

    // finish archive and zip it
    log.info("Bundling archive at {}", dwcaZip);
    writer.setEml(eml);
    writer.finalize();

    // compress
    CompressionUtil.zipDir(dwcaDir, dwcaZip);
    // remove temp folder
    //org.apache.commons.io.FileUtils.deleteDirectory(dwcaDir);

    log.info("Dwc archive completed at {} !", dwcaZip);

    return dwcaZip;
  }

  public static void main(String[] args) throws IOException {
    ChecklistBuilder builder = new ChecklistBuilder();
    File archive = builder.build();
    System.out.println("Archive generated at " + archive.getAbsolutePath());
  }
}
