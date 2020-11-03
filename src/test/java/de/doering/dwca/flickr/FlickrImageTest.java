package de.doering.dwca.flickr;

import org.gbif.dwc.terms.DwcTerm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FlickrImageTest {

  @Test
  public void testSetAttribute() throws Exception {
    FlickrImage img = new FlickrImage();
    String x = "Abies alba";
    img.setAttribute(DwcTerm.scientificName, x);
    assertEquals(x, img.getScientificName());
    assertTrue(img.getAttributes().isEmpty());

    x = "pia";
    img.setAttribute(DwcTerm.decimalLongitude, x);
    assertNull(img.getLongitude());
    assertTrue(img.getAttributes().isEmpty());

    x = "12.03";
    img.setAttribute(DwcTerm.decimalLongitude, x);
    assertEquals((Float)12.03f, img.getLongitude());
    assertTrue(img.getAttributes().isEmpty());

    x = "12";
    img.setAttribute(DwcTerm.coordinatePrecision, x);
    assertEquals((Integer) 12, img.getAccuracy());
    assertTrue(img.getAttributes().isEmpty());

    x = "köln";
    img.setAttribute(DwcTerm.locality, x);
    assertEquals(x, img.getAttribute(DwcTerm.locality));
    assertEquals(1, img.getAttributes().size());
  }
}
