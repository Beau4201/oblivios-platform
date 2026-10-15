// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

/**
 * How a connection between two companies reads from one company's page. A relation is said of its first
 * company ({@code ownerA owns ownerB}); on the page of the second, the first is the subject, and on the page
 * of the first, the second is the object. The words differ for the two (buys from, sells to), so the page
 * asks for the role of the other company.
 */
public final class RelationRole {
    public static final int NONE = 0;
    /** The other company is the one the verb is said of (it owns, it buys from). */
    public static final int SUBJECT = 1;
    /** The other company is what the verb is said to (it is owned, it sells to). */
    public static final int OBJECT = 2;

    /** The relation types in the order of the two string arrays of the same names; the last is the fallback. */
    private static final String[] TYPES = {"owns", "merged_with", "partner_of", "buys_ad_space_from"};

    private RelationRole() {}

    /** The role of the other company in {@code r} as seen from {@code viewedId}; NONE if it is neither end. */
    public static int of(String viewedId, MapModel.Relation r) {
        if (viewedId == null) {
            return NONE;
        }
        if (viewedId.equals(r.ownerA)) {
            return OBJECT;
        }
        if (viewedId.equals(r.ownerB)) {
            return SUBJECT;
        }
        return NONE;
    }

    /** The other end of the relation, or null if {@code viewedId} is neither end. */
    public static String other(String viewedId, MapModel.Relation r) {
        int role = of(viewedId, r);
        return role == OBJECT ? r.ownerB : role == SUBJECT ? r.ownerA : null;
    }

    /** The index of a relation type into the arrays of wordings; an unknown type is "connected" (4). */
    public static int typeIndex(String type) {
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(type)) {
                return i;
            }
        }
        return TYPES.length;
    }
}
