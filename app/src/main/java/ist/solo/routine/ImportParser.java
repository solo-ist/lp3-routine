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
    private static final String[] DAYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    static final class ImportException extends Exception {
        ImportException(String message) {
            super(message);
        }
    }

    static List<RoutineSpec> parse(String text) throws ImportException {
        if (text.length() > MAX_BYTES) throw new ImportException("file is over 64 KB");
        try {
            JSONObject root = new JSONObject(text);
            int v = root.optInt("v", 1);
            if (v != 1) throw new ImportException("unsupported version " + v);
            JSONArray arr = root.getJSONArray("routines");
            if (arr.length() == 0) throw new ImportException("no routines");
            if (arr.length() > MAX_ROUTINES) throw new ImportException("more than " + MAX_ROUTINES + " routines");
            List<RoutineSpec> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) out.add(routine(arr.getJSONObject(i), i + 1));
            return out;
        } catch (JSONException e) {
            throw new ImportException("not valid routine json: " + e.getMessage());
        }
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

        r.threshold = o.optInt("threshold", 100);
        if (r.threshold < 1 || r.threshold > 100) throw new ImportException(where + ": threshold must be 1–100");

        JSONArray steps = o.getJSONArray("steps");
        if (steps.length() < 1 || steps.length() > RoutineSpec.MAX_STEPS) {
            throw new ImportException(where + ": needs 1–" + RoutineSpec.MAX_STEPS + " steps");
        }
        for (int i = 0; i < steps.length(); i++) {
            JSONObject s = steps.getJSONObject(i);
            String sn = name(s.getString("name"), where + " step " + (i + 1));
            int sec;
            if (s.has("min") && s.has("sec")) throw new ImportException(where + ", " + sn + ": give min or sec, not both");
            else if (s.has("min")) sec = (int) Math.round(s.getDouble("min") * 60);
            else if (s.has("sec")) sec = s.getInt("sec");
            else sec = 0;
            if (sec < 0 || sec > RoutineSpec.MAX_STEP_MINUTES * 60) {
                throw new ImportException(where + ", " + sn + ": duration must be 0–" + RoutineSpec.MAX_STEP_MINUTES + " min");
            }
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
