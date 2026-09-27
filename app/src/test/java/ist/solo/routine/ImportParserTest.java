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
    public void hugeMinutesAreRejectedNotWrapped() {
        // Audit R-03: 71582789 * 60 narrowed to an int is 44 seconds, and
        // -71582788 * 60 is 16 — both used to pass as valid timers.
        rejects(one("\"name\":\"x\",\"steps\":[{\"name\":\"s\",\"min\":71582789}]"), "0–180 min");
        rejects(one("\"name\":\"x\",\"steps\":[{\"name\":\"s\",\"min\":-71582788}]"), "0–180 min");
        rejects(one("\"name\":\"x\",\"steps\":[{\"name\":\"s\",\"sec\":4294967340}]"), "sec must be 0–10800");
        rejects(one("\"name\":\"x\",\"steps\":[{\"name\":\"s\",\"min\":1e400}]"), "out of range");
    }

    @Test
    public void numbersMustBeNumbers() {
        rejects(one("\"name\":\"x\",\"steps\":[{\"name\":\"s\",\"min\":\"5\"}]"), "min must be a number");
        rejects(one("\"name\":\"x\",\"steps\":[{\"name\":\"s\",\"sec\":1.5}]"), "whole number");
        rejects(one("\"name\":\"x\",\"threshold\":\"75\",\"steps\":[{\"name\":\"s\"}]"), "threshold must be a number");
        rejects(one("\"name\":\"x\",\"threshold\":75.5,\"steps\":[{\"name\":\"s\"}]"), "whole number");
        rejects("{\"v\":\"1\",\"routines\":[{\"name\":\"x\",\"steps\":[{\"name\":\"s\"}]}]}", "v must be a number");
    }

    @Test
    public void boundariesAreInclusive() throws Exception {
        RoutineSpec r = ImportParser.parse(one(
                "\"name\":\"x\",\"threshold\":1,\"steps\":[{\"name\":\"a\",\"min\":180},"
                        + "{\"name\":\"b\",\"sec\":10800},{\"name\":\"c\",\"min\":0},{\"name\":\"d\",\"min\":0.5}]")).get(0);
        assertEquals(10800, r.steps.get(0).durationSec);
        assertEquals(10800, r.steps.get(1).durationSec);
        assertEquals(0, r.steps.get(2).durationSec);
        assertEquals(30, r.steps.get(3).durationSec);
        assertEquals(1, r.threshold);
    }

    @Test
    public void deepNestingIsRefusedBeforeParsing() {
        // An unknown field nested far deeper than the format ever goes. It
        // fits comfortably in 64 KB and would recurse the JSON tokenizer.
        String deep = "[".repeat(5000) + "]".repeat(5000);
        rejects("{\"junk\":" + deep + ",\"routines\":[]}", "nested more than");
        assertEquals(3, ImportParser.depth("{\"routines\":[{\"steps\":[]}]}") - 1);
        assertEquals(1, ImportParser.depth("{\"name\":\"[[[{{{\\\"\"}"));
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
