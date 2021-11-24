package diff.difftree.filter;

import diff.difftree.DiffTree;
import diff.difftree.analysis.Equality;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Filters all duplicates in a list of DiffTrees regarding isomorphism.
 */
public class DuplicateDiffTreeFilter {
    private final BiFunction<DiffTree, DiffTree, Boolean> equality;

    public DuplicateDiffTreeFilter(final BiFunction<DiffTree, DiffTree, Boolean> equalityCondiiton) {
        this.equality = equalityCondiiton;
    }

    public static DuplicateDiffTreeFilter wrtIsomorphism() {
        return new DuplicateDiffTreeFilter(Equality::isomorph);
    }

    public List<DiffTree> filterDuplicates(final List<DiffTree> treesWithDuplicates) {
        final List<DiffTree> distinct = new ArrayList<>(treesWithDuplicates.size());

        for (final DiffTree candidate : treesWithDuplicates) {
            if (distinct.stream().noneMatch(t -> equality.apply(candidate, t))) {
                distinct.add(candidate);
            }
        }

        return distinct;
    }
}
