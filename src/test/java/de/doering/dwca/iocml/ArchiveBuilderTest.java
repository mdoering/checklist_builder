package de.doering.dwca.iocml;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

public class ArchiveBuilderTest {

  @Test
  public void version() {
    assertEquals("10.3", ArchiveBuilder.version(10,3,null));
    assertEquals("9.1_b", ArchiveBuilder.version(9,1,"_b"));
    assertEquals("12.2", ArchiveBuilder.version(12,2,null));
  }

  @Test
  public void url() {
    String version = "9.1_b";
    assertEquals("https://www.worldbirdnames.org/Multiling%20IOC%209.1_b.xlsx", ArchiveBuilder.url(version));
  }
}