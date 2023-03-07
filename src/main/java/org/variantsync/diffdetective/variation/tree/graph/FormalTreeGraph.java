package org.variantsync.diffdetective.variation.tree.graph;

import org.variantsync.diffdetective.variation.tree.VariationTree;
import org.variantsync.diffdetective.variation.tree.VariationTreeNode;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


/**
 * A view on a VariationTree that shows the VariationTree as a list of nodes and edges.
 * The view invalidates as soon as the viewed VariationTree is altered as the view
 * will not update itself.
 *
 * @param nodes The set of all nodes in a DiffTree.
 * @param edges The set of all edges in a DiffTree.
 *
 * @author Paul Bittner
 */
public record FormalTreeGraph(
            Set<VariationTreeNode> nodes,
            Set<Edge> edges
) {
    public record Edge(VariationTreeNode child, VariationTreeNode parent) {}

    /**
     * Creates a GraphView for a given VariationTree.
     * The produced view reflects the state of the given VariationTree as is.
     * This means, the view is invalid as soon as the given VariationTree gets
     * modified elsewhere.
     *
     * @param t The VariationTree to view as a list of nodes and edges.
     * @return the graph view
     */
    public static FormalTreeGraph fromTree(final VariationTree t) {
        final Set<VariationTreeNode> nodes = new HashSet<>();
        final Set<Edge> edges = new HashSet<>();

        t.forAllPreorder(n -> {
            nodes.add(n);
            if (n.getParent() != null) {
                edges.add(new Edge(n, n.getParent()));
            }
        });

        return new FormalTreeGraph(nodes, edges);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FormalTreeGraph that = (FormalTreeGraph) o;
        return Objects.equals(nodes, that.nodes) && Objects.equals(edges, that.edges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, edges);
    }
}
