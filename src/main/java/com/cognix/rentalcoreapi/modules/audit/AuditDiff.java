package com.cognix.rentalcoreapi.modules.audit;

import java.math.BigDecimal;
import java.util.List;

/** Helper for building field-level change lists in audit sentences. */
public final class AuditDiff {

    private AuditDiff() {
    }

    /** Appends {@code field 'old' → 'new'} to {@code changes} when the value changed. */
    public static void diff(List<String> changes, String field, Object oldVal, Object newVal) {
        if (unchanged(oldVal, newVal)) {
            return;
        }
        changes.add("%s '%s' → '%s'".formatted(field, render(oldVal), render(newVal)));
    }

    /**
     * Money compares by value, not by text. A BigDecimal parsed from JSON
     * carries the scale it was written with (150000 → scale 0) while the one
     * loaded from a DECIMAL(12,2) column carries scale 2, so a plain toString
     * comparison reports "amount '100000.00' → '100000'" for a figure nobody
     * touched — and then writes an audit row saying so.
     */
    private static boolean unchanged(Object oldVal, Object newVal) {
        if (oldVal instanceof BigDecimal a && newVal instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        return render(oldVal).equals(render(newVal));
    }

    private static String render(Object value) {
        if (value == null) {
            return "";
        }
        // Normalized so the two sides of a genuine change read consistently
        // ("100000.00" → "150000.00", never "100000.00" → "150000").
        if (value instanceof BigDecimal d) {
            return d.stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }
}
