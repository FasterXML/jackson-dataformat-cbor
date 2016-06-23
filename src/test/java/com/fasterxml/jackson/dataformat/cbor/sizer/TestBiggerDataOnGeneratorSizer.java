package com.fasterxml.jackson.dataformat.cbor.sizer;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.cbor.TestBiggerData;

/**
 * Bigger test to try to do smoke-testing of overall functionality, using more
 * sizable (500k of JSON, 200k of encoded data) dataset. Should tease out at
 * least some of boundary conditions.
 */
public class TestBiggerDataOnGeneratorSizer extends TestBiggerData {
    final ObjectMapper MAPPER = new ObjectMapper();

    public void testReadingOnGeneratorSizer() throws Exception {
        Citm citm0 = MAPPER.readValue(getClass().getResourceAsStream("/data/citm_catalog.json"), Citm.class);

        ObjectMapper mapper = cborMapper();
        byte[] cbor = mapper.writeValueAsBytes(citm0);

        Citm citm = mapper.readValue(cbor, Citm.class);

        assertNotNull(citm);
        assertNotNull(citm.areaNames);
        assertEquals(17, citm.areaNames.size());
        assertNotNull(citm.events);
        assertEquals(184, citm.events.size());

        assertNotNull(citm.seatCategoryNames);
        assertEquals(64, citm.seatCategoryNames.size());
        assertNotNull(citm.subTopicNames);
        assertEquals(19, citm.subTopicNames.size());
        assertNotNull(citm.subjectNames);
        assertEquals(0, citm.subjectNames.size());
        assertNotNull(citm.topicNames);
        assertEquals(4, citm.topicNames.size());
        assertNotNull(citm.topicSubTopics);
        assertEquals(4, citm.topicSubTopics.size());
        assertNotNull(citm.venueNames);
        assertEquals(1, citm.venueNames.size());
    }

    public void testRoundTripOnGeneratorSizer() throws Exception {
        Citm citm0 = MAPPER.readValue(getClass().getResourceAsStream("/data/citm_catalog.json"), Citm.class);
        ObjectMapper mapper = cborMapper();
        byte[] cbor = mapper.writeValueAsBytes(citm0);

        Citm citm = mapper.readValue(cbor, Citm.class);

        byte[] smile1 = mapper.writeValueAsBytes(citm);
        Citm citm2 = mapper.readValue(smile1, Citm.class);
        byte[] smile2 = mapper.writeValueAsBytes(citm2);

        assertEquals(smile1.length, smile2.length);

        assertNotNull(citm.areaNames);
        assertEquals(17, citm.areaNames.size());
        assertNotNull(citm.events);
        assertEquals(184, citm.events.size());

        assertEquals(citm.seatCategoryNames.size(), citm2.seatCategoryNames.size());
        assertEquals(citm.subTopicNames.size(), citm2.subTopicNames.size());
        assertEquals(citm.subjectNames.size(), citm2.subjectNames.size());
        assertEquals(citm.topicNames.size(), citm2.topicNames.size());
        assertEquals(citm.topicSubTopics.size(), citm2.topicSubTopics.size());
        assertEquals(citm.venueNames.size(), citm2.venueNames.size());
    }
}
