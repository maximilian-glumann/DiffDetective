package org.variantsync.diffdetective.diff.difftree;

import org.prop4j.And;
import org.prop4j.Node;
import org.variantsync.diffdetective.diff.DiffLineNumber;
import org.variantsync.diffdetective.diff.Lines;
import org.variantsync.diffdetective.util.Assert;
import org.variantsync.diffdetective.util.StringUtils;
import org.variantsync.diffdetective.util.fide.FixTrueFalse;

import java.util.*;
import java.util.stream.Collectors;

import static org.variantsync.diffdetective.diff.difftree.Time.AFTER;
import static org.variantsync.diffdetective.diff.difftree.Time.BEFORE;
import static org.variantsync.diffdetective.util.fide.FormulaUtils.negate;

/**
 * Implementation of a node in a {@link DiffTree}.
 * A DiffNode represents a single node within a variation tree diff (according to our ESEC/FSE'22 paper), but is specialized
 * to the target domain of preprocessor-based software product lines.
 * Thus, opposed to the generic mathematical model of variation tree diffs, a DiffNode always stores lines of text, line numbers, and child ordering information as its label.
 * Each DiffNode may be edited according to its {@link DiffType} and represents a source code element according to its {@link NodeType}.
 * DiffNode's store parent and child information to build a graph.
 * @author Paul Bittner, Sören Viegener, Benjamin Moosherr
 */
public class DiffNode {
    private static final short ID_OFFSET = 3;

    /**
     * The diff type of this node, which determines if this node represents
     * an inserted, removed, or unchanged element in a diff.
     */
    public final DiffType diffType;

    /**
     * The node type of this node, which determines the type of the represented
     * element in the diff (e.g., mapping or artifact).
     */
    public final NodeType nodeType;

    private DiffLineNumber from = DiffLineNumber.Invalid();
    private DiffLineNumber to = DiffLineNumber.Invalid();

    private Node featureMapping;
    private List<String> lines;

    /**
     * The parents {@link DiffNode} before and after the edit.
     * This array has to be indexed by {@code Time.ordinal()}
     *
     * Invariant: Iff {@code getParent(time) != null} then
     * {@code getParent(time).childOrder.contains(this)}.
     */
    private DiffNode[] parents = new DiffNode[2];

    /**
     * We use a list for children to maintain order.
     *
     * Invariant: Iff {@code childOrder.contains(child)} then
     * {@code child.getParent(BEFORE) == this || child.getParent(AFTER) == this}.
     *
     * Note that it's explicitly allowed to have
     * {@code child.getParent(BEFORE) == this && child.getParent(AFTER) == this}.
     */
    private final List<DiffNode> childOrder;

    /**
     * Creates a DiffNode with the given parameters.
     * @param diffType The type of change made to this node.
     * @param nodeType The type of this node (i.e., mapping or artifact).
     * @param fromLines The starting line number of the corresponding text.
     * @param toLines The ending line number of the corresponding text.
     * @param featureMapping The formula stored in this node. Should be null for artifact nodes.
     * @param label A text label containing information to identify the node (such as the corresponding source code).
     */
    public DiffNode(DiffType diffType, NodeType nodeType,
                    DiffLineNumber fromLines, DiffLineNumber toLines,
                    Node featureMapping, String label) {
        this(diffType, nodeType, fromLines, toLines, featureMapping,
                new ArrayList<String>(Arrays.asList(StringUtils.LINEBREAK_REGEX.split(label, -1))));
    }

    /**
     * The same as {@link DiffNode#DiffNode(DiffType, NodeType, DiffLineNumber, DiffLineNumber, Node, String)}
     * but with the label separated into different lines of text instead of as a single String with newlines.
     */
    public DiffNode(DiffType diffType, NodeType nodeType,
                    DiffLineNumber fromLines, DiffLineNumber toLines,
                    Node featureMapping, List<String> lines) {
        this.childOrder = new ArrayList<>();

        this.diffType = diffType;
        this.nodeType = nodeType;
        this.from = fromLines;
        this.to = toLines;
        this.featureMapping = featureMapping;
        this.lines = lines;
    }

    /**
     * Creates a new root node.
     * The root is a neutral annotation (i.e., its feature mapping is "true").
     */
    public static DiffNode createRoot() {
        return new DiffNode(
                DiffType.NON,
                NodeType.IF,
                DiffLineNumber.Invalid(),
                DiffLineNumber.Invalid(),
                FixTrueFalse.True,
                new ArrayList<>()
        );
    }

    /**
     * Creates an artifact node with the given parameters.
     * For parameter descriptions, see {@link DiffNode#DiffNode(DiffType, NodeType, DiffLineNumber, DiffLineNumber, Node, String)}.
     * The <code>code</code> parameter will be set as the node's label.
     */
    public static DiffNode createArtifact(DiffType diffType, DiffLineNumber fromLines, DiffLineNumber toLines, String code) {
        return new DiffNode(diffType, NodeType.ARTIFACT, fromLines, toLines, null, code);
    }

    /**
     * The same as {@link DiffNode#createArtifact(DiffType, DiffLineNumber, DiffLineNumber, String)} but with the code for the label
     * given as a list of individual lines instead of a single String with linebreaks to identify newlines.
     */
    public static DiffNode createArtifact(DiffType diffType, DiffLineNumber fromLines, DiffLineNumber toLines, List<String> lines) {
        return new DiffNode(diffType, NodeType.ARTIFACT, fromLines, toLines, null, lines);
    }

    /**
     * Adds the given lines to the source code lines of this node.
     * @param lines Lines to add.
     */
    public void addLines(final List<String> lines) {
        this.lines.addAll(lines);
    }

    /**
     * Returns the lines in the diff that are represented by this DiffNode.
     */
    public List<String> getLines() {
        return lines;
    }

    /**
     * Returns the lines in the diff that are represented by this DiffNode as a single text.
     * @see DiffNode#getLines
     */
    public String getLabel() {
        return String.join(StringUtils.LINEBREAK, lines);
    }

    /**
     * Sets the the lines in the diff that are represented by this DiffNode to the given code.
     * Lines are identified by linebreak characters.
     */
    public void setLabel(String label) {
        lines.clear();
        Collections.addAll(lines, StringUtils.LINEBREAK_REGEX.split(label, -1));
    }

    /**
     * Gets the first {@code if} node in the path from the root to this node at the time
     * {@code time}.
     * @return The first {@code if} node in the path to the root at the time {@code time}
     */
    public DiffNode getIfNode(Time time) {
        if (isIf()) {
            return this;
        }
        if (isRoot()) {
            return null;
        }
        return getParent(time).getIfNode(time);
    }

    /**
     * Gets the number of annotations in the path from the root to this node at the time
     * {@code time}.
     * @return the number of annotations above this node at the time {@code time}
     */
    public int getAnnotationDepth(Time time) {
        if (isRoot()) {
            return 0;
        }

        if (isIf()) {
            return getParent(time).getAnnotationDepth(time) + 1;
        }

        return getParent(time).getAnnotationDepth(time);
    }

    /**
     * Gets the length of the path from the root to this node at the time {@code time}.
     * @return the depth of the this node in the diff tree at the time {@code time}
     */
    public int getDepth(Time time) {
        if (isRoot()) {
            return 0;
        }

        return getParent(time).getDepth(time) + 1;
    }

    /**
     * Returns true iff the path's in parent direction following the before parent and after parent
     * are the very same.
     */
    public boolean beforePathEqualsAfterPath() {
        if (getParent(BEFORE) == getParent(AFTER)) {
            if (getParent(BEFORE) == null) {
                // root
                return true;
            }

            return getParent(BEFORE).beforePathEqualsAfterPath();
        }

        return false;
    }

    /**
     * Returns the number of unique child nodes.
     */
    public int getTotalNumberOfChildren() {
        return childOrder.size();
    }

    /**
     * Gets the amount of nodes on the path from the root to this node which only exist at the time
     * {@code time}.
     */
    public int getChangeAmount(Time time) {
        if (isRoot()) {
            return 0;
        }

        var changeType = DiffType.thatExistsOnlyAt(time);

        if (isIf() && diffType.equals(changeType)) {
            return getParent(time).getChangeAmount(time) + 1;
        }

        if ((isElif() || isElse()) && diffType.equals(changeType)) {
            // if this is a removed elif or else we do not want to count the other branches of
            // this annotation
            // we thus go up the tree until we get the next if and continue with the parent of it
            return getParent(time).getIfNode(time).getParent(time).getChangeAmount(time) + 1;
        }

        return getParent(time).getChangeAmount(time);
    }

    /**
     * Sets the parent at {@code time} checking that this node doesn't currently have a parent.
     */
    private void setParent(final DiffNode newParent, Time time) {
        Assert.assertTrue(getParent(time) == null);
        parents[time.ordinal()] = newParent;
    }

    /**
     * Adds thus subtree below the given parents.
     * Inverse of drop.
     * @param newBeforeParent Node that should be this node's before parent. May be null.
     * @param newAfterParent Node that should be this node's after parent. May be null.
     * @return True iff this node could be added as child to at least one of the given non-null parents.
     */
    public boolean addBelow(final DiffNode newBeforeParent, final DiffNode newAfterParent) {
        boolean success = false;
        if (newBeforeParent != null) {
            success |= newBeforeParent.addChild(this, BEFORE);
        }
        if (newAfterParent != null) {
            success |= newAfterParent.addChild(this, AFTER);
        }
        return success;
    }

    /**
     * Removes this subtree from its parents.
     * Inverse of addBelow.
     */
    public void drop() {
        if (getParent(BEFORE) != null) {
            getParent(BEFORE).removeChild(this, BEFORE);
        }
        if (getParent(AFTER) != null) {
            getParent(AFTER).removeChild(this, AFTER);
        }
    }

    /**
     * Remove this as the parent of {@code child}.
     */
    private void dropChild(final DiffNode child, Time time) {
        Assert.assertTrue(child.getParent(time) == this);
        child.parents[time.ordinal()] = null;
    }

    /**
     * Returns the index of the given child in the list of children of thus node.
     * Returns -1 if the given node is not a child of this node.
     */
    public int indexOfChild(final DiffNode child) {
        return childOrder.indexOf(child);
    }

    /**
     * Insert {@code child} as child at the time {@code time} at the position {@code index}.
     */
    public boolean insertChild(final DiffNode child, int index, Time time) {
        if (child.getDiffType().existsAtTime(time)) {
            if (!isChild(child)) {
                childOrder.add(index, child);
            }
            child.setParent(this, time);
            return true;
        }
        return false;
    }

    /**
     * The same as {@link DiffNode#insertChild} but puts the node at the end of the children
     * list instead of inserting it at a specific index.
     */
    public boolean addChild(final DiffNode child, Time time) {
        if (child.getDiffType().existsAtTime(time)) {
            if (child.getParent(time) != null) {
                throw new IllegalArgumentException("Given child " + child + " already has a before parent (" + child.getParent(time) + ")!");
            }

            if (!isChild(child)) {
                childOrder.add(child);
            }
            child.setParent(this, time);
            return true;
        }
        return false;
    }

    /**
     * Adds all given nodes at the time {@code time} as children using {@link DiffNode#addChild}.
     * @param children Nodes to add as children.
     * @param time whether to add {@code children} before or after the edit
     */
    public void addChildren(final Collection<DiffNode> children, Time time) {
        for (final DiffNode child : children) {
            addChild(child, time);
        }
    }

    /**
     * Removes the given node from this node's children before or after the edit.
     * The node might still remain a child after or before the edit.
     * @param child the child to remove
     * @param time whether {@code child} should be removed before or after the edit
     * @return True iff the child was removed, false iff it's not a child at {@code time}.
     */
    public boolean removeChild(final DiffNode child, Time time) {
        if (isChild(child, time)) {
            dropChild(child, time);
            removeFromCache(child);
            return true;
        }
        return false;
    }

    /**
     * Removes all given children for all times.
     * None of the given nodes will be a child, neither before nor after the edit, afterwards.
     * @param childrenToRemove Nodes that should not be children of this node anymore.
     */
    public void removeChildren(final Collection<DiffNode> childrenToRemove) {
        for (final DiffNode childToRemove : childrenToRemove) {
            removeChild(childToRemove, BEFORE);
            removeChild(childToRemove, AFTER);
        }
    }

    /**
     * Removes all children before or after the edit.
     * Afterwards, this node will have no before children.
     * @param time whether to remove all children before or after the edit
     * @return All removed children.
     */
    public List<DiffNode> removeChildren(Time time) {
        final List<DiffNode> orphans = new ArrayList<>();

        // Note that the following method call can't be written using a foreach loop reusing
        // {@code removeBeforeChild} because lists can't be modified during traversal.
        childOrder.removeIf(child -> {
            if (!isChild(child, time)) {
                return false;
            }

            orphans.add(child);
            dropChild(child, time);
            return !isChild(child, time.other());
        });

        return orphans;
    }

    /**
     * If the given node is neither a before nor after child, it will be removed
     * from the internal cache that stores the order of children.
     * This method does nothing the given node is (still) a child.
     * @param child The node to remove from the order cache if it is no child.
     * @see DiffNode#isChild(DiffNode)
     */
    private void removeFromCache(final DiffNode child) {
        if (!isChild(child)) {
            childOrder.remove(child);
        }
    }

    /**
     * Removes all children from the given node and adds them as children to this node at the respective times.
     * The order of children is not stable because first all before children are transferred and then all after children.
     * The given node will have no children afterwards.
     * @param other The node whose children should be stolen.
     */
    public void stealChildrenOf(final DiffNode other) {
        addChildren(other.removeChildren(BEFORE), BEFORE);
        addChildren(other.removeChildren(AFTER), AFTER);
    }

    /**
     * Returns the parent of this node before or after the edit.
     */
    public DiffNode getParent(Time time) {
        return parents[time.ordinal()];
    }

    /**
     * Returns the starting line number of this node's corresponding text block.
     */
    public DiffLineNumber getFromLine() {
        return from;
    }

    public void setFromLine(DiffLineNumber from) {
        this.from = from.as(diffType);
    }

    /**
     * Returns the end line number of this node's corresponding text block.
     * The line number is exclusive (i.e., it points 1 behind the last included line).
     */
    public DiffLineNumber getToLine() {
        return to;
    }

    public void setToLine(DiffLineNumber to) {
        this.to = to.as(diffType);
    }

    /**
     * Returns the range of line numbers of this node's corresponding source code in the text-based diff.
     * @see DiffLineNumber#rangeInDiff
     */
    public Lines getLinesInDiff() {
        return DiffLineNumber.rangeInDiff(from, to);
    }

    /**
     * Returns the range of line numbers of this node's corresponding source code before or after
     * the edit.
     */
    public Lines getLinesAtTime(Time time) {
        return DiffLineNumber.rangeAtTime(from, to, time);
    }

    /**
     * Returns the formula that is stored in this node.
     * The formula is null for artifact nodes (i.e., {@link NodeType#ARTIFACT}).
     * The formula is not null for mapping nodes
     * @see NodeType#isAnnotation
     */
    public Node getDirectFeatureMapping() {
        return featureMapping;
    }

    /**
     * Returns the list representing the order of the children.
     * Any child occurs exactly once, even if this node is it's before and after parent.
     */
    public List<DiffNode> getChildOrder() {
        return Collections.unmodifiableList(childOrder);
    }

    /**
     * Legacy alias for {@link DiffNode#getChildOrder()}.
     */
    public List<DiffNode> getAllChildren() {
        return getChildOrder();
    }

    /**
     * Returns the full feature mapping formula of this node.
     * The feature mapping of an {@link NodeType#IF} node is its {@link DiffNode#getDirectFeatureMapping direct feature mapping}.
     * The feature mapping of {@link NodeType#ELSE} and {@link NodeType#ELIF} nodes is determined by all formulas in the respective if-elif-else chain.
     * The feature mapping of an {@link NodeType#ARTIFACT artifact} node is the feature mapping of its parent.
     * See Equation (1) in our paper (+ its extension to time for variation tree diffs described in Section 3.1).
     * @param time Whether to return the feature mapping clauses before or after the edit.
     * @return The feature mapping of this node for the given parent edges.
     *         The returned list represents a conjunction (i.e., all clauses should be combined with boolean AND).
     */
    private List<Node> getFeatureMappingClauses(Time time) {
        final DiffNode parent = getParent(time);

        if (isElse() || isElif()) {
            List<Node> and = new ArrayList<>();

            if (isElif()) {
                and.add(getDirectFeatureMapping());
            }

            // Negate all previous cases
            DiffNode ancestor = parent;
            while (!ancestor.isIf()) {
                if (ancestor.isElif()) {
                    and.add(negate(ancestor.getDirectFeatureMapping()));
                } else {
                    throw new RuntimeException("Expected If or Elif above Else or Elif but got " + ancestor.nodeType + " from " + ancestor);
                    // Assert.assertTrue(ancestor.isArtifact());
                }
                ancestor = ancestor.getParent(time);
            }
            and.add(negate(ancestor.getDirectFeatureMapping()));

            return and;
        } else if (isArtifact()) {
            return parent.getFeatureMappingClauses(time);
        }

        return List.of(getDirectFeatureMapping());
    }

    /**
     * Depending on the given time, returns either the feature mapping before or after the edit.
     */
    public Node getFeatureMapping(Time time) {
        final List<Node> fmClauses = getFeatureMappingClauses(time);
        if (fmClauses.size() == 1) {
            return fmClauses.get(0);
        }
        return new And(fmClauses);
    }

    /**
     * Returns the presence condition of this node for the respective time.
     * See Equation (2) in our paper (+ its extension to time for variation tree diffs described in Section 3.1).
     * @param time Whether to return the presence condition clauses before or after the edit.
     * @return The presence condition of this node for the given parent edges.
     *         The returned list represents a conjunction (i.e., all clauses should be combined with boolean AND).
     */
    private List<Node> getPresenceConditionClauses(Time time) {
        final DiffNode parent = getParent(time);

        if (isElse() || isElif()) {
            final List<Node> clauses = new ArrayList<>(getFeatureMappingClauses(time));

            // Find corresponding if
            DiffNode correspondingIf = parent;
            while (!correspondingIf.isIf()) {
                correspondingIf = correspondingIf.getParent(time);
            }

            // If this elif-else-chain was again nested in another annotation, add its pc.
            final DiffNode outerNesting = correspondingIf.getParent(time);
            if (outerNesting != null) {
                clauses.addAll(outerNesting.getPresenceConditionClauses(time));
            }

            return clauses;
        } else if (isArtifact()) {
            return parent.getPresenceConditionClauses(time);
        }

        // this is mapping or root
        final List<Node> clauses;
        if (parent == null) {
            clauses = new ArrayList<>(1);
        } else {
            clauses = parent.getPresenceConditionClauses(time);
        }
        clauses.add(featureMapping);
        return clauses;
    }

    /**
     * Returns the presence condition of this node before or after the edit.
     * See Equation (2) in our paper (+ its extension to time for variation tree diffs described in Section 3.1).
     * @param time Whether to return the presence condition before or after the edit.
     * @return The presence condition of this node for the given parent edges.
     */
    public Node getPresenceCondition(Time time) {
        if (diffType.existsAtTime(time)) {
            return new And(getPresenceConditionClauses(time));
        } else {
            throw new WrongTimeException("Cannot determine after PC of removed node " + this);
        }
    }

    /**
     * Returns true iff this node is the before or after parent of the given node.
     */
    public boolean isChild(DiffNode child) {
        return isChild(child, BEFORE) || isChild(child, AFTER);
    }

    /**
     * Returns true iff this node is the parent of the given node at the given time.
     */
    public boolean isChild(DiffNode child, Time time) {
        return child.getParent(time) == this;
    }

    /**
     * Returns true iff this node has no children.
     */
    public boolean isLeaf() {
        return childOrder.isEmpty();
    }

    /**
     * Returns true iff this node represents a removed element.
     * @see DiffType#REM
     */
    public boolean isRem() {
        return this.diffType.equals(DiffType.REM);
    }

    /**
     * Returns true iff this node represents an unchanged element.
     * @see DiffType#NON
     */
    public boolean isNon() {
        return this.diffType.equals(DiffType.NON);
    }

    /**
     * Returns true iff this node represents an inserted element.
     * @see DiffType#ADD
     */
    public boolean isAdd() {
        return this.diffType.equals(DiffType.ADD);
    }

    /**
      * Returns the diff type of this node.
     */
    public DiffType getDiffType() {
        return this.diffType;
    }

    /**
     * Returns true if this node represents an ELIF annotation.
     * @see NodeType#ELIF
     */
    public boolean isElif() {
        return this.nodeType.equals(NodeType.ELIF);
    }

    /**
     * Returns true if this node represents a conditional annotation.
     * @see NodeType#IF
     */
    public boolean isIf() {
        return this.nodeType.equals(NodeType.IF);
    }

    /**
     * Returns true if this node is an artifact node.
     * @see NodeType#ARTIFACT
     */
    public boolean isArtifact() {
        return this.nodeType.equals(NodeType.ARTIFACT);
    }

    /**
     * Returns true if this node represents an ELSE annotation.
     * @see NodeType#ELSE
     */
    public boolean isElse() {
        return this.nodeType.equals(NodeType.ELSE);
    }

    /**
     * Returns true if this node is a root node (has no parents).
     */
    public boolean isRoot() {
        return getParent(BEFORE) == null && getParent(AFTER) == null;
    }

    /**
     * Returns {@link NodeType#isAnnotation()} for this node's {@link DiffNode#nodeType}.
     */
    public boolean isAnnotation() {
        return this.nodeType.isAnnotation();
    }

    /**
     * @return An integer that uniquely identifiers this DiffNode within its patch.
     *
     * From the returned id a new node with all essential attributes reconstructed can be obtained
     * by using {@link DiffNode#fromID}.
     *
     * Note that only {@code 26} bits of the line number are encoded, so if the line number is bigger than
     * {@code 2^26}, this id will no longer be unique.
     */
    public int getID() {
        // Add one to ensure invalid (negative) line numbers don't cause issues.
        int lineNumber = 1 + from.inDiff();
        Assert.assertTrue((lineNumber << 2*ID_OFFSET) >> 2*ID_OFFSET == lineNumber);

        int id;
        id = lineNumber;
        id <<= ID_OFFSET;
        id += diffType.ordinal();
        id <<= ID_OFFSET;
        id += nodeType.ordinal();
        return id;
    }

    /**
     * Reconstructs a node from the given id and sets the given label.
     * An id uniquely determines a node's {@link DiffNode#nodeType}, {@link DiffNode#diffType}, and {@link DiffLineNumber#inDiff line number in the diff}.
     * The almost-inverse function is {@link DiffNode#getID()} but the conversion is not lossless.
     * @param id The id from which to reconstruct the node.
     * @param label The label the node should have.
     * @return The reconstructed DiffNode.
     */
    public static DiffNode fromID(final int id, String label) {
        final int lowestBitsMask = (1 << ID_OFFSET) - 1;

        final int nodeTypeOrdinal = id & lowestBitsMask;
        final int diffTypeOrdinal = (id >> ID_OFFSET) & lowestBitsMask;
        final int fromInDiff      = (id >> (2*ID_OFFSET)) - 1;

        var nodeType = NodeType.values()[nodeTypeOrdinal];
        return new DiffNode(
                DiffType.values()[diffTypeOrdinal],
                nodeType,
                new DiffLineNumber(fromInDiff, DiffLineNumber.InvalidLineNumber, DiffLineNumber.InvalidLineNumber),
                DiffLineNumber.Invalid(),
                nodeType.isConditionalAnnotation() ? FixTrueFalse.True : null,
                label
        );
    }

    /**
     * Checks that the DiffTree is in a valid state.
     * In particular, this method checks that all edges are well-formed (e.g., edges can be inconsistent because edges are double-linked).
     * This method also checks that a node with exactly one parent was edited, and that a node with exactly two parents was not edited.
     * @see Assert#assertTrue
     * @throws AssertionError when an inconsistency is detected.
     */
    public void assertConsistency() {
        // check consistency of children lists and edges
        for (final DiffNode c : childOrder) {
            Assert.assertTrue(isChild(c), () -> "Child " + c + " of " + this + " is neither a before nor an after child!");
            if (c.getParent(BEFORE) != null) {
                Assert.assertTrue(c.getParent(BEFORE).isChild(c, BEFORE), () -> "The parent before the edit of " + c + " doesn't contain that node as child");
            }
            if (c.getParent(AFTER) != null) {
                Assert.assertTrue(c.getParent(AFTER).isChild(c, AFTER), () -> "The parent after the edit of " + c + " doesn't contain that node as child");
            }
        }

        // a node with exactly one parent was edited
        if (getParent(BEFORE) == null && getParent(AFTER) != null) {
            Assert.assertTrue(isAdd());
        }
        if (getParent(BEFORE) != null && getParent(AFTER) == null) {
            Assert.assertTrue(isRem());
        }
        // a node with exactly two parents was not edited
        if (getParent(BEFORE) != null && getParent(AFTER) != null) {
            Assert.assertTrue(isNon());
        }

        // Else and Elif nodes have an If or Elif as parent.
        if (this.isElse() || this.isElif()) {
            if (getParent(BEFORE) != null) {
                Assert.assertTrue(getParent(BEFORE).isIf() || getParent(BEFORE).isElif(), "Before parent " + getParent(BEFORE) + " of " + this + " is neither IF nor ELIF!");
            }
            if (getParent(AFTER) != null) {
                Assert.assertTrue(getParent(AFTER).isIf() || getParent(AFTER).isElif(), "After parent " + getParent(AFTER) + " of " + this + " is neither IF nor ELIF!");
            }
        }

        // Only if and elif nodes have a formula
        if (this.isIf() || this.isElif()) {
            Assert.assertTrue(this.getDirectFeatureMapping() != null, "If or elif without feature mapping!");
        } else {
            Assert.assertTrue(this.getDirectFeatureMapping() == null, "Node with type " + nodeType + " has a non null feature mapping");
        }
    }

    /**
     * Prepends the {@link DiffType#symbol} of the given diffType to all given lines and
     * joins all lines with {@link StringUtils#LINEBREAK linebreaks} to a single text.
     * @param diffType The change type of the given diff hunk.
     * @param lines The lines to turn into a text-based diff.
     * @return A diff in which all given lines have the given diff type.
     */
    public static String toTextDiffLine(final DiffType diffType, final List<String> lines) {
        return lines.stream().collect(Collectors.joining(StringUtils.LINEBREAK + diffType.symbol, diffType.symbol, ""));
    }

    /**
     * Unparses this node's lines into its original text-based diff.
     * @return The diff from which this node was parsed, reconstructed as accurately as possible.
     */
    public String toTextDiffLine() {
        return toTextDiffLine(diffType, lines);
    }

    /**
     * Unparses this subgraph into its original text-based diff.
     * This will return the diff of the entire subgraph starting with this node as root.
     * Recursively invokes {@link DiffNode#toTextDiffLine()} on this node and all its descendants.
     * @return The diff from which this subgraph was parsed, reconstructed as accurately as possible.
     */
    public String toTextDiff() {
        final StringBuilder diff = new StringBuilder();

        if (!this.isRoot()) {
            diff
                    .append(this.toTextDiffLine())
                    .append(StringUtils.LINEBREAK);
        }

        for (final DiffNode child : childOrder) {
            diff.append(child.toTextDiff());
        }

        // Add endif after macro
        if (isAnnotation() && !isRoot()) {
            diff
                    .append(toTextDiffLine(this.diffType, List.of("#endif")))
                    .append(StringUtils.LINEBREAK);
        }

        return diff.toString();
    }

    @Override
    public String toString() {
        String s;
        if (isArtifact()) {
            s = String.format("%s_%s from %d to %d", diffType, nodeType, from.inDiff(), to.inDiff());
        } else if (isRoot()) {
            s = "ROOT";
        } else {
            s = String.format("%s_%s from %d to %d with \"%s\"", diffType, nodeType,
                    from.inDiff(), to.inDiff(), featureMapping);
        }
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiffNode diffNode = (DiffNode) o;
        return diffType == diffNode.diffType && nodeType == diffNode.nodeType && from.equals(diffNode.from) && to.equals(diffNode.to) && Objects.equals(featureMapping, diffNode.featureMapping) && lines.equals(diffNode.lines);
    }

    /**
     * Compute a hash using all available attributes.
     *
     * This implementation doesn't strictly adhere to the contract required by {@code Object},
     * because some attributes (for example the line numbers) can be changed during the lifetime of
     * a {@code DiffNode}. So when using something like a {@code HashSet} the user of {@code
     * DiffNode} has to be careful not to change any attributes of a stored {@code DiffNode}.
     */
    @Override
    public int hashCode() {
        return Objects.hash(diffType, nodeType, from, to, featureMapping, lines);
    }
}
