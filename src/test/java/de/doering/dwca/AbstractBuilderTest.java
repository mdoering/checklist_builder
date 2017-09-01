package de.doering.dwca;

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

}