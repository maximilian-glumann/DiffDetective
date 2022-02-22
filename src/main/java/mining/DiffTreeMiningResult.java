package mining;

import de.variantsync.functjonal.Functjonal;
import de.variantsync.functjonal.category.InplaceMonoid;
import de.variantsync.functjonal.category.InplaceSemigroup;
import de.variantsync.functjonal.category.Semigroup;
import de.variantsync.functjonal.map.MergeMap;
import diff.difftree.serialize.DiffTreeSerializeDebugData;
import diff.result.DiffError;
import metadata.AtomicPatternCount;
import metadata.ExplainedFilterSummary;
import metadata.Metadata;
import pattern.atomic.proposed.ProposedAtomicPatterns;
import util.IO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;

public class DiffTreeMiningResult implements Metadata<DiffTreeMiningResult> {
    public final static String EXTENSION = ".metadata.txt";

    public final static String ERROR_BEGIN = "#Error[";
    public final static String ERROR_END = "]";

    public final static String KEY_RUNTIME = "runtime in seconds";
    public final static String KEY_COMMITS = "commits";
    public final static String KEY_TREES = "trees";

    public static Map.Entry<String, BiConsumer<DiffTreeMiningResult, String>> storeAsCustomInfo(String key) {
        return Map.entry(key, (r, val) -> r.putCustomInfo(key, val));
    }
    
    public final static InplaceSemigroup<DiffTreeMiningResult> ISEMIGROUP = (a, b) -> {
        a.exportedCommits += b.exportedCommits;
        a.exportedTrees += b.exportedTrees;
        a.runtimeInSeconds += b.runtimeInSeconds;
        a.debugData.append(b.debugData);
        a.filterHits.append(b.filterHits);
        a.atomicPatternCounts.append(b.atomicPatternCounts);
        MergeMap.putAllValues(a.customInfo, b.customInfo, Semigroup.assertEquals());
        a.diffErrors.append(b.diffErrors);
    };

    public final static InplaceMonoid<DiffTreeMiningResult> IMONOID = InplaceMonoid.From(
            DiffTreeMiningResult::new,
            ISEMIGROUP
    );

    public int exportedCommits;
    public int exportedTrees;
    public double runtimeInSeconds;
    public final DiffTreeSerializeDebugData debugData;
    public ExplainedFilterSummary filterHits;
    public AtomicPatternCount atomicPatternCounts;
    private final LinkedHashMap<String, String> customInfo = new LinkedHashMap<>();
    private final MergeMap<DiffError, Integer> diffErrors = new MergeMap<>(new HashMap<>(), Integer::sum);

    public DiffTreeMiningResult() {
        this(0, 0, 0, new DiffTreeSerializeDebugData(), new ExplainedFilterSummary());
    }

    public DiffTreeMiningResult(
            int exportedCommits,
            int exportedTrees,
            double runtimeInSeconds,
            final DiffTreeSerializeDebugData debugData,
            final ExplainedFilterSummary filterHits)
    {
        this.exportedCommits = exportedCommits;
        this.exportedTrees = exportedTrees;
        this.runtimeInSeconds = runtimeInSeconds;
        this.debugData = debugData;
        this.filterHits = filterHits;
        this.atomicPatternCounts = new AtomicPatternCount();
    }

    public void putCustomInfo(final String key, final String value) {
        customInfo.put(key, value);
    }

    public void reportDiffErrors(final List<DiffError> errors) {
        for (final DiffError e : errors) {
            diffErrors.put(e, 1);
        }
    }
    
    /**
     * Imports a metadata file, which is an output of a {@link DiffTreeMiningResult}, and saves back to {@link DiffTreeMiningResult}.
     * 
     * @param p {@link Path} to the metadata file
     * @return The reconstructed {@link DiffTreeMiningResult}
     * @throws IOException
     */
    public static DiffTreeMiningResult importFrom(final Path p, final Map<String, BiConsumer<DiffTreeMiningResult, String>> customParsers) throws IOException {
        DiffTreeMiningResult result = new DiffTreeMiningResult();
        
        final List<String> filterHitsLines = new ArrayList<>();
        final List<String> atomicPatternCountsLines = new ArrayList<>();
        
        String fileInput = IO.readAsString(p); // read in metadata file
        fileInput = fileInput.replace("\r", ""); // remove carriage returns if present
        final String[] lines = fileInput.split("\n");
        String[] keyValuePair;
        String key;
        String value;
        
        // examine each line of the metadata file separately
        for (final String line : lines) {
            keyValuePair = line.split(": ");
            key = keyValuePair[0];
            value = keyValuePair[1];

            switch (key) {
                case KEY_TREES -> result.exportedTrees = Integer.parseInt(value);
                case KEY_COMMITS -> result.exportedCommits = Integer.parseInt(value);
                case DiffTreeSerializeDebugData.KEY_NON -> result.debugData.numExportedNonNodes = Integer.parseInt(value);
                case DiffTreeSerializeDebugData.KEY_ADD -> result.debugData.numExportedAddNodes = Integer.parseInt(value);
                case DiffTreeSerializeDebugData.KEY_REM -> result.debugData.numExportedRemNodes = Integer.parseInt(value);
                case KEY_RUNTIME -> {
                    if (value.endsWith("s")) {
                        value = value.substring(0, value.length() - 1);
                    }
                    result.runtimeInSeconds = Double.parseDouble(value);
                }
                default -> {
                    final String finalKey = key;
                    if (ProposedAtomicPatterns.All.stream().anyMatch(pattern -> pattern.getName().equals(finalKey))) {
                        atomicPatternCountsLines.add(line);
                    } else if (key.startsWith(ExplainedFilterSummary.FILTERED_MESSAGE_BEGIN)) {
                        filterHitsLines.add(line);
                    } else if (key.startsWith(ERROR_BEGIN)) {
                        DiffError e = new DiffError(key.substring(ERROR_BEGIN.length(), key.length() - ERROR_END.length()));
                        // add DiffError
                        result.diffErrors.put(e, Integer.parseInt(value));
                    } else {
                        final BiConsumer<DiffTreeMiningResult, String> customParser = customParsers.get(key);
                        if (customParser == null) {
                            final String errorMessage = "Unknown entry \"" + line + "\"!";
                            throw new IOException(errorMessage);
                        } else {
                            customParser.accept(result, value);
                        }
                    }
                }
            }
        }
        
        result.filterHits = ExplainedFilterSummary.parse(filterHitsLines);
        result.atomicPatternCounts = AtomicPatternCount.parse(atomicPatternCountsLines, p.toString());

        return result;
    }

    @Override
    public LinkedHashMap<String, Object> snapshot() {
        LinkedHashMap<String, Object> snap = new LinkedHashMap<>();
        snap.put(KEY_TREES, exportedTrees);
        snap.put(KEY_COMMITS, exportedCommits);
        snap.put(KEY_RUNTIME, runtimeInSeconds);
        snap.putAll(debugData.snapshot());
        snap.putAll(filterHits.snapshot());
        snap.putAll(atomicPatternCounts.snapshot());
        snap.putAll(customInfo);
        snap.putAll(Functjonal.bimap(diffErrors, error -> ERROR_BEGIN + error + ERROR_END, Object::toString));
        return snap;
    }

    @Override
    public InplaceSemigroup<DiffTreeMiningResult> semigroup() {
        return ISEMIGROUP;
    }
}
