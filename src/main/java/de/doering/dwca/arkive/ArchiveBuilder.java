/*
 * Copyright 2011 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.doering.dwca.arkive;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import de.doering.dwca.AbstractBuilder;
import de.doering.dwca.CliConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.gbif.api.model.common.MediaObject;
import org.gbif.api.vocabulary.DatasetType;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;

import java.io.IOException;
import java.net.URI;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArchiveBuilder extends AbstractBuilder {

    private static final int MAX_IMAGES_PER_SPECIES = 100;
    private static final int LOG_INTERVALL = 1000;
    private static final String API_KEY = "GWDG7LURAX";
    private static final String DB_URL = "jdbc:postgresql://boma.gbif.org:5432/clb-indexing";
    private static final String DB_USER = "portal";
    private static final String DB_PASS = "fdgt53hj";

    private static final String WEBSERVICE = "http://www.arkive.org/api/" + API_KEY + "/portlet/latin/{SPECIES}/" + MAX_IMAGES_PER_SPECIES + "?outputFormat=json&media=images";
    private static final Pattern imgParser = Pattern.compile("href=\"(http.+)\" title=\"(.+)\" .+img src=\"(http[^ ]+)\"");

    // metadata
    private static final String TITLE = "ARKive Images";
    private static final String HOMEPAGE = "http://www.arkive.org/";
    private static final String LOGO = "http://www.arkive.org/images/logos_external/arkive_black_trans_150.png";
    private static final String DESCRIPTION = "ARKive's mission is to promote a greater understanding of global biodiversity, and the conservation of the world's most threatened species, using the power of wildlife imagery.\n"
            + "ARKive is leading the 'virtual' conservation effort - gathering together films, photographs and audio recordings of the world's species, prioritising those most at risk of extinction, and building them into comprehensive multimedia digital profiles.\n"
            + "Using these films, photographs and audio recordings, ARKive is creating a unique record of the world's increasingly threatened biodiversity – complementing other species information datasets and making this key resource available for scientists, conservationists, educators and the general public."
            + "The ARKive project has unique access to the very best of the world's wildlife films and photographs, with more than 3,500 of the world's leading filmmakers and photographers actively contributing to the project, and giving ARKive unprecedented access to their materials. Contributors include the most famous names in natural history broadcasting, commercial film and picture agencies, leading academic institutions and international conservation organisations, as well as myriad individual filmmakers, photographers, scientists and conservationists.\n"
            + "ARKive has achieved significant success since its launch in 2003, with numerous awards and accolades, fantastic visitor rates from all round the world and an impressive line-up of international partners and strategic alliances. ARKive's priority is now the completion of audio-visual profiles for the c. 17,000 species on the IUCN Red List of Threatened Species.\n";
    private static final String CONTACT_ORG = "Wildscreen";
    private static final String CONTACT_EMAIL = "arkive@wildscreen.org.uk";

    private ObjectMapper jsonMapper = new ObjectMapper();
    private final TypeReference<HashMap<String, Object>> jsonMapTypeRef = new TypeReference<HashMap<String, Object>>() {
    };
    private int usageCounter;
    private int imageCounter;

    @Inject
    public ArchiveBuilder(CliConfiguration cfg) {
        super(DatasetType.CHECKLIST, cfg);
    }

    private void findSpecies(String name) {
        String url = WEBSERVICE.replace("{SPECIES}", name.replace(" ", "%20"));
        try {
            String resp = http.get(url);
            List<MediaObject> images = processJson(resp);
            if (!images.isEmpty()) {
                usageCounter++;
                writer.newRecord(name);
                writer.addCoreColumn(DwcTerm.scientificName, name);
                writer.addCoreColumn(DwcTerm.kingdom, "Animalia");

                for (MediaObject img : images) {
                    if (img == null) {
                        continue;
                    }
                    imageCounter++;
                    Map<Term, String> data = new HashMap<Term, String>();
                    data.put(DcTerm.identifier, img.getIdentifier().toString());
                    data.put(DcTerm.references, img.getReferences().toString());
                    data.put(DcTerm.title, img.getTitle());
                    data.put(DcTerm.creator, img.getCreator());
                    writer.addExtensionRecord(GbifTerm.Image, data);
                }
            }

        } catch (Exception e) {
            LOG.error("Exception for species {}", name, e);
        }
    }

    /**
     * {
     * "error":null,
     * "result":null,
     * "results":[
     * "\u003ca href=\"http://www.arkive.org/puma/puma-concolor/image-G6642.html#src=portletV3api\" title=\"ARKive image - Florida panther kitten at three weeks
     * old\" \u003e\u003cimg src=\"http://cdn1.arkive.org/media/05/0511201D-A928-4992-9C43-C3E28C319061/Presentation.Portlet/Florida-panther-kitten-at-three-weeks-old.jpg\"
     * alt=\"ARKive image - Florida panther kitten at three weeks old\" title=\"ARKive image - Florida panther kitten at three weeks old\"
     * border=\"0\"/\u003e\u003c/a\u003e",
     * "\u003ca href=\"http://www.arkive.org/puma/puma-concolor/image-G18547.html#src=portletV3api\" title=\"ARKive image - Florida panther kitten at three
     * months old\" \u003e\u003cimg src=\"http://cdn2.arkive.org/media/96/96378ABA-FA71-4CBA-A14F-91DD7BA1EE7F/Presentation.Portlet/Florida-panther-kitten-at-three-months-old.jpg\"
     * alt=\"ARKive image - Florida panther kitten at three months old\" title=\"ARKive image - Florida panther kitten at three months old\"
     * border=\"0\"/\u003e\u003c/a\u003e",
     * "\u003ca href=\"http://www.arkive.org/puma/puma-concolor/image-G6643.html#src=portletV3api\" title=\"ARKive image - Florida panther at five months old\"
     * \u003e\u003cimg src=\"http://cdn2.arkive.org/media/92/92752C53-3579-4BE3-9CC0-89AFF0D6CF32/Presentation.Portlet/Florida-panther-at-five-months-old.jpg\"
     * alt=\"ARKive image - Florida panther at five months old\" title=\"ARKive image - Florida panther at five months old\" border=\"0\"/\u003e\u003c/a\u003e"
     * ]
     * }
     */
    private List<MediaObject> processJson(String json) {
        List<MediaObject> images = Lists.newArrayList();
        try {
            Map<String, Object> data = jsonMapper.readValue(json, jsonMapTypeRef);
            List<String> results = (List<String>) data.get("results");
            if (results != null) {
                for (String res : results) {
                    MediaObject img = parseHtml(res);
                    if (img != null) {
                        images.add(img);
                    }
                }
            }

        } catch (IOException e) {
            LOG.error("Cant deserialize json {}", json, e);
        }

        return images;
    }

    private MediaObject parseHtml(String html) {
        Matcher m = imgParser.matcher(html);
        if (m.find()) {
            MediaObject img = new MediaObject();
            img.setReferences(uri(m.group(1)));
            img.setTitle(StringUtils.removeStart(m.group(2), "ARKive image - "));
            img.setIdentifier(thumbnailToImageUrl(m.group(3)));
            return img;
        }

        return null;
    }

    /**
     * turns:
     * http://cdn2.arkive.org/media/92/92752C53-3579-4BE3-9CC0-89AFF0D6CF32/Presentation.Portlet/Florida-panther-at-five-months-old.jpg
     * into:
     * http://cdn2.arkive.org/media/92/92752C53-3579-4BE3-9CC0-89AFF0D6CF32/Presentation.Large/Florida-panther-at-five-months-old.jpg
     */
    private URI thumbnailToImageUrl(String thumbnail) {
        return uri(thumbnail.replace("Presentation.Portlet", "Presentation.Large"));
    }

    protected void parseData() throws IOException {
        int count = 0;
        LOG.info("Query for nub species");

        try {
            Connection con = getConnection();
            con.setReadOnly(true);
            Statement sta = con.createStatement();
            // get all nub names which are species or below
            ResultSet res = sta.executeQuery("SELECT distinct cn.scientific_name FROM name_usage u join name_string n on u.name_fk=n.id join name_string cn on n.canonical_name_fk=cn.id where checklist_fk=1 and rank_fk>=600");

            LOG.info("Iterate over nub species");
            while (res.next()) {
                String name = res.getString(1);
                if (count % LOG_INTERVALL == 0) {
                    LOG.debug("{} names found with {} images out of {} searched nub names", new Object[]{usageCounter, imageCounter, count});
                }
                findSpecies(name);
                count++;
            }
            res.close();
            sta.close();
            con.close();

        } catch (SQLException e) {
            // TODO: Handle exception
        }

        LOG.info("Finished with {} names found with {} images out of {} searched nub names", new Object[]{usageCounter, imageCounter, count});
    }

    @Override
    protected void addMetadata() {
        dataset.setTitle(TITLE);
        dataset.setDescription(DESCRIPTION);
        dataset.setHomepage(uri(HOMEPAGE));
        dataset.setLogoUrl(uri(LOGO));
        addContact(CONTACT_ORG, CONTACT_EMAIL);
    }

    private Connection getConnection() {
        //See your driver documentation for the proper format of this string :
        final String DRIVER_CLASS_NAME = "org.postgresql.Driver";
        try {
            Class.forName(DRIVER_CLASS_NAME).newInstance();
        } catch (Exception ex) {
            LOG.error("Check classpath. Cannot load db driver: {}", DRIVER_CLASS_NAME);
            throw new RuntimeException(ex);
        }

        try {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        } catch (SQLException e) {
            LOG.error("Cannot connect to db {} ", DB_URL);
            throw new RuntimeException(e);
        }
    }


}
