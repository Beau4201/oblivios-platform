// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The user's choices and consent in device-protected storage, so that the apps on Tor are closed from the first moment of a
 * boot, before the first unlock. Each file is replaced as a whole (a crash leaves the old one or the new one). Used from
 * the runtime's one thread.
 */
final class AppStore implements Store {
    private static final String CHOICES = "choices";
    private static final String CONSENT = "consent";
    /** A choices file is a few kilobytes at most ({@link Choices#MAX_FILE_CHARS}); a larger one is not ours. */
    private static final int MAX_BYTES = 4 * Choices.MAX_FILE_CHARS;

    private final File dir;
    private Choices choices;
    private Consent consent;

    AppStore(Context ctx) {
        dir = ctx.createDeviceProtectedStorageContext().getFilesDir();
        choices = Choices.parse(read(CHOICES));
        consent = Consent.parse(read(CONSENT));
    }

    private String read(String name) {
        File f = new File(dir, name);
        if (!f.exists() || f.length() > MAX_BYTES) {
            return f.exists() ? "damaged" : null;
        }
        try {
            return new String(new AtomicFile(f).readFully(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "damaged";
        }
    }

    private void write(String name, String text) throws IOException {
        AtomicFile f = new AtomicFile(new File(dir, name));
        FileOutputStream out = f.startWrite();
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            f.finishWrite(out);
        } catch (IOException e) {
            f.failWrite(out);
            throw e;
        }
    }

    @Override
    public Choices choices() {
        return choices;
    }

    @Override
    public Consent consent() {
        return consent;
    }

    /** Writes the choices as they are now. */
    void saveChoices() throws IOException {
        write(CHOICES, choices.serialize());
    }

    void setConsent(Consent c) throws IOException {
        write(CONSENT, c.serialize());
        consent = c;
    }
}
