import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tinylog.Logger;
import org.variantsync.diffdetective.diff.result.DiffParseException;
import org.variantsync.diffdetective.util.StringUtils;
import org.variantsync.diffdetective.variation.DiffLinesLabel;
import org.variantsync.diffdetective.variation.diff.VariationDiff;
import org.variantsync.diffdetective.variation.diff.bad.BadVDiff;
import org.variantsync.diffdetective.variation.diff.parse.VariationDiffParseOptions;

import java.io.IOException;
import java.nio.file.Path;

public class BadVDiffTest {
    private static final Path resDir = Constants.RESOURCE_DIR.resolve("badvdiff");

    @ParameterizedTest
    @ValueSource(strings = {
            "1",
            "const",
            "diamond",
            "deep_insertion"
    })
    public void toGood_after_fromGood_idempotency(String filename) throws IOException, DiffParseException {
        final Path testfile = resDir.resolve(filename + ".diff");
        Logger.debug("Testing " + testfile);

        final VariationDiff<DiffLinesLabel> initialVDiff = VariationDiff.fromFile(testfile, new VariationDiffParseOptions(false, false));
        Logger.debug("Initial:" + StringUtils.LINEBREAK + initialVDiff);
        initialVDiff.assertConsistency();

        final BadVDiff<DiffLinesLabel> badDiff = BadVDiff.fromGood(initialVDiff);
        Logger.debug("Bad:" + StringUtils.LINEBREAK + badDiff.prettyPrint());

        final VariationDiff<DiffLinesLabel> goodDiff = badDiff.toGood();
        Logger.debug("Good:" + StringUtils.LINEBREAK + goodDiff);
        goodDiff.assertConsistency();

        Assertions.assertTrue(initialVDiff.isSameAs(goodDiff));
    }
}
