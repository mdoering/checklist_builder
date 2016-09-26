package de.doering.dwca;

import java.io.File;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;
import com.google.common.base.Joiner;

/**
 *
 */
public class CliConfiguration {

  @Parameter(names = {"-r", "--repository"})
  @NotNull
  public File repository;

  @Parameter(names = {"-s", "--source"}, required = true)
  @NotNull
  public String source;

  @Parameter(names = {"--timeout"})
  public int timeout = 600;

  @Parameter(names = {"--threads"})
  public int threads = 4;

  @Parameter(names = {"--flickr-key"})
  public String flickrKey = "59c1f626e17ddc0e37160b56d7b21ea3";

  @Parameter(names = {"--flickr-secret"})
  public String flickrSecret = "56cf7af06a966665";

  @Parameter(names = {"--flickr-cache-size"})
  public int flickrCacheSize = 10000;

  @Parameter(names = {"--flickr-page-size"})
  @Max(500)
  @Min(10)
  public int flickrPageSize = 500;

  /**
   * Returns the directory with the decompressed archive folder created by the checklist builder
   */
  public File archiveDir() {
    return new File(repository, source);
  }

  public Class<Runnable> builderClass() {
    try {
      String classname = CliConfiguration.class.getPackage().getName() + "." + source.toLowerCase() + ".ArchiveBuilder";
      return (Class<Runnable>) CliConfiguration.class.getClassLoader().loadClass(classname);
    } catch (ClassNotFoundException e) {
      List<String> sources = Lists.newArrayList();
      Joiner joiner = Joiner.on(",").skipNulls();
      throw new IllegalArgumentException(source + " not a valid source. Please use one of: " + joiner.join(sources), e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
