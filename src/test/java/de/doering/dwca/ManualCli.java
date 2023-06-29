package de.doering.dwca;

import org.junit.Ignore;

@Ignore
public class ManualCli {

  public static void main(String[] args) throws Exception {
    BuilderCli.main( new String[]{"-s", "wsc", "-r", "/tmp/checklist_builder/archives"} );
  }
}
