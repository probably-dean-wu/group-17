package org.accessgraph.data_access;

import org.accessgraph.data_access.GtfsRealtimeText.Block;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GtfsRealtimeTextTest {
    @Test
    void readsNestedBlocksAndRepeatedFields() throws IOException {
        Block feed = GtfsRealtimeText.parse(String.join("\r\n",
                "header {",
                "  gtfs_realtime_version: \"2.0\"",
                "  timestamp: 1795192640",
                "}",
                "entity {",
                "  id: \"80511\"",
                "  alert {",
                "    informed_entity {",
                "      route_id: \"1\"",
                "      stop_id: \"15659\"",
                "    }",
                "    informed_entity {",
                "      route_id: \"1\"",
                "      stop_id: \"15658\"",
                "    }",
                "    effect: NO_SERVICE",
                "  }",
                "}",
                "entity {",
                "  id: \"80512\"",
                "}", ""));

        assertEquals("2.0", feed.blocks("header").get(0).value("gtfs_realtime_version"));
        assertEquals("1795192640", feed.blocks("header").get(0).value("timestamp"));
        assertEquals(2, feed.blocks("entity").size());
        Block alert = feed.blocks("entity").get(0).blocks("alert").get(0);
        assertEquals("NO_SERVICE", alert.value("effect"));
        assertEquals(2, alert.blocks("informed_entity").size());
        assertEquals("15658", alert.blocks("informed_entity").get(1).value("stop_id"));
        assertEquals("80512", feed.blocks("entity").get(1).value("id"));
    }

    @Test
    void absentFieldsAreEmptyNotErrors() throws IOException {
        Block feed = GtfsRealtimeText.parse("header {\n}\n");

        assertNull(feed.blocks("header").get(0).value("timestamp"));
        assertTrue(feed.blocks("header").get(0).values("timestamp").isEmpty());
        assertTrue(feed.blocks("entity").isEmpty());
    }

    @Test
    void keepsRepeatedValuesInOrder() throws IOException {
        Block block = GtfsRealtimeText.parse("stop_id: \"1\"\nstop_id: \"2\"\n");
        assertEquals(List.of("1", "2"), block.values("stop_id"));
    }

    @Test
    void resolvesEscapesInsideStrings() throws IOException {
        Block block = GtfsRealtimeText.parse(
                "text:     \"Queen\\'s Park: \\\"Elevator\\\" caf\\303\\251 {out}\\nline two \\\\ end\"\n"
                        + "plain: \"Bay: no service\"\n");

        assertEquals("Queen's Park: \"Elevator\" café {out}\nline two \\ end", block.value("text"));
        assertEquals("Bay: no service", block.value("plain"));
    }

    @Test
    void rejectsTextThatIsNotAFeed() {
        assertThrows(IOException.class, () -> GtfsRealtimeText.parse("entity {\n  id: \"1\"\n"));
        assertThrows(IOException.class, () -> GtfsRealtimeText.parse("}\n"));
        assertThrows(IOException.class, () -> GtfsRealtimeText.parse("<!DOCTYPE html>\n"));
        assertThrows(IOException.class, () -> GtfsRealtimeText.parse("text: \"never closed\n"));
    }
}
