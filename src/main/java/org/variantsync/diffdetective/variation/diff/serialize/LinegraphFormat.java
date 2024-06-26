package org.variantsync.diffdetective.variation.diff.serialize;

/**
 * Root interface for any formats describing content's structure in a linegraph file.
 */
public interface LinegraphFormat {
    /**
     * Name of the format that uniquely identifies the format.
     */
    default String getIdentifier() {
        return this.getClass().getName();
    }

    default String getShortName() {
        return this.getClass().getSimpleName();
    }
}
