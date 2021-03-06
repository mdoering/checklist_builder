package de.doering.dwca.ioc;

import com.google.common.io.Resources;
import org.junit.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class IocXmlHandlerTest {

  @Test
  public void testStartElement() throws Exception {
    SAXParserFactory factory = SAXParserFactory.newInstance();

    final SAXParser parser = factory.newSAXParser();
    IocXmlHandler handler = new IocXmlHandler(null);
    Reader reader = new InputStreamReader(Resources.getResource("master_ioc-names_xml.xml").openStream(), "UTF-8");
    parser.parse(new InputSource(reader), handler);

    assertEquals("2016", handler.getYear());
    assertEquals("6.3", handler.getVersion());
  }
}