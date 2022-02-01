package mining.postprocessing;

import de.variantsync.functjonal.Product;
import diff.difftree.DiffTree;
import diff.difftree.render.DiffTreeRenderer;
import diff.difftree.serialize.*;
import diff.difftree.serialize.edgeformat.DefaultEdgeLabelFormat;
import diff.difftree.serialize.treeformat.IndexedTreeFormat;
import mining.DiffTreeMiner;
import mining.DiffTreeMiningResult;
import mining.formats.DebugMiningDiffNodeFormat;
import util.FileUtils;
import util.IO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Performs a postprocessing on mined frequent subgraphs in edits to find semantic edit patterns.
 */
public class MiningPostprocessing {
    private static final DiffTreeRenderer DefaultRenderer = DiffTreeRenderer.WithinDiffDetective();
    private static final boolean RENDER_CANDIDATES = true;
    private static final DiffTreeLineGraphImportOptions IMPORT_OPTIONS = new DiffTreeLineGraphImportOptions(
            GraphFormat.DIFFGRAPH,
            new IndexedTreeFormat(),
            DiffTreeMiner.NodeFormat(),
            DiffTreeMiner.EdgeFormat()
            );
    private static final DiffTreeLineGraphExportOptions EXPORT_OPTIONS = new DiffTreeLineGraphExportOptions(
            GraphFormat.DIFFTREE,
            IMPORT_OPTIONS.treeFormat(),
            new DebugMiningDiffNodeFormat(),
            new DefaultEdgeLabelFormat()
    );
    private static final DiffTreeRenderer.RenderOptions DefaultRenderOptions = new DiffTreeRenderer.RenderOptions(
            EXPORT_OPTIONS.graphFormat(),
            EXPORT_OPTIONS.treeFormat(),
            EXPORT_OPTIONS.nodeFormat(),
            EXPORT_OPTIONS.edgeFormat(),
            false,
            DiffTreeRenderer.RenderOptions.DEFAULT.dpi(),
            DiffTreeRenderer.RenderOptions.DEFAULT.nodesize(),
            DiffTreeRenderer.RenderOptions.DEFAULT.edgesize(),
            DiffTreeRenderer.RenderOptions.DEFAULT.arrowsize(),
            DiffTreeRenderer.RenderOptions.DEFAULT.fontsize(),
            true,
            List.of("--format", "patternsdebug")
    );

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected path to directory of mined patterns as first argument and path to output directory as second argument but got only " + args.length + " arguments!");
        }

        final Path inputPath = Path.of(args[0]);
        final Path outputPath = Path.of(args[1]);
        if (!Files.isDirectory(inputPath) && !FileUtils.hasExtension(inputPath, ".lg")) {
            throw new IllegalArgumentException("Expected path to directory of mined patterns as first argument but got a path that is not a directory, namely \"" + inputPath + "\"!");
        }
        if (Files.exists(outputPath) && !FileUtils.tryIsEmptyDirectory(outputPath)) {
            throw new IllegalArgumentException("Expected path to an empty output directory as second argument but got a path that is not a directory or not empty, namely \"" + outputPath + "\"!");
        }

        postprocessAndInterpretResults(
                parseFrequentSubgraphsIn(inputPath),
                Postprocessor.Default(),
                System.out::println,
                DefaultRenderer,
                DefaultRenderOptions,
                outputPath
        );
    }

    /**
     * Parses all linegraph files in the given directory or file as patterns (i.e., as DiffGraphs).
     * non-recursive
     * @param path A path to a linegraph file or a directory containing linegraph files.
     * @return The list of all diffgraphs parsed from linegraph files in the given directory.
     * @throws IOException If the directory could not be accessed ({@link Files::list}).
     */
    public static List<DiffTree> parseFrequentSubgraphsIn(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Files.list(path)
                    .filter(FileUtils::isLineGraph)
                    .flatMap(file -> LineGraphImport.fromFile(file, IMPORT_OPTIONS).stream())
                    .collect(Collectors.toList());
        } else {
            return LineGraphImport.fromFile(path, IMPORT_OPTIONS);
        }
    }

    public static void postprocessAndInterpretResults(
            final List<DiffTree> frequentSubgraphs,
            final Postprocessor postprocessor,
            final Consumer<String> printer,
            final DiffTreeRenderer renderer,
            DiffTreeRenderer.RenderOptions renderOptions,
            final Path outputDir)
    {
        final Postprocessor.Result result = postprocessor.postprocess(frequentSubgraphs);
        final List<DiffTree> semanticPatterns = result.processedTrees();

        printer.accept("Of " + frequentSubgraphs.size() + " mined subgraphs "
                + semanticPatterns.size() + " are candidates for semantic patterns.");
        printer.accept("Subgraphs were discarded for the following reasons:");
        for (Map.Entry<String, Integer> nameAndCount : result.filterCounts().entrySet()) {
            printer.accept("    " + nameAndCount.getKey() + ": " + nameAndCount.getValue());
        }
        printer.accept("");

        if (RENDER_CANDIDATES && renderer != null) {
            if (renderOptions == null) {
                renderOptions = DiffTreeRenderer.RenderOptions.DEFAULT;
            }

            printer.accept("Exporting and rendering semantic patterns to " + outputDir);
            int patternNo = 0;
            for (final DiffTree semanticPattern : semanticPatterns) {
                renderer.render(semanticPattern, "SemanticPatternCandidate_" + patternNo, outputDir, renderOptions);
                ++patternNo;
            }
        } else {
            final Product<DiffTreeMiningResult, String> lineGraph = LineGraphExport.toLineGraphFormat(semanticPatterns, EXPORT_OPTIONS);
            IO.tryWrite(outputDir.resolve("candidates.lg"), lineGraph.second());
        }
    }
}
