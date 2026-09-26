package ist.solo.routine;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        if (file.length() > ImportParser.MAX_BYTES) throw new ImportParser.ImportException("file is over 64 KB");
        try {
            return ImportParser.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ImportParser.ImportException("couldn't read " + NAME + ": " + e.getMessage());
        }
    }

    void discard() {
        if (file != null) //noinspection ResultOfMethodCallIgnored
            file.delete();
    }
}
