// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

public final class RelationRoleCheck {
    private static void eq(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        MapModel.Relation r = new MapModel.Relation();
        r.ownerA = "alphabet";
        r.type = "owns";
        r.ownerB = "google";
        eq(RelationRole.OBJECT, RelationRole.of("alphabet", r), "on the first company's page the other is the object");
        eq(RelationRole.SUBJECT, RelationRole.of("google", r), "on the second company's page the other is the subject");
        eq(RelationRole.NONE, RelationRole.of("meta", r), "a company that is neither end");
        eq(RelationRole.NONE, RelationRole.of(null, r), "no company");
        eq("google", RelationRole.other("alphabet", r), "the other end from the first");
        eq("alphabet", RelationRole.other("google", r), "the other end from the second");
        eq(null, RelationRole.other("meta", r), "no other end for a stranger");
        eq(0, RelationRole.typeIndex("owns"), "owns");
        eq(1, RelationRole.typeIndex("merged_with"), "merged_with");
        eq(2, RelationRole.typeIndex("partner_of"), "partner_of");
        eq(3, RelationRole.typeIndex("buys_ad_space_from"), "buys_ad_space_from");
        eq(4, RelationRole.typeIndex("connected"), "connected is the fallback");
        eq(4, RelationRole.typeIndex("something_new"), "an unknown type is the fallback");
        eq(4, RelationRole.typeIndex(null), "no type is the fallback");
        System.out.println("PASS RelationRoleCheck");
    }
}
