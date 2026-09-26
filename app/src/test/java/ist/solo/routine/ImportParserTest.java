package ist.solo.routine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.List;

public class ImportParserTest {

    private static String one(String routineBody) {
        return "{\"v\":1,\"routines\":[{" + routineBody + "}]}";
    }

    private static void rejects(String json, String fragment) {
        try {
            ImportParser.parse(json);
            fail("accepted: " + json);
        } catch (ImportParser.ImportException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
        }
    }

    @Test
    public void parsesTheDocumentedExample() throws Exception {
        List<RoutineSpec> rs = ImportParser.parse(one(
                "\"name\":\"morning\",\"weekdays\":[\"mon\",\"tue\",\"wed\",\"thu\",\"fri\"],"
                        + "\"target\":\"07:30\",\"threshold\":75,"
                        + "\"steps\":[{\"name\":\"water\",\"min\":2},{\"name\":\"stretch\",\"sec\":90},"
                        + "{\"name\":\"make bed\"}]"));
        RoutineSpec r = rs.get(0);
        assertEquals("morning", r.name);
        assertEquals(0b0011111, r.weekdays);
        assertEquals(7 * 60 + 30, r.targetMinute);
        assertEquals(75, r.threshold);
        assertEquals(120, r.steps.get(0).durationSec);
        assertEquals(90, r.steps.get(1).durationSec);
        assertEquals(0, r.steps.get(2).durationSec);
        assertNull(r.steps.get(2).detail);
    }

    @Test
    public void acceptsFullDayNames() throws Exception {
        RoutineSpec r = ImportParser.parse(one(
                "\"name\":\"x\",\"weekdays\":[\"Saturday\",\"sunday\"],\"steps\":[{\"name\":\"a\"}]")).get(0);
        assertEquals(0b1100000, r.weekdays);
    }

    @Test
    public void enforcesLimits() {
        rejects(one("\"name\":\"x\",\"steps\":[]"), "1–30 steps");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 31; i++) many.append(i == 0 ? "" : ",").append("{\"name\":\"s\"}");
        rejects(one("\"name\":\"x\",\"steps\":[" + many + "]"), "1–30 steps");
        rejects(one("\"name\":\"" + "a".repeat(41) + "\",\"steps\":[{\"name\":\"s\"}]"), "over 40");
        rejects(one("\"name\":\"x\",\"steps\":[{\"name\":\"s\",\"min\":181}]"), "0–180 min");
        rejects(one("\"name\":\"x\",\"steps\":[{\"name\":\"s\",\"min\":1,\"sec\":1}]"), "not both");
        rejects(one("\"name\":\"  \",\"steps\":[{\"name\":\"s\"}]"), "empty name");
        rejects(one("\"name\":\"x\",\"target\":\"25:00\",\"steps\":[{\"name\":\"s\"}]"), "HH:MM");
        rejects(one("\"name\":\"x\",\"weekdays\":[\"funday\"],\"steps\":[{\"name\":\"s\"}]"), "unknown weekday");
        rejects("{\"v\":2,\"routines\":[]}", "unsupported version");
        rejects("not json", "not valid");
    }
}
