package de.doering.dwca;

import org.gbif.cli.BaseCommand;
import org.gbif.cli.Command;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checklist build command
 */
@MetaInfServices(Command.class)
public class CliCommand extends BaseCommand {
    private static final Logger LOG = LoggerFactory.getLogger(CliCommand.class);

    private final CliConfiguration cfg = new CliConfiguration();

    public CliCommand() {
        super("build");
    }

    @Override
    protected Object getConfigurationObject() {
        return cfg;
    }

    @Override
    protected void doRun() {
        Injector inj = Guice.createInjector(new CliModule(cfg));
        Runnable builder = inj.getInstance(cfg.builderClass());
        LOG.info("Building {} checklist", cfg.source);
        builder.run();
        LOG.info("{} checklist completed", cfg.source);
    }
}
