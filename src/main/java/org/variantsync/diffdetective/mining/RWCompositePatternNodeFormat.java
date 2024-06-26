package org.variantsync.diffdetective.mining;

import org.variantsync.diffdetective.editclass.proposed.ProposedEditClasses;
import org.variantsync.diffdetective.mining.formats.DebugMiningDiffNodeFormat;
import org.variantsync.diffdetective.variation.DiffLinesLabel;
import org.variantsync.diffdetective.variation.diff.DiffNode;

public class RWCompositePatternNodeFormat extends DebugMiningDiffNodeFormat {
    @Override
    public String toLabel(final DiffNode<? extends DiffLinesLabel> node) {
        if (node.isArtifact()) {
            return ProposedEditClasses.Instance.match(node).getName() + "<br>" + node.getLabel();
        } else {
            return node.diffType + "_" + switch (node.getNodeType()) {
                case IF -> "mapping<br> " + node.getLabel();
                case ELSE -> "else";
                case ELIF -> "elif<br>" + node.getLabel();
                default -> node.getNodeType() + "<br>" + node.getLabel();
            };
        }
    }
}
