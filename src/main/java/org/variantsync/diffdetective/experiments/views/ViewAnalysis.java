package org.variantsync.diffdetective.experiments.views;

import org.prop4j.And;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.analysis.logic.SAT;
import org.variantsync.diffdetective.editclass.proposed.ProposedEditClasses;
import org.variantsync.diffdetective.util.Assert;
import org.variantsync.diffdetective.util.fide.FixTrueFalse;
import org.variantsync.diffdetective.variation.diff.DiffTree;
import org.variantsync.diffdetective.variation.diff.Time;
import org.variantsync.functjonal.Cast;

import java.util.*;

import static org.variantsync.diffdetective.util.fide.FormulaUtils.negate;

public class ViewAnalysis implements Analysis.Hooks {
    public static void sortRegularCNF(final Node rcnf) {
        Assert.assertTrue(rcnf instanceof And);

        // sort literals in clauses by string compare
        Node[] cs = rcnf.getChildren();
        for (final Node c : cs) {
            Arrays.sort(c.getChildren(), (e1, e2) -> {
                final Literal l1 = Cast.unchecked(e1);
                final Literal l2 = Cast.unchecked(e2);
                return ((String) l1.var).compareTo((String) l2.var);
            });
        }

        // sort clauses by literal count
        // clauses with equal literal count will be sorted by their literals as string (might be useless)
        Arrays.sort(cs, Comparator
                .comparingInt((Node e) -> e.getChildren().length)
                .thenComparing(e -> Arrays.toString(e.getChildren())));
    }

    public static int numberOfLiteralsInRegularCNF(final Node rcnf) {
        Assert.assertTrue(rcnf instanceof And);
        return Arrays.stream(rcnf.getChildren())
                .mapToInt(cs -> cs.getChildren().length)
                .sum();
    }

    /**
     * Build a set of partial configurations such that
     * - every config denotes a view of the given diff
     * - every view is uniqe
     * - every possible view is included
     * This works by deselecting any subset of presence conditions of the artifacts in the given diff.
     * @param d
     * @param simplify Whether to simplify formulas in between the algorithm.
     * @return
     */
    public static List<Node> getUniquePartialConfigs(DiffTree d, boolean simplify) {
        final Set<Node> deselectedPCs = new LinkedHashSet<>();

        // Collect all PCs negated
        d.forAll(a -> {
            if (a.isArtifact() && !ProposedEditClasses.Untouched.matches(a)) { // remove second clause for variation trees
                Time.forAll(t -> {
                    Node deselectedPC = FixTrueFalse.EliminateTrueAndFalseInplace(
                            a.getPresenceCondition(t)
                    );

                    deselectedPC = FixTrueFalse.EliminateTrueAndFalseInplace(deselectedPC); // must
                    deselectedPC = negate(deselectedPC); // must
                    deselectedPC = deselectedPC.toRegularCNF(simplify); // optimization
                    sortRegularCNF(deselectedPC); // optimization

                    deselectedPCs.add(deselectedPC);
                });
            }
        });

        // Algorithm is restricted in number of different PCs it can handle.
        Assert.assertTrue(deselectedPCs.size() < Integer.SIZE);
        final List<Node> deselectedPCsList = new ArrayList<>(deselectedPCs);

        // Optimization Heuristic: Sort list of PCs
        deselectedPCsList.sort(Comparator
                .comparingInt((Node e) -> e.getChildren().length)
                .thenComparing(ViewAnalysis::numberOfLiteralsInRegularCNF)
        );
        Logger.info(deselectedPCsList);

        // powerset
        final int powsetSize = 1 << deselectedPCsList.size();

        final List<Node> partialConfigs = new ArrayList<>(powsetSize); // :-1: by Sebastian
        for (int pcsToDeselectBitVector = 0; pcsToDeselectBitVector < powsetSize; ++pcsToDeselectBitVector) {
            int pcsRemainingToDeselect = Integer.bitCount(pcsToDeselectBitVector);
            final List<Node> subset = new ArrayList<>(pcsRemainingToDeselect);

            // As long as there are more pcs to deselect, deselect the one at the current bit.
            for (int i = 0; pcsRemainingToDeselect > 0; ++i) {
                if (((1L << i) & pcsToDeselectBitVector) > 0) {
                    subset.add(deselectedPCsList.get(i));
                    --pcsRemainingToDeselect;
                }
            }

            final Node viewFormula = new And(subset).toCNF(simplify);
            if (SAT.isSatisfiable(viewFormula)) {
                partialConfigs.add(viewFormula);
            } else {
                // OPTIMIZATION
                // skip some of the unsatisfiable next cases that just add further clauses to our already
                // conflicting clause set
                pcsToDeselectBitVector += 1 << Integer.lowestOneBit(pcsToDeselectBitVector);
            }
        }

        // remove semantic duplicates
        int len = partialConfigs.size();
        for (int i = 0; i < len; ++i) {
            final Node ci = partialConfigs.get(i);

            for (int j = i + 1; j < len; ++j) {
                final Node cj = partialConfigs.get(j);
                if (SAT.equivalent(cj, ci)) {
                    Collections.swap(partialConfigs, i, len - 1);
                    --i;
                    --len;
                    break;
                }
            }
        }
        partialConfigs.subList(len, partialConfigs.size()).clear();

        return partialConfigs;//*/
    }

    @Override
    public void initializeResults(Analysis analysis) {
        Analysis.Hooks.super.initializeResults(analysis);
    }

    @Override
    public boolean analyzeDiffTree(Analysis analysis) throws Exception {
        return Analysis.Hooks.super.analyzeDiffTree(analysis);
    }
}
