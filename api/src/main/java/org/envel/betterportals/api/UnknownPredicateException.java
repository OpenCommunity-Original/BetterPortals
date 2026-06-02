package org.envel.betterportals.api;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown if attempting to remove a portal predicate that wasn't added
 */
public class UnknownPredicateException extends IllegalArgumentException {
    /**
     * Creates a new {@link UnknownPredicateException} with an error based on the given predicate's class name.
     * @param predicate Predicate to base error message on
     */
    public UnknownPredicateException(@NotNull PortalPredicate predicate) {
        super("Attempted to remove predicate that wasn't added. Type: " + predicate.getClass().getName());
    }
}
