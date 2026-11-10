// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

/** Where the user's choices and consent live (device-protected storage on a device, memory in the host checks). */
public interface Store {
    /** The choices as the controller must see them now; the same object as long as nothing changed. */
    Choices choices();

    Consent consent();
}
