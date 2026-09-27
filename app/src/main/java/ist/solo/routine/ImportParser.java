package ist.solo.routine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses a routine file authored on a computer — the way routines get onto the
 * phone until the on-device editor exists, and the format a QR import will use
 * later (P1-7).
 *
 * <pre>
 * {
 *   "v": 1,
 *   "routines": [
 *     {
 *       "name": "morning",
 *       "weekdays": ["mon", "tue", "wed", "thu", "fri"],
 *       "target": "07:30",
 *       "threshold": 100,
 *       "steps": [
 *         { "name": "water", "min": 2 },
 *         { "name": "stretch", "sec": 90 },
 *         { "name": "make bed" }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * A step with no duration is untimed. Everything is validated against the
 * same limits the editor will enforce, and a file that fails any check is
 * rejected whole — a partial import would be harder to notice than none.
 */
final class ImportParser {
    private ImportParser() {}

    static final int MAX_BYTES = 64 * 1024;
    static final int MAX_ROUTINES = 50;
    /**
     * The format is three levels deep ({routines: [{steps: [{…}]}]}), so 8 is
     * generous. The JSON tokenizer recurses per level, and a 64 KB file can
     * nest thousands deep: without this, an unknown field could overflow the
     * stack before shape validation ever ran (security audit 2026-09-27).
     */
    static final int MAX_DEPTH = 8;
    private static final String[] DAYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    static final class ImportException extends Exception {
        ImportException(String message) {
            super(message);
        }
    }

    static List<RoutineSpec> parse(String text) throws ImportException {
        if (text.length() > MAX_BYTES) throw new ImportException("file is over 64 KB");
        if (depth(text) > MAX_DEPTH) throw new ImportException("nested more than " + MAX_DEPTH + " levels deep");
        try {
            JSONObject root = new JSONObject(text);
            if (root.has("v")) {
                long v = integer(root, "v", 1, Integer.MAX_VALUE, "file");
                if (v != 1) throw new ImportException("unsupported version " + v);
            }
            JSONArray arr = root.getJSONArray("routines");
            if (arr.length() == 0) throw new ImportException("no routines");
            if (arr.length() > MAX_ROUTINES) throw new ImportException("more than " + MAX_ROUTINES + " routines");
            List<RoutineSpec> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) out.add(routine(arr.getJSONObject(i), i + 1));
            return out;
        } catch (JSONException e) {
            throw new ImportException("not valid routine json: " + e.getMessage());
        } catch (RuntimeException | StackOverflowError e) {
            // Belt and braces: a parser failure must end in "can't import"
            // with a discard button, never a crash on every launch.
            throw new ImportException("not valid routine json");
        }
    }

    /**
     * Deepest nesting of objects and arrays, counted without parsing — the
     * point is to refuse before the recursive parser sees the text. Brackets
     * inside strings don't count.
     */
    static int depth(String text) {
        int depth = 0, max = 0;
        boolean inString = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                max = Math.max(max, ++depth);
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
        return max;
    }

    /**
     * A JSON number, checked for type and finiteness before anything else
     * touches it. org.json's getInt/getDouble coerce strings and silently
     * truncate, and narrowing before a range check is how 71582789 minutes
     * became a valid 44 seconds (security audit 2026-09-27, R-03).
     */
    private static double number(JSONObject o, String key, String where) throws JSONException, ImportException {
        Object v = o.get(key);
        if (!(v instanceof Number)) throw new ImportException(where + ": " + key + " must be a number");
        double d = ((Number) v).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) throw new ImportException(where + ": " + key + " is out of range");
        return d;
    }

    /** A whole number in [min, max], validated before narrowing. */
    private static long integer(JSONObject o, String key, long min, long max, String where)
            throws JSONException, ImportException {
        double d = number(o, key, where);
        if (d != Math.rint(d)) throw new ImportException(where + ": " + key + " must be a whole number");
        if (d < min || d > max) throw new ImportException(where + ": " + key + " must be " + min + "–" + max);
        return (long) d;
    }

    private static RoutineSpec routine(JSONObject o, int n) throws JSONException, ImportException {
        RoutineSpec r = new RoutineSpec();
        r.name = name(o.getString("name"), "routine " + n);
        String where = "routine \"" + r.name + "\"";

        JSONArray days = o.optJSONArray("weekdays");
        if (days != null) {
            for (int i = 0; i < days.length(); i++) {
                int d = dayIndex(days.getString(i));
                if (d < 0) throw new ImportException(where + ": unknown weekday \"" + days.getString(i) + "\"");
                r.weekdays |= 1 << d;
            }
        }

        String target = o.optString("target", "");
        if (!target.isEmpty()) r.targetMinute = time(target, where);

        if (o.has("threshold")) r.threshold = (int) integer(o, "threshold", 1, 100, where);

        JSONArray steps = o.getJSONArray("steps");
        if (steps.length() < 1 || steps.length() > RoutineSpec.MAX_STEPS) {
            throw new ImportException(where + ": needs 1–" + RoutineSpec.MAX_STEPS + " steps");
        }
        for (int i = 0; i < steps.length(); i++) {
            JSONObject s = steps.getJSONObject(i);
            String sn = name(s.getString("name"), where + " step " + (i + 1));
            String at = where + ", " + sn;
            int sec;
            if (s.has("min") && s.has("sec")) throw new ImportException(at + ": give min or sec, not both");
            else if (s.has("min")) {
                // Range-check the minutes themselves, then convert.
                double min = number(s, "min", at);
                if (min < 0 || min > RoutineSpec.MAX_STEP_MINUTES) {
                    throw new ImportException(at + ": duration must be 0–" + RoutineSpec.MAX_STEP_MINUTES + " min");
                }
                sec = (int) Math.round(min * 60);
            } else if (s.has("sec")) {
                sec = (int) integer(s, "sec", 0, RoutineSpec.MAX_STEP_MINUTES * 60L, at);
            } else sec = 0;
            RoutineSpec.Step step = new RoutineSpec.Step(sn, sec);
            String detail = s.optString("detail", "").trim();
            if (!detail.isEmpty()) step.detail = detail.length() > 120 ? detail.substring(0, 120) : detail;
            r.steps.add(step);
        }
        return r;
    }

    private static String name(String raw, String where) throws ImportException {
        String s = raw.trim();
        if (s.isEmpty()) throw new ImportException(where + ": empty name");
        if (s.length() > RoutineSpec.MAX_NAME) {
            throw new ImportException(where + ": \"" + s + "\" is over " + RoutineSpec.MAX_NAME + " characters");
        }
        return s;
    }

    private static int dayIndex(String s) {
        String k = s.trim().toLowerCase(Locale.ROOT);
        if (k.length() > 3) k = k.substring(0, 3);
        for (int i = 0; i < DAYS.length; i++) if (DAYS[i].equals(k)) return i;
        return -1;
    }

    private static int time(String s, String where) throws ImportException {
        String[] p = s.trim().split(":");
        try {
            if (p.length == 2) {
                int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
                if (h >= 0 && h < 24 && m >= 0 && m < 60) return h * 60 + m;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new ImportException(where + ": target \"" + s + "\" is not HH:MM");
    }
}
