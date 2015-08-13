package de.doering.dwca;

import java.io.File;
import java.util.List;
import javax.validation.constraints.NotNull;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;
import com.google.common.base.Joiner;

/**
 *
 */
public class CliConfiguration {

    @Parameter(names = {"-r", "--repository"}, required = true)
    @NotNull
    public File repository;

    @Parameter(names = {"-s", "--source"}, required = true)
    @NotNull
    public String source;

    @Parameter(names = {"--timeout"})
    public int timeout = 60;

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
