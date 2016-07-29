package de.doering.dwca.iocwbn;

import org.gbif.api.model.registry.Dataset;

import java.io.InputStreamReader;
import java.io.Reader;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.google.common.io.Resources;
import org.junit.Test;
import org.xml.sax.InputSource;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class IocXmlHandlerTest {

  @Test
  public void testStartElement() throws Exception {
    SAXParserFactory factory = SAXParserFactory.newInstance();

    final SAXParser parser = factory.newSAXParser();
    IocXmlHandler handler = new IocXmlHandler(null, new Dataset());
    Reader reader = new InputStreamReader(Resources.getResource("master_ioc-names_xml.xml").openStream(), "UTF-8");
    parser.parse(new InputSource(reader), handler);

    assertEquals("2016", handler.getYear());
    assertEquals("6.3", handler.getVersion());
  }
}