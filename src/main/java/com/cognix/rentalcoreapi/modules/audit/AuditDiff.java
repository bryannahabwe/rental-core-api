package com.cognix.rentalcoreapi.modules.audit;

import java.util.List;

/** Helper for building field-level change lists in audit sentences. */
public final class AuditDiff {

    private AuditDiff() {
    }

    /** Appends {@code field 'old' → 'new'} to {@code changes} when the value changed. */
    public static void diff(List<String> changes, String field, Object oldVal, Object newVal) {
        String o = oldVal == null ? "" : oldVal.toString();
        String n = newVal == null ? "" : newVal.toString();
        if (!o.equals(n)) {
            changes.add("%s '%s' → '%s'".formatted(field, o, n));
        }
    }
}
