package com.cognix.rentalcoreapi.shared.util;

import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Builds a {@link Sort} from untrusted {@code sortBy}/{@code sortDir} query
 * params.
 *
 * <p>Passing a client-supplied string straight into {@link Sort#by(String...)}
 * lets any unknown field reach Spring Data, which raises
 * {@code PropertyReferenceException} and surfaces as a 500. Callers declare the
 * fields they actually support and anything else falls back to the default.
 *
 * <p>An unrecognised sort field is <em>ignored</em> rather than rejected: the
 * rows returned are still correct, only their order differs, so failing the
 * whole request would be a harsher response than the mistake warrants and would
 * break clients that send a stale field name. (This is deliberately unlike an
 * unrecognised <em>filter</em>, which must never be ignored — silently
 * returning unfiltered data would be wrong data.)
 */
public final class SortUtils {

    private SortUtils() {
    }

    /**
     * @param sortBy    requested field, may be null or unknown
     * @param sortDir   "asc" (any other value, including null, means descending)
     * @param allowed   fields this endpoint permits sorting on
     * @param fallback  used when {@code sortBy} is null or not in {@code allowed};
     *                  must itself be a real persistent field
     */
    public static Sort resolve(String sortBy, String sortDir, Set<String> allowed, String fallback) {
        String field = (sortBy != null && allowed.contains(sortBy)) ? sortBy : fallback;
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort primary = Sort.by(dir, field);
        // Stable tiebreak: among rows sharing the primary value, the most recently
        // created record comes first. Without it, ties (e.g. two payments on the
        // same date — a CASH row and the ROLLOVER it spawned) come back in
        // nondeterministic order, so the same data renders differently across
        // endpoints and even between calls. Every entity extends BaseEntity, so
        // `createdAt` is always a valid persistent property.
        if ("createdAt".equals(field)) {
            return primary;
        }
        return primary.and(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
