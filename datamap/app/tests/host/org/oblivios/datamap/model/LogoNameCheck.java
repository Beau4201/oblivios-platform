// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** usage: LogoNameCheck logo-names.tsv  (owner identifier, tab, resource name; tools/import-logos.py reads the same file) */
public final class LogoNameCheck {
    public static void main(String[] args) throws Exception {
        int n = 0;
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            if (f.length != 2) throw new AssertionError("bad fixture line: " + line);
            if (!LogoName.of(f[0]).equals(f[1])) {
                throw new AssertionError("logo name of <" + f[0] + "> expected <" + f[1] + "> but was <"
                        + LogoName.of(f[0]) + ">");
            }
            n++;
        }
        if (n < 6) throw new AssertionError("the fixture has too few lines: " + n);
        System.out.println("PASS LogoNameCheck");
    }
}
