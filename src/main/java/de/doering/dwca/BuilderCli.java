package de.doering.dwca;

import com.beust.jcommander.JCommander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

/**
 * Checklist build command
 */
public class BuilderCli {
    private static final Logger LOG = LoggerFactory.getLogger(BuilderCli.class);

    public static void main(String[] args) throws Exception {
        BuilderConfig cfg = new BuilderConfig();
        new JCommander(cfg, args);

        LOG.info("Building {} checklist", cfg.source);
        Class<? extends AbstractBuilder> abClass = cfg.builderClass();
        Constructor<? extends AbstractBuilder> cons = abClass.getConstructor(BuilderConfig.class);
        AbstractBuilder builder = cons.newInstance(cfg);
        builder.run();
        LOG.info("{} checklist completed", cfg.source);
    }

}
