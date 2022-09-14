package org.variantsync.diffdetective.pattern.elementary.proposed;

import org.prop4j.Node;
import org.variantsync.diffdetective.analysis.logic.SAT;
import org.variantsync.diffdetective.diff.difftree.DiffNode;
import org.variantsync.diffdetective.diff.difftree.DiffType;
import org.variantsync.diffdetective.pattern.elementary.ElementaryPattern;
import org.variantsync.diffdetective.pattern.elementary.ElementaryPatternCatalogue;
import org.variantsync.diffdetective.util.Assert;

import java.util.*;

/**
 * The catalog of elementary edit patterns proposed in our ESEC/FSE'22 paper.
 * @author Paul Bittner
 */
public class ProposedElementaryPatterns implements ElementaryPatternCatalogue {
    public static final ElementaryPattern AddToPC = new AddToPC();
    public static final ElementaryPattern AddWithMapping = new AddWithMapping();
    public static final ElementaryPattern RemFromPC = new RemFromPC();
    public static final ElementaryPattern RemWithMapping = new RemWithMapping();
    public static final ElementaryPattern Specialization = new Specialization();
    public static final ElementaryPattern Generalization = new Generalization();
    public static final ElementaryPattern Reconfiguration = new Reconfiguration();
    public static final ElementaryPattern Refactoring = new Refactoring();
    public static final ElementaryPattern Untouched = new Untouched();

    /**
     * A list of all nine patterns in their order of appearance in the paper.
     */
    public static final List<ElementaryPattern> All = List.of(
            AddToPC, AddWithMapping,
            RemFromPC, RemWithMapping,
            Specialization, Generalization, Reconfiguration, Refactoring, Untouched
    );

    /**
     * A map of all nine edit patterns, indexed by their DiffType.
     */
    public static final Map<DiffType, List<ElementaryPattern>> PatternsByType;

    /**
     * Singleton instance of this catalog.
     */
    public static final ProposedElementaryPatterns Instance = new ProposedElementaryPatterns();

    static {
        PatternsByType = new HashMap<>();
        for (final ElementaryPattern ap : All) {
            PatternsByType.computeIfAbsent(ap.getDiffType(), d -> new ArrayList<>()).add(ap);
        }
    }

    private ProposedElementaryPatterns() {}

    @Override
    public List<ElementaryPattern> all() {
        return All;
    }

    @Override
    public Map<DiffType, List<ElementaryPattern>> byType() {
        return PatternsByType;
    }

    @Override
    public ElementaryPattern match(DiffNode node)
    {
        // This is an inlined version of all patterns to optimize runtime when detecting the pattern of a certain node.

        // Because this compiles, we know that each branch terminates and returns a value.
        // Each returned value is not null but an actual pattern object.
        // Since the given node may be any node, we have proven that every node is classified by at least one pattern.
        if (!node.isCode()) {
            throw new IllegalArgumentException("Expected a code node but got " + node.nodeType + "!");
        }

        if (node.isAdd()) {
            if (node.getAfterParent().isAdd()) {
                return AddWithMapping;
            } else {
                return AddToPC;
            }
        } else if (node.isRem()) {
            if (node.getBeforeParent().isRem()) {
                return RemWithMapping;
            } else {
                return RemFromPC;
            }
        } else {
            Assert.assertTrue(node.isNon());

            final Node pcb = node.getBeforePresenceCondition();
            final Node pca = node.getAfterPresenceCondition();

            final boolean beforeVariantsSubsetOfAfterVariants;
            final boolean afterVariantsSubsetOfBeforeVariants;

            /// We can avoid any SAT calls in case both formulas are syntactically equal.
            if (pcb.equals(pca)) {
                beforeVariantsSubsetOfAfterVariants = true;
                afterVariantsSubsetOfBeforeVariants = true;
            } else {
                beforeVariantsSubsetOfAfterVariants = SAT.implies(pcb, pca);
                afterVariantsSubsetOfBeforeVariants = SAT.implies(pca, pcb);
            }

//            System.out.println("Found NON node " + node.getLabel());
//            System.out.println("TAUT(" + pcb + " => " + pca + ") = " + beforeVariantsSubsetOfAfterVariants);
//            System.out.println("TAUT(" + pca + " => " + pcb + ") = " + afterVariantsSubsetOfBeforeVariants);

            // If the set of variants stayed the same.
            if (beforeVariantsSubsetOfAfterVariants && afterVariantsSubsetOfBeforeVariants) {
                if (node.beforePathEqualsAfterPath()) {
                    return Untouched;
                } else {
                    return Refactoring;
                }
            }
            // If the set of variants grew.
            if (beforeVariantsSubsetOfAfterVariants) { // && !afterVariantsSubsetOfBeforeVariants
                return Generalization;
            }
            // If the set of variants shrank.
            if (afterVariantsSubsetOfBeforeVariants) { // && !beforeVariantsSubsetOfAfterVariants
                return Specialization;
            }

            // If the set of variants changed but there is no subset relation.
            // !beforeVariantsSubsetOfAfterVariants && !afterVariantsSubsetOfBeforeVariants
            return Reconfiguration;
        }
    }

    /**
     * Returns the elementary edit pattern that has the given name.
     * Returns empty of no pattern has the given name.
     */
    public Optional<ElementaryPattern> fromName(String label) {
        for (final ElementaryPattern p : All) {
            if (p.getName().equals(label)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
