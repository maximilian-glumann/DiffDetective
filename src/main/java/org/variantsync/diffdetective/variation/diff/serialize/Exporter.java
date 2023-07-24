package org.variantsync.diffdetective.variation.diff.serialize;

import java.io.IOException;
import java.io.OutputStream;

import org.variantsync.diffdetective.variation.Label;
import org.variantsync.diffdetective.variation.diff.DiffTree;

/**
 * Common interface for serialisation of a single {@code DiffTree}.
 * Not all formats have to provide a way to deserialize a {@link DiffTree} from this format.
 *
 * @author Benjamin Moosherr
 */
public interface Exporter<L extends Label> {
    /**
     * Export a {@code diffTree} into {@code destination}.
     *
     * This method should have no side effects besides writing to {@code destination}. Above all,
     * {@code diffTree} shouldn't be modified. Furthermore, {@code destination} shouldn't be
     * closed to allow the embedding of the exported format into a surrounding file.
     *
     * It can be assumed, that {@code destination} is sufficiently buffered.
     *
     * @param diffTree to be exported
     * @param destination where the result should be written
     */
    <La extends L> void exportDiffTree(DiffTree<La> diffTree, OutputStream destination) throws IOException;
}
