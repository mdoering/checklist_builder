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

import com.google.inject.Inject;
import com.hp.hpl.jena.query.*;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.gbif.api.vocabulary.DatasetType;

import java.io.IOException;

public class ArchiveBuilder extends AbstractBuilder {

    private static final String DOWNLOAD = "http://dbpedia.org/sparql";
    // metadata
    private static final String TITLE = "DBPedia";
    private static final String HOMEPAGE = "http://downloads.dbpedia.org/current/core/";
    private static final String CITATION = "";
    private static final String LOGO = null;
    private static final String DESCRIPTION = "";

    @Inject
    public ArchiveBuilder(CliConfiguration cfg) {
        super(DatasetType.CHECKLIST, cfg);
    }

    @Override
    protected void parseData() throws IOException {
        String sparqlQueryString = "select distinct ?Concept where {[] a ?Concept } LIMIT 10";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.sparqlService(" http://dbpedia.org/sparql", query);

        try {
            ResultSet results = qexec.execSelect();
            for (; results.hasNext(); ) {
                QuerySolution soln = results.nextSolution();
                String x = soln.get("Concept").toString();
                System.out.print(x + "\n");
            }
        } finally {
            qexec.close();
        }
    }

    @Override
    protected void addMetadata() {
        dataset.setTitle(TITLE);
        setCitation(CITATION);
        dataset.setDescription(DESCRIPTION);
        dataset.setHomepage(uri(HOMEPAGE));
        dataset.setLogoUrl(uri(LOGO));
    }

}
