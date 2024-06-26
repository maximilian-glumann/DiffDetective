import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.prop4j.*;
import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.logic.UniqueViewsAlgorithm;
import org.variantsync.diffdetective.diff.result.DiffParseException;
import org.variantsync.diffdetective.show.Show;
import org.variantsync.diffdetective.show.engine.GameEngine;
import org.variantsync.diffdetective.util.StringUtils;
import org.variantsync.diffdetective.variation.DiffLinesLabel;
import org.variantsync.diffdetective.variation.diff.Time;
import org.variantsync.diffdetective.variation.diff.VariationDiff;
import org.variantsync.diffdetective.variation.diff.bad.BadVDiff;
import org.variantsync.diffdetective.variation.diff.parse.VariationDiffParseOptions;
import org.variantsync.diffdetective.variation.diff.transform.CutNonEditedSubtrees;
import org.variantsync.diffdetective.variation.diff.view.DiffView;
import org.variantsync.diffdetective.variation.diff.view.ViewSource;
import org.variantsync.diffdetective.variation.tree.VariationTree;
import org.variantsync.diffdetective.variation.tree.view.TreeView;
import org.variantsync.diffdetective.variation.tree.view.relevance.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.variantsync.diffdetective.util.fide.FormulaUtils.*;

@Disabled
public class ViewTest {
    private static final Path resDir = Constants.RESOURCE_DIR.resolve("badvdiff");

    private static void showViews(
            VariationDiff<?> initialVDiff,
            Relevance query
    ) {
        // treeify
        final BadVDiff<?> badDiff = BadVDiff.fromGood(initialVDiff);

        // create view
        final BadVDiff<?> view = badDiff.deepCopy();
        TreeView.treeInline(view.diff(), query);

        // unify
        final VariationDiff<?> goodDiff = view.toGood();
        goodDiff.assertConsistency();

        GameEngine.showAndAwaitAll(
                Show.diff(initialVDiff, "initial edit e"),
                Show.baddiff(badDiff, "tree(e)"),
                Show.baddiff(view, "view(tree(e), " + query + ")"),
                Show.diff(goodDiff, "unify(view(tree(e), " + query + "))")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "emacsbug1"
    })
    void debugTest(String filename) throws IOException, DiffParseException {
        final String filenameWithEnding = filename + ".diff";
        final Path testfile = resDir.resolve(filenameWithEnding);
        Logger.debug("Testing " + testfile);
//        is(  /* Check both of the above conditions, for symbols.  */)
        final VariationDiff<DiffLinesLabel> D = VariationDiff.fromFile(testfile, VariationDiffParseOptions.Default);
        D.assertConsistency();

        final Relevance debugQuery = new Search("  /* Check both of the above conditions, for symbols.  */");
        final var imp = DiffView.computeWhenNodesAreRelevant(D, debugQuery);
        Show.diff(DiffView.optimized(D, debugQuery, imp)).showAndAwait();
        Show.diff(DiffView.naive(D, debugQuery, imp)).showAndAwait();
    }


    @ParameterizedTest
    @ValueSource(strings = {
            "1",
            "diamond",
            "deep_insertion"
    })
    void test(String filename) throws IOException, DiffParseException {
        final String filenameWithEnding = filename + ".diff";
        final Path testfile = resDir.resolve(filenameWithEnding);
        Logger.debug("Testing " + testfile);

        // Load diff
        final VariationDiff<DiffLinesLabel> initialVDiff = VariationDiff.fromFile(testfile, VariationDiffParseOptions.Default);
        initialVDiff.assertConsistency();

        List<Relevance> queries = List.of(
                new Trace("B"),
                new Configure(negate(var("B"))),
                new Search("foo")
        );

        for (Relevance q : queries) {
            final var viewNodes = DiffView.computeWhenNodesAreRelevant(initialVDiff, q);

            GameEngine.showAndAwaitAll(
                    Show.diff(initialVDiff, "D = " + filenameWithEnding),
                    Show.diff(DiffView.naive(initialVDiff, q, viewNodes), "diff_naive(D, " + q + ")"),
                    Show.diff(DiffView.optimized(initialVDiff, q, viewNodes), "diff_smart(D, " + q + ")")
            );
        }

//        showViews(initialVDiff, new VariantQuery(new And(new Literal("X"))));
//        showViews(initialVDiff, new VariantQuery(new And(new Literal("Y"))));
//        showViews(initialVDiff, new VariantQuery(new And(negate(new Literal("X")))));
//        showViews(initialVDiff, new VariantQuery(new And(negate(new Literal("Y")))));
//        showViews(initialVDiff, new FeatureQuery("X"));
//        showViews(initialVDiff, new FeatureQuery("Y"));
//        showViews(initialVDiff, new ArtifactQuery("y"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "runningexampleInDomain",
    })
    void inspectRunningExample(String filename) throws IOException, DiffParseException {
        final Path testfile = resDir.resolve(filename + ".diff");

//        final Literal X = var("Mutable");
        final Literal featureRing = var("Ring");
        final Literal featureDoubleLink = var("DoubleLink");

        final VariationDiff<DiffLinesLabel> d = VariationDiff.fromFile(testfile, VariationDiffParseOptions.Default);
        final VariationTree<DiffLinesLabel> b = d.project(Time.BEFORE);
        final VariationTree<DiffLinesLabel> a = d.project(Time.AFTER);

        // Queries of Listing 3 and 4
        final Relevance bobsQuery1 = new Configure(and(negate(featureDoubleLink)));
        final Relevance charlottesQuery = new Configure(negate(featureRing));

        // Figure 1
        GameEngine.showAndAwaitAll(
                Show.tree(b, "Figure 1: project(D, b)")
        );

        // Figure 2
        GameEngine.showAndAwaitAll(
                Show.diff(d, "Figure 2: D")
        );

        // Figure 3
        final Configure configureExample1 = new Configure(
                and(featureRing, /* FM = */ negate(new And(featureDoubleLink, featureRing)))
        );
        GameEngine.showAndAwaitAll(
                Show.tree(TreeView.tree(b, configureExample1), "Figure 3: view_{tree}(Figure 1, " + configureExample1 + ")")
        );

        // Figure 4
        final Trace traceYesExample1 = new Trace(
                featureDoubleLink.toString()
        );
        GameEngine.showAndAwaitAll(
                Show.tree(TreeView.tree(b, traceYesExample1), "Figure 4: view_{tree}(Figure 1, " + traceYesExample1 + ")")
        );

        // Figure 5
        GameEngine.showAndAwaitAll(
                Show.diff(DiffView.optimized(d, charlottesQuery), "Figure 5: view_{naive}(Figure 2, " + charlottesQuery + ")")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1",
            "2"
//            "runningexample",
////            "const",
////            "diamond",
////            "deep_insertion"
    })
    void testAllConfigs(String filename) throws IOException, DiffParseException {
        final Path testfile = resDir.resolve(filename + ".diff");
        final VariationDiff<DiffLinesLabel> d = VariationDiff.fromFile(testfile, VariationDiffParseOptions.Default);

        final List<Node> configs = UniqueViewsAlgorithm.getUniquePartialConfigs(d, false);
        final List<VariationDiff<DiffLinesLabel>> views = new ArrayList<>();

        final StringBuilder str = new StringBuilder();
        for (int i = 0; i < configs.size(); ++i) {
            final Node config = configs.get(i);

            final Relevance q = new Configure(config);
            final VariationDiff<DiffLinesLabel> view = DiffView.optimized(d, q);
            views.add(view);

            str
                    .append(" ")
                    .append(org.apache.commons.lang3.StringUtils.leftPad(
                            Integer.toString(i),
                            4)
                    )
                    .append(".) ")
                    .append(org.apache.commons.lang3.StringUtils.rightPad(
                            config.toString(NodeWriter.logicalSymbols),
                            40)
                    )
                    .append(" --- ")
                    .append(
                            view.computeArtifactNodes().stream()
                                    .map(a -> a.getLabel().getLines().stream()
                                            .map(String::trim)
                                            .collect(Collectors.joining(StringUtils.LINEBREAK))
                                    )
                                    .collect(Collectors.toList()))
                    .append(StringUtils.LINEBREAK);
        }

        Logger.info("All unique partial configs:" + StringUtils.LINEBREAK + str);
        Show.diff(d, "D").show();
//        {
//            final Query q = new FeatureQuery("C");
//            Show.diff(DiffView.optimized(d, q), "view(D, " + q.getName() + ")").showAndAwait();
//        }

        for (int i = 0; i < configs.size(); ++i) {
            final VariationDiff<DiffLinesLabel> view = views.get(i);
            final Relevance q = ((ViewSource<?>) view.getSource()).relevance();
            Show.diff(view, i + ".) view(D, " + q + ")").showAndAwait();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1",
            "2",
            "runningexample",
            "const",
            "diamond",
            "deep_insertion"
    })
    void cutTest(String filename) throws IOException, DiffParseException {
        final Path testfile = resDir.resolve(filename + ".diff");
        final VariationDiff<DiffLinesLabel> d = VariationDiff.fromFile(testfile, VariationDiffParseOptions.Default);

        Show.diff(d, "original").showAndAwait();
        CutNonEditedSubtrees.genericTransform(true, d);
        Show.diff(d, "cut").showAndAwait();
    }
}
