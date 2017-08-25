package de.doering.dwca;

import com.google.inject.AbstractModule;
import de.doering.dwca.clements.ArchiveBuilder;

public class CliModule extends AbstractModule {
    private final CliConfiguration cfg;

    public CliModule(CliConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    protected void configure() {
        bind(CliConfiguration.class).toInstance(cfg);
        bind(ArchiveBuilder.class);
    }
}
