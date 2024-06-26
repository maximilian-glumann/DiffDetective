package org.variantsync.diffdetective.variation.diff.serialize.nodeformat;

import org.variantsync.diffdetective.variation.Label;
import org.variantsync.diffdetective.variation.diff.DiffNode;

/**
 * Print NodeType and DiffType and Mappings of Annotations.
 * The produced label will be <code>DiffType_NodeType_"annotation formula"</code> for mapping nodes,
 * and <code>DiffType_NodeType_""</code> for non-mapping nodes.
 * @see DiffNodeLabelPrettyfier#prettyPrintIfAnnotationOr(DiffNode, String)
 * @author Paul Bittner, Kevin Jedelhauser
 */
public class MappingsDiffNodeFormat<L extends Label> implements DiffNodeLabelFormat<L> {
	@Override
	public String toLabel(final DiffNode<? extends L> node) {
		return node.diffType + "_" + node.getNodeType() + "_\"" + DiffNodeLabelPrettyfier.prettyPrintIfAnnotationOr(node, "") + "\"";
	}
}
