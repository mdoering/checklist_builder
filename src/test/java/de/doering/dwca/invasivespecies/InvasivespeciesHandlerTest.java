package de.doering.dwca.invasivespecies;

import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class InvasivespeciesHandlerTest {
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Test
  public void testPatterns(){
    assertSynonym("Lithobates catesbeianus (=Rana catesbeiana)", "Lithobates catesbeianus", "Rana catesbeiana");
    assertSynonym("Sphaeroma quoianum (=S. quoyanum)", "Sphaeroma quoianum", "Sphaeroma quoyanum");
  }

  private void assertSynonym(String sciname, String epxSciname, String expSynonym){
    String synonym = null;
    Matcher m = InvasivespeciesHandler.extractSynonym.matcher(sciname);
    if (m.find()){
      synonym = m.group(1).trim();
      sciname = m.replaceFirst("").trim();
      log.debug("Synonym for {} found: {}", sciname, synonym);
      // allow abbreviated genera
      Matcher abbrev = InvasivespeciesHandler.abbrevGenus.matcher(synonym);
      if (abbrev.find()){
        String[] parts = StringUtils.split(sciname, " ");
        String oldSyn = synonym;
        synonym = abbrev.replaceFirst(parts[0]);
        log.debug("Abbreviated genus found: {} => {}", oldSyn, synonym);
      }
    }

    assertEquals(epxSciname, sciname);
    assertEquals(expSynonym, synonym);
  }
}
