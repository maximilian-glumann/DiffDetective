package org.variantsync.diffdetective.variation.diff.source;

import java.nio.file.Path;

import org.variantsync.diffdetective.diff.git.CommitDiff; // For Javadoc

/**
 * Describes that a VariationDiff was created from a patch in a {@link CommitDiff}.
 */
public class CommitDiffVariationDiffSource implements VariationDiffSource {
	private final Path fileName;
	private final String commitHash;

	/**
	 * Create a source that refers to changes to the given file in the given commit.
	 * @param fileName Name of the modified file from whose changes the VariationDiff was parsed.
	 * @param commitHash Hash of the commit in which the edit occurred.
	 */
	public CommitDiffVariationDiffSource(final Path fileName, final String commitHash) {
		this.fileName = fileName;
		this.commitHash = commitHash;
	}

	/**
	 * Returns the name of the modified file from whose changes the VariationDiff was parsed.
	 */
	public Path getFileName() {
		return fileName;
	}

	/**
	 * Returns the hash of the commit in which the edit occurred.
	 */
	public String getCommitHash() {
		return commitHash;
	}

    @Override
    public String toString() {
        return fileName + "@" + commitHash;
    }
}
