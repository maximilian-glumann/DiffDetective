package org.variantsync.diffdetective.mining.formats;

import org.variantsync.diffdetective.diff.text.DiffLineNumber;
import org.variantsync.diffdetective.util.fide.FixTrueFalse;
import org.variantsync.diffdetective.variation.diff.DiffNode;
import org.variantsync.diffdetective.variation.diff.DiffType;
import org.variantsync.diffdetective.variation.diff.serialize.nodeformat.DiffNodeLabelFormat;
import org.variantsync.diffdetective.variation.DiffLinesLabel;
import org.variantsync.diffdetective.variation.NodeType;
import org.variantsync.functjonal.Pair;

public interface MiningNodeFormat extends DiffNodeLabelFormat<DiffLinesLabel> {
    Pair<DiffType, NodeType> fromEncodedTypes(final String tag);

    @Override
    default DiffNode<DiffLinesLabel> fromLabelAndId(String lineGraphNodeLabel, int nodeId) {
        /// We cannot reuse the id as it is just a sequential integer. It thus, does not contain any information.
        final DiffLineNumber lineFrom = new DiffLineNumber(nodeId, nodeId, nodeId);
        final DiffLineNumber lineTo = new DiffLineNumber(nodeId, nodeId, nodeId);
        final var resultLabel = new DiffLinesLabel();

        final Pair<DiffType, NodeType> types = fromEncodedTypes(lineGraphNodeLabel);
        lineFrom.as(types.first());
        lineTo.as(types.first());
        if (types.second() == NodeType.ARTIFACT) {
            return DiffNode.createArtifact(types.first(),
                    lineFrom, lineTo, resultLabel);
        } else {
            return new DiffNode<>(types.first(), types.second(), lineFrom, lineTo, FixTrueFalse.True, resultLabel);
        }
    }
}
