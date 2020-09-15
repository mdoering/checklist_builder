package de.doering.dwca.clements;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;

public class ArchiveBuilderTest {

  @Test
  public void url() {
    LocalDate date = LocalDate.of(2020, 3, 23);
    assertEquals("https://www.birds.cornell.edu/clementschecklist/wp-content/uploads/2020/03/Clements-Checklist-v2020-March-2020.xlsx", ArchiveBuilder.url(date));

    date = LocalDate.of(1999, 1, 1);
    assertEquals("https://www.birds.cornell.edu/clementschecklist/wp-content/uploads/1999/01/Clements-Checklist-v1999-January-1999.xlsx", ArchiveBuilder.url(date));

    date = LocalDate.of(2021, 12, 31);
    assertEquals("https://www.birds.cornell.edu/clementschecklist/wp-content/uploads/2021/12/Clements-Checklist-v2021-December-2021.xlsx", ArchiveBuilder.url(date));
  }
}