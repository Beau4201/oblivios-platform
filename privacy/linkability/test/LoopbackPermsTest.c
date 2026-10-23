/* SPDX-License-Identifier: Apache-2.0
 * Host check of the loopback permission logic of packages/modules/Connectivity bpf/progs/netd.c (patches
 * packages-modules-connectivity/0001 and 0002; review F13). run-loopback-test.sh extracts the real functions
 * can_force_loopback, uids_have_loopback_permissions and uid_may_use_loopback and the permission bits and
 * is_system_uid from the files it is given, so this test judges the code that is built into the module, not a copy.
 * The reference below states the rule again in other words; every combination of two uids and of the permission
 * bits is compared with it.
 */
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

#define function static
#define AID_USER_OFFSET 100000
#define AID_APP_START 10000

static uint32_t g_perms[2];
static uint32_t g_uids[2];

static uint8_t get_chunk_permissions(const uint32_t uid) {
    return (uint8_t)(uid == g_uids[0] ? g_perms[0] : g_perms[1]);
}

#include "extracted.h"

static int failures;
static int checks;

static void expect(bool got, bool want, const char *what, uint32_t s, uint32_t r, uint32_t sp, uint32_t rp) {
    checks++;
    if (got != want) {
        failures++;
        printf("FAIL %s: sender %u (perms %u) receiver %u (perms %u): got %d want %d\n", what, s, sp, r, rp, got, want);
    }
}

static bool ref_system(uint32_t uid) { return uid % AID_USER_OFFSET < AID_APP_START; }
static bool ref_force(uint32_t p) {
    return (p & PERMISSION_BIT_FORCE_USE_LOOPBACK_INTERFACE) && (p & PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL);
}
static bool ref_use(uint32_t p) { return (p & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0; }

static bool ref_allowed(uint32_t s, uint32_t r, uint32_t sp, uint32_t rp) {
    if (ref_system(s) || ref_system(r)) return true;
    if (ref_force(sp) || ref_force(rp)) return true;
    if (s / AID_USER_OFFSET == r / AID_USER_OFFSET) return ref_use(sp) || ref_use(rp); /* one user */
    if (s % AID_USER_OFFSET == r % AID_USER_OFFSET) {                                 /* same app, other user */
        return (sp & (PERMISSION_BIT_INTERACT_ACROSS_USERS_OR_PROFILES | PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL)) != 0;
    }
    return (sp & PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL) != 0; /* other user, other app */
}

int main(void) {
    const uint32_t uids[] = {0, 1000, 9999, 10100, 10101, 1010100, 1010101, 1099999, 2010100};
    const uint32_t n = sizeof(uids) / sizeof(uids[0]);
    const uint32_t bits[] = {PERMISSION_BIT_USE_LOOPBACK_INTERFACE, PERMISSION_BIT_FORCE_USE_LOOPBACK_INTERFACE,
                             PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL, PERMISSION_BIT_INTERACT_ACROSS_USERS_OR_PROFILES,
                             PERMISSION_BIT_NO_INTERNET};
    const uint32_t nbits = sizeof(bits) / sizeof(bits[0]);

    /* The named claims first, so a failure reads as a sentence. */
    g_uids[0] = 10100; g_uids[1] = 10101;
    g_perms[0] = 0; g_perms[1] = 0;
    expect(uids_have_loopback_permissions(10100, 10101), false, "two apps of one user without a permission", 10100, 10101, 0, 0);
    g_perms[0] = PERMISSION_BIT_USE_LOOPBACK_INTERFACE;
    expect(uids_have_loopback_permissions(10100, 10101), true, "the sender holds USE_LOOPBACK_INTERFACE", 10100, 10101, g_perms[0], 0);
    g_perms[0] = 0; g_perms[1] = PERMISSION_BIT_USE_LOOPBACK_INTERFACE;
    expect(uids_have_loopback_permissions(10100, 10101), true, "the receiver holds USE_LOOPBACK_INTERFACE", 10100, 10101, 0, g_perms[1]);
    g_perms[0] = 0; g_perms[1] = PERMISSION_BIT_FORCE_USE_LOOPBACK_INTERFACE;
    expect(uids_have_loopback_permissions(10100, 10101), false, "FORCE alone is not enough", 10100, 10101, 0, g_perms[1]);
    g_perms[1] = PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL;
    expect(uids_have_loopback_permissions(10100, 10101), false, "INTERACT_ACROSS_USERS_FULL alone is not enough in one user", 10100, 10101, 0, g_perms[1]);
    g_uids[0] = 10100; g_uids[1] = 1010101;
    g_perms[0] = PERMISSION_BIT_USE_LOOPBACK_INTERFACE; g_perms[1] = PERMISSION_BIT_USE_LOOPBACK_INTERFACE;
    expect(uids_have_loopback_permissions(10100, 1010101), false, "USE_LOOPBACK_INTERFACE does not cross users", 10100, 1010101, g_perms[0], g_perms[1]);
    g_uids[0] = 1000; g_uids[1] = 10101;
    g_perms[0] = 0; g_perms[1] = 0;
    expect(uids_have_loopback_permissions(1000, 10101), true, "a system uid passes", 1000, 10101, 0, 0);
    expect(uids_have_loopback_permissions(10101, 1000), true, "a system uid passes as the receiver", 10101, 1000, 0, 0);

    /* Every pair of uids with every combination of the five bits on each side. */
    for (uint32_t i = 0; i < n; i++) {
        for (uint32_t j = 0; j < n; j++) {
            if (uids[i] == uids[j]) continue; /* the caller never judges two equal uids */
            for (uint32_t a = 0; a < (1u << nbits); a++) {
                for (uint32_t b = 0; b < (1u << nbits); b++) {
                    uint32_t sp = 0, rp = 0;
                    for (uint32_t k = 0; k < nbits; k++) {
                        if (a & (1u << k)) sp |= bits[k];
                        if (b & (1u << k)) rp |= bits[k];
                    }
                    g_uids[0] = uids[i]; g_uids[1] = uids[j];
                    g_perms[0] = sp; g_perms[1] = rp;
                    expect(uids_have_loopback_permissions(uids[i], uids[j]), ref_allowed(uids[i], uids[j], sp, rp),
                           "uids_have_loopback_permissions", uids[i], uids[j], sp, rp);
                }
            }
        }
    }

    /* uid_may_use_loopback: a system uid, the force pair, or USE_LOOPBACK_INTERFACE. */
    for (uint32_t i = 0; i < n; i++) {
        for (uint32_t a = 0; a < (1u << nbits); a++) {
            uint32_t p = 0;
            for (uint32_t k = 0; k < nbits; k++) if (a & (1u << k)) p |= bits[k];
            g_uids[0] = uids[i]; g_uids[1] = uids[i]; g_perms[0] = p; g_perms[1] = p;
            expect(uid_may_use_loopback(uids[i]), ref_system(uids[i]) || ref_force(p) || ref_use(p),
                   "uid_may_use_loopback", uids[i], 0, p, 0);
        }
    }

    if (checks < 10000) {
        printf("FAIL only %d checks ran: the loops did not run as written\n", checks);
        return 1;
    }
    printf("%d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
