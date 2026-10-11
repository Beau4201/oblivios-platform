// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Parses a classifier file with the parser that the device uses, so that a file this accepts is a file the
 * device accepts. Exit status 1 and the line of the first fault if it is refused.
 * usage: ClassifierFileCheck FILE
 */
public final class ClassifierFileCheck {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(args[0]));
        if (bytes.length > 4 * 1024 * 1024) {   // DataMapService.MAX_CLASSIFIER_BYTES
            System.err.println("REFUSED: the file is larger than the device reads (4 MiB)");
            System.exit(1);
        }
        try {
            Classifier c = Classifier.parse(new String(bytes, StandardCharsets.UTF_8));
            System.out.println("classifier ok: " + c.serviceCount() + " services, " + c.owners().size()
                    + " owners, " + c.relations().size() + " relations");
        } catch (ClassifierException e) {
            System.err.println("REFUSED: " + e.getMessage());
            System.exit(1);
        }
    }
}
