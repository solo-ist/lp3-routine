package ist.solo.routine;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Picks up {@code routines.json} dropped into the app's external files
 * directory:
 *
 * <pre>
 * adb push routines.json /sdcard/Android/data/ist.solo.routine/files/
 * </pre>
 *
 * Since Android 11 no other app can write there, so a file appearing in it
 * came from adb or from this app — not from something else on the phone
 * trying to plant routines. It is still shown as a preview and only saved
 * once confirmed, then deleted either way.
 */
final class ImportFile {
    static final String NAME = "routines.json";

    private final File file;

    ImportFile(Context c) {
        File dir = c.getExternalFilesDir(null);
        file = dir == null ? null : new File(dir, NAME);
    }

    boolean present() {
        return file != null && file.isFile();
    }

    List<RoutineSpec> read() throws ImportParser.ImportException {
        // Read at most one byte past the limit, rather than trusting a length
        // checked before the read: the file could grow in between.
        byte[] buf = new byte[ImportParser.MAX_BYTES + 1];
        int n = 0;
        try (InputStream in = new FileInputStream(file)) {
            for (int r; n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0; ) n += r;
        } catch (IOException e) {
            throw new ImportParser.ImportException("couldn't read " + NAME + ": " + e.getMessage());
        }
        if (n > ImportParser.MAX_BYTES) throw new ImportParser.ImportException("file is over 64 KB");
        return ImportParser.parse(new String(buf, 0, n, StandardCharsets.UTF_8));
    }

    void discard() {
        if (file != null) //noinspection ResultOfMethodCallIgnored
            file.delete();
    }
}
