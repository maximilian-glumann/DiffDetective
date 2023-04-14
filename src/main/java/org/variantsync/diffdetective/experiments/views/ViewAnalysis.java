package org.variantsync.diffdetective.experiments.views;

import org.prop4j.Node;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.diff.result.DiffParseException;
import org.variantsync.diffdetective.editclass.proposed.ProposedEditClasses;
import org.variantsync.diffdetective.experiments.views.result.ViewEvaluation;
import org.variantsync.diffdetective.util.*;
import org.variantsync.diffdetective.variation.diff.DiffTree;
import org.variantsync.diffdetective.variation.diff.view.DiffView;
import org.variantsync.diffdetective.variation.tree.view.query.ArtifactQuery;
import org.variantsync.diffdetective.variation.tree.view.query.FeatureQuery;
import org.variantsync.diffdetective.variation.tree.view.query.Query;
import org.variantsync.diffdetective.variation.tree.view.query.VariantQuery;

import java.io.IOException;
import java.util.*;

import static org.variantsync.diffdetective.util.fide.FormulaUtils.negate;

/**
 * Wie wäre es, wenn wir auch ermitteln, wie groß der Anteil der Commits ist, bei denen man durch die Views einen
 * Vorteil hat? Also nicht nur auf die Mittelwerte schauen, sondern ganz bewusst zwischen Commits mit hoher
 * Komplexität, Zahl an Views, etc., und enfachen Commits unterscheiden? Ein ausgedachtes Beispiel: Im Durchschnitt
 * könnte es ja sein, dass die Views erstmal scheinbar wenig bringen,  aber dann eben in beispielsweise 10% der Commit
 * die Views einen sehr großen Unterschied machen.
 */
public class ViewAnalysis implements Analysis.Hooks {
    // Result data
    public static final String EDIT_COMPLEXITIES_EXTENSION = ".views.csv";
    private StringBuilder csv;
    private Random random;

    @Override
    public void initializeResults(Analysis analysis) {
        Analysis.Hooks.super.initializeResults(analysis);

        random = new Random();

        csv = new StringBuilder();
        csv.append(ViewEvaluation.makeHeader(CSV.DEFAULT_CSV_DELIMITER)).append(StringUtils.LINEBREAK);
    }

    private void runQueryExperiment(Analysis analysis, final DiffTree d, final Query q) {
        final long naiveTime, optimizedTime;
        final Clock c = new Clock();

        // measure naive view generation
        try {
            c.start();
            DiffView.naive(d, q);
            naiveTime = c.getPassedMilliseconds();
        } catch (IOException | DiffParseException e) {
            throw new RuntimeException(e);
        }

        // measure optimized view generation
        c.start();
        DiffView.optimized(d, q);
        optimizedTime = c.getPassedMilliseconds();

        // export results
        final ViewEvaluation e = new ViewEvaluation(
                analysis.getCurrentCommitDiff().getAbbreviatedCommitHash(),
                analysis.getCurrentPatch().getFileName(),
                q,
                naiveTime,
                optimizedTime
        );
        csv.append(e.toCSV()).append(StringUtils.LINEBREAK);
    }

    @Override
    public boolean analyzeDiffTree(Analysis analysis) throws Exception {
        final DiffTree d                = analysis.getCurrentDiffTree();
        final Collection<Query> queries = generateRandomQueries(d);

        for (final Query q : queries) {
            runQueryExperiment(analysis, d, q);
        }

        return Analysis.Hooks.super.analyzeDiffTree(analysis);
    }

    private List<Query> generateRandomQueries(final DiffTree d) {
        final List<Node> deselectedPCs = new ArrayList<>();
        final Set<String> features = new HashSet<>();
        final Set<String> artifacts = new HashSet<>();

        d.forAll(a -> {
            if (a.isArtifact()) {
                // Collect all PCs negated
                if (!ProposedEditClasses.Untouched.matches(a)) {
                    a.getDiffType().forAllTimesOfExistence(t -> deselectedPCs.add(negate(a.getPresenceCondition(t))));
                }

                // Collect all artifact names.
                artifacts.addAll(a.getLabelLines());
            }

            // Collect all features
            else if (a.isConditionalAnnotation()) {
                features.addAll(a.getFormula().getUniqueContainedFeatures());
            }
        });

        final List<Query> queries = new ArrayList<>(3);
        if (!deselectedPCs.isEmpty()) {
            queries.add(randomVariantQuery(deselectedPCs));
        }
        if (!features.isEmpty()) {
            queries.add(randomFeatureQuery(features));
        }
        if (!artifacts.isEmpty()) {
            queries.add(randomArtifactQuery(artifacts));
        }

        return queries;
    }

    private Query randomVariantQuery(final List<Node> deselectedPCs) {
        /*
        Do we need this?
        I think for our feasibility study, we can ignore this.
        Without semantic duplicates, we sample uniform randomly over all PCs of edited artifacts.
        Otherwise, we would sample uniform randomly over all _unique_ PCs of edited artifacts.
         */
        // remove semantic duplicates
//        final List<Node> deselectedPCsList = new ArrayList<>(deselectedPCs);
//        removeSemanticDuplicates(deselectedPCsList);

        return new VariantQuery(deselectedPCs.get(random.nextInt(deselectedPCs.size())));
    }

    private Query randomFeatureQuery(final Set<String> features) {
        /*
        Pick a random feature for our query.
        Since we actually just need a single random value, we could also just pick the first element
        but I don't know if there is any hidden sorting within the set that would bias this choice.
         */
        return new FeatureQuery(CollectionUtils.getRandomElement(random, features));
    }

    private Query randomArtifactQuery(final Set<String> artifacts) {
        /*
        Pick a random artifact for our query.
        Since we actually just need a single random value, we could also just pick the first element
        but I don't know if there is any hidden sorting within the set that would bias this choice.
         */
        return new ArtifactQuery(CollectionUtils.getRandomElement(random, artifacts));
    }

    @Override
    public void endBatch(Analysis analysis) throws IOException {
        IO.write(
                FileUtils.addExtension(analysis.getOutputFile(), EDIT_COMPLEXITIES_EXTENSION),
                csv.toString()
        );
    }
}