// SPDX-License-Identifier: Apache-2.0
package com.android.internal.compartments;

import android.content.Intent;
import android.os.Bundle;

import java.util.TimeZone;

/** Host test of PersonaProcess: one scenario per JVM, because a process is bound once. Usage: PersonaProcessTest SCENARIO */
public final class PersonaProcessTest {
    private static void check(boolean cond, String what) {
        if (!cond) {
            throw new AssertionError(what);
        }
    }

    private static Bundle persona(String tz) {
        Bundle b = new Bundle();
        PersonaProcess.putInto(b, tz, "es", "21407");
        return b;
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        switch (args[0]) {
            case "none": // an app without a persona: nothing changes
                PersonaProcess.setFrom(new Bundle());
                check(PersonaProcess.timeZone() == null && PersonaProcess.country() == null && PersonaProcess.operator() == null, "no values");
                for (String key : new String[] {"persist.sys.timezone", "gsm.operator.numeric", "gsm.operator.iso-country",
                        "gsm.operator.alpha", "gsm.operator.isroaming"}) {
                    check(PersonaProcess.property(key) == null, "no persona, no property " + key);
                }
                PersonaProcess.applyTimeZone();
                check(TimeZone.getDefault().getID().equals("America/Los_Angeles"), "the time zone is left alone");
                Intent real = new Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra(Intent.EXTRA_TIMEZONE, "Asia/Tokyo");
                PersonaProcess.timeZoneChanged(real);
                check("Asia/Tokyo".equals(real.getStringExtra(Intent.EXTRA_TIMEZONE)), "no persona: the broadcast is left alone");
                break;
            case "null": // a process with no core settings at all
                PersonaProcess.setFrom(null);
                check(PersonaProcess.country() == null, "no values");
                break;
            case "full":
                PersonaProcess.setFrom(persona("Europe/Madrid"));
                check("Europe/Madrid".equals(PersonaProcess.timeZone()) && "es".equals(PersonaProcess.country())
                        && "21407".equals(PersonaProcess.operator()), "the three values");
                check("Europe/Madrid".equals(PersonaProcess.property("persist.sys.timezone")), "the zone property");
                check("21407".equals(PersonaProcess.property("gsm.operator.numeric")), "the operator property");
                check("es".equals(PersonaProcess.property("gsm.operator.iso-country")), "the country property");
                check("".equals(PersonaProcess.property("gsm.operator.alpha")), "the operator name is empty");
                check("false".equals(PersonaProcess.property("gsm.operator.isroaming")), "never roaming");
                check(PersonaProcess.property("ro.build.id") == null && PersonaProcess.property("gsm.sim.operator.numeric") == null
                        && PersonaProcess.property(null) == null, "other properties are not touched");
                PersonaProcess.applyTimeZone();
                check(TimeZone.getDefault().getID().equals("Europe/Madrid"), "the persona time zone is the default");
                TimeZone.setDefault(null); // what a time zone change does
                PersonaProcess.applyTimeZone();
                check(TimeZone.getDefault().getID().equals("Europe/Madrid"), "and stays after a time zone change");
                Intent changed = new Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra(Intent.EXTRA_TIMEZONE, "Asia/Tokyo");
                PersonaProcess.timeZoneChanged(changed);
                check("Europe/Madrid".equals(changed.getStringExtra(Intent.EXTRA_TIMEZONE)), "the broadcast reports the persona zone");
                Intent other = new Intent("android.intent.action.TIME_SET");
                PersonaProcess.timeZoneChanged(other);
                check(!other.hasExtra(Intent.EXTRA_TIMEZONE), "another broadcast gets no zone");
                Intent noAction = new Intent(null);
                PersonaProcess.timeZoneChanged(noAction);
                PersonaProcess.timeZoneChanged(null);
                check(!noAction.hasExtra(Intent.EXTRA_TIMEZONE), "no action, no null intent trouble");
                break;
            case "badzone": // an id this system does not know leaves the default
                PersonaProcess.setFrom(persona("Mars/Olympus"));
                PersonaProcess.applyTimeZone();
                check(TimeZone.getDefault().getID().equals("America/Los_Angeles") && "es".equals(PersonaProcess.country()), "unknown zone");
                break;
            case "partial": // a missing key means no persona at all
                Bundle b = new Bundle();
                b.putString(PersonaProcess.KEY_COUNTRY, "es");
                PersonaProcess.setFrom(b);
                check(PersonaProcess.country() == null && PersonaProcess.timeZone() == null, "a bundle with one key");
                break;
            case "twice": // a second push of the core settings does not replace the values
                PersonaProcess.setFrom(persona("Europe/Madrid"));
                boolean threw = false;
                try {
                    PersonaProcess.setFrom(persona("Asia/Tokyo"));
                } catch (IllegalStateException e) {
                    threw = true;
                }
                check(threw && "Europe/Madrid".equals(PersonaProcess.timeZone()), "the second call throws and changes nothing");
                break;
            default:
                throw new IllegalArgumentException(args[0]);
        }
        System.out.println("ok " + args[0]);
    }
}
