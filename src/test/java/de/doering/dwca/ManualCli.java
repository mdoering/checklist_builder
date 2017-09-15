package de.doering.dwca;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Ignore;

import java.io.File;

/**
 *
 */
@Ignore
public class ManualCli {

  public static void main(String[] args) {
    CliConfiguration cfg = new CliConfiguration();
    cfg.repository = new File("/Users/markus/Desktop/archives");
    cfg.source = "itis";

    Injector inj = Guice.createInjector(new CliModule(cfg));

    Runnable builder = inj.getInstance(cfg.builderClass());
    builder.run();
  }
}
