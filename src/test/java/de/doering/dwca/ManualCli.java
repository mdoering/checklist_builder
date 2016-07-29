package de.doering.dwca;

import java.io.File;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Ignore;

/**
 *
 */
@Ignore
public class ManualCli {

  public static void main(String[] args) {
    CliConfiguration cfg = new CliConfiguration();
    cfg.repository = new File("/Users/markus/Desktop/archives");
    cfg.source = "iocwbn";

    Injector inj = Guice.createInjector(new CliModule(cfg));

    Runnable builder = inj.getInstance(cfg.builderClass());
    builder.run();
  }
}
