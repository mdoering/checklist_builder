package de.doering.dwca;

import org.gbif.api.model.registry.Citation;
import org.gbif.api.model.registry.Contact;
import org.gbif.api.model.registry.Dataset;
import org.gbif.api.model.registry.eml.DataDescription;
import org.gbif.api.vocabulary.ContactType;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.api.vocabulary.Language;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwca.io.DwcaWriter;
import org.gbif.utils.file.CompressionUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import javax.annotation.Nullable;

import de.doering.dwca.utils.BasicAuthContextProvider;
import de.doering.dwca.utils.HttpUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBuilder implements Runnable {
  protected static Logger LOG = LoggerFactory.getLogger(AbstractBuilder.class);
  protected final Dataset dataset = new Dataset();
  protected final CliConfiguration cfg;
  protected final CloseableHttpClient client;
  protected final HttpUtils http;
  protected DwcaWriter writer;
  private final DatasetType type;

  public AbstractBuilder(DatasetType type, CliConfiguration cfg) {
    this(type, cfg, null);
  }

  public AbstractBuilder(DatasetType type, CliConfiguration cfg, @Nullable BasicAuthContextProvider authContextProvider) {
    this.cfg = cfg;
    client = HttpUtils.newMultithreadedClient(cfg.timeout * 1000, 50, 50);
    http = new HttpUtils(client, authContextProvider);
    this.type = type;
  }

  @Override
  public void run() {
    try {
      // metadata defaults
      dataset.setLanguage(Language.ENGLISH);
      addMetadataProvider();

      try {
        writer = new DwcaWriter(type == DatasetType.CHECKLIST ? DwcTerm.Taxon : DwcTerm.Occurrence, cfg.archiveDir(), false);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      parseData();
      addMetadata();

      // finish archive and zip it
      final File dwcaDir = cfg.archiveDir();
      LOG.info("Bundling archive at {}", dwcaDir.getAbsolutePath());
      writer.setEml(dataset);
      writer.close();

      // compress
      File zip = new File(dwcaDir.getParentFile(), dwcaDir.getName() + ".zip");
      CompressionUtil.zipDir(dwcaDir, zip);
      LOG.info("Dwc archive completed at {} !", zip);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected abstract void parseData() throws Exception;

  protected abstract void addMetadata() throws Exception;

  protected void addMetadataProvider() {
    addContact("GBIF", "Markus", "Döring", "mdoering@gbif.org", ContactType.METADATA_AUTHOR);
  }

  protected void addContact(String org, String email) {
    addContact(org, null, null, email, ContactType.POINT_OF_CONTACT);
  }

  protected void addContactPerson(String first, String last, ContactType role) {
    addContact(null, first, last, null, role);
  }

  protected void addContact(String org, String email, ContactType role) {
    addContact(org, null, null, email, role);
  }

  protected void addContact(String org, String firstname, String lastname, String email) {
    addContact(org, firstname, lastname, email, ContactType.POINT_OF_CONTACT);
  }

  protected void addContact(String org, String firstname, String lastname, String email, ContactType role) {
    Contact contact = new Contact();
    contact.setType(role);
    contact.setOrganization(org);
    contact.setFirstName(firstname);
    contact.setLastName(lastname);
    contact.getEmail().add(email);
    dataset.getContacts().add(contact);
  }

  protected void setCitation(String citation) {
    Citation cite = new Citation();
    cite.setText(citation);
    dataset.setCitation(cite);
  }

  protected void addExternalData(String url, String name) {
    DataDescription dd = new DataDescription();
    dd.setName(name);
    dd.setUrl(uri(url));
    dataset.getDataDescriptions().add(dd);
  }

  protected void setPubDate(String isoDate) {
    Date d = new Date();
    dataset.setPubDate(d);
  }

  /**
   * Silently swallows errors converting a string to a URI
   */
  protected URI uri(String uri) {
    if (!StringUtils.isBlank(uri)) {
      try {
        return URI.create(uri);
      } catch (IllegalArgumentException e) {
        LOG.debug("Bogus URI {}", uri);
      }
    }
    return null;
  }

}
