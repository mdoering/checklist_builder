package de.doering.dwca;

import org.gbif.dwc.terms.DwcTerm;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class AbstractBuilderTest {

    @Test
    public void buildCitation() throws Exception {
        assertEquals("??? (1984). Wise friends never talk.", AbstractBuilder.buildCitation(" ", " 1984.", " Wise friends never talk.", ""));
    }

    @Test
    public void testDwca() throws Exception {
        assertEquals("http://rs.tdwg.org/dwc/terms/Taxon", DwcTerm.Taxon.qualifiedName());
    }

}