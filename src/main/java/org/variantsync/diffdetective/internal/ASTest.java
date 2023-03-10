package org.variantsync.diffdetective.internal;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.tinylog.Logger;
import org.variantsync.diffdetective.AnalysisRunner;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.analysis.FilterAnalysis;
import org.variantsync.diffdetective.analysis.PreprocessingAnalysis;
import org.variantsync.diffdetective.datasets.PatchDiffParseOptions;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.diffdetective.diff.git.DiffFilter;
import org.variantsync.diffdetective.diff.git.PatchDiff;
import org.variantsync.diffdetective.variation.diff.DiffTree;
import org.variantsync.diffdetective.variation.diff.filter.DiffTreeFilter;
import org.variantsync.diffdetective.variation.diff.parse.DiffTreeParseOptions;
import org.variantsync.diffdetective.variation.diff.transform.CutNonEditedSubtrees;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ASTest implements Analysis.Hooks {
    public static final TriFunction<Repository, Path, Analysis.Hooks, Analysis> AnalysisFactory = (repo, repoOutputDir, pcAnalysis) -> new Analysis(
            "PCAnalysis",
            List.of(
                    new PreprocessingAnalysis(new CutNonEditedSubtrees()),
                    new FilterAnalysis(DiffTreeFilter.notEmpty()), // filters unwanted trees
                    pcAnalysis
            ),
            repo,
            repoOutputDir
    );

    /**
     * Main method to start the analysis.
     *
     * @param args Command-line options.
     * @throws IOException When copying the log file fails.
     */
    public static void main(String[] args) throws IOException {
        ASTest analysis = new ASTest();
        AnalysisRunner.Options defaultOptions = AnalysisRunner.Options.DEFAULT(args);
        var options = new AnalysisRunner.Options(
                defaultOptions.repositoriesDirectory(),
                Paths.get("results", "astest"),
                defaultOptions.datasetsFile(),
                repo -> {
                    final PatchDiffParseOptions repoDefault = repo.getParseOptions();
                    return new PatchDiffParseOptions(
                            PatchDiffParseOptions.DiffStoragePolicy.DO_NOT_REMEMBER,
                            new DiffTreeParseOptions(
                                    repoDefault.diffTreeParseOptions().annotationParser(),
                                    false,
                                    false
                            )
                    );
                },
                repo -> new DiffFilter.Builder()
                        .allowMerge(false)
                        .allowAllChangeTypes()
                        .allowAllFileExtensions()
                        .build(),
                true,
                false
        );

        FileUtils.deleteDirectory(options.outputDirectory().toFile());

        AnalysisRunner.run(options, (repo, repoOutputDir) ->
                Analysis.forEachCommit(() -> AnalysisFactory.apply(repo, repoOutputDir, analysis))
        );
    }

    @Override
    public boolean beginCommit(Analysis analysis) {
        Logger.info(analysis.getCurrentCommit());
        return true;
    }

    @Override
    public boolean beginPatch(Analysis analysis) {
        final PatchDiff patch = analysis.getCurrentPatch();
        Logger.info("  " + patch.getFileName());
        return true;
    }

    @Override
    public boolean analyzeDiffTree(Analysis analysis) {
        // Get the ground truth for this file
        final DiffTree d = analysis.getCurrentDiffTree();
        Logger.info("    has VDiff");
//        Show.diff(d).showAndAwait();
        return true;
    }
}
