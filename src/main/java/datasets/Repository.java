package datasets;

import diff.DiffFilter;
import load.GitLoader;
import org.eclipse.jgit.api.Git;
import org.pmw.tinylog.Logger;
import util.IO;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Representation of git repositories used as datasets for DiffDetective.
 * 
 * @author Kevin Jedelhauser, Paul Maximilian Bittner
 */
public class Repository {
	/**
	 * The location from where the input repository is read from.
	 */
	private final LoadingParameter repoLocation;
	
	/**
	 * The local path where the repository can be found or should be cloned to.
	 */
	private final Path localPath;

	/**
	 * The remote url of the repository. May be <code>null</code> if local.
	 */
	private final URI remote;

	/**
	 * The name of the repository. Used for debugging.
	 */
	private final String repositoryName;

	/**
	 * Filter determining which files and commits to consider for diffs.
	 */
	private DiffFilter diffFilter;
	
	/**
	 * For large repositories.
	 * Alternatively, increasing the java heap size also helps.
	 */
	private boolean saveMemory;
	
	/**
	 * Creates a repository.
	 * 
	 * @param repoLocation {@link LoadingParameter} From which location the repository is read from
	 * @param localPath The local path where the repository can be found or should be cloned to.
	 * @param remote The remote url of the repository. May be <code>null</code> if local.
	 * @param repositoryName Name of the cloned repository (<code>null</code> if local)
	 * @param saveMemory Omit some debug data to save RAM.
	 * @param diffFilter Filter determining which files and commits to consider for diffs.
	 */
	public Repository(
			final LoadingParameter repoLocation,
			final Path localPath,
			final URI remote,
			final String repositoryName,
			final boolean saveMemory,
			final DiffFilter diffFilter) {
		this.repoLocation = repoLocation;
		this.localPath = localPath;
		this.remote = remote;
		this.repositoryName = repositoryName;
		this.saveMemory = saveMemory;
		this.diffFilter = diffFilter;
	}

	/**
	 * Creates repository of the given source and with all other settings set to default values.
	 * @see Repository
	 */
	public Repository(
			final LoadingParameter repoLocation,
			final Path localPath,
			final URI remote,
			final String repositoryName) {
		this(repoLocation, localPath, remote, repositoryName,
				false, DiffFilter.ALLOW_ALL);
	}

	/**
	 * Creates a repository from an existing directory.
	 * 
	 * @param dirPath The path to the repo directory relative to <WORKING_DIRECTORY>/repositories
	 * @param repoName A name for the repository (currently not used)
	 * @return A repository from an existing directory
	 */
	public static Repository fromDirectory(Path dirPath, String repoName) {
		return new Repository(
				LoadingParameter.FROM_DIR,
				dirPath,
				null,
				repoName);
	}
	
	/**
	 * Creates a repository from a local zip file.
	 * 
	 * @param filePath The path to the zip file (absolute or relative to <WORKING_DIRECTORY>).
	 * @param repoName A name for the repository (currently not used)
	 * @return A repository from a local zip file
	 */
	public static Repository fromZip(Path filePath, String repoName) {
		return new Repository(
				LoadingParameter.FROM_ZIP,
				filePath,
				null,
				repoName);
	}

	/**
	 * Creates a repository from a remote repository.
	 *
	 * @param localPath Path to clone the repository to.
	 * @param repoUri The address of the remote repository
	 * @param repoName Name of the folder, where the git repository is cloned to
	 * @return A repository from a remote location (e.g. Github repository)
	 */
	public static Repository fromRemote(Path localPath, URI repoUri, String repoName) {
		return new Repository(
				LoadingParameter.FROM_REMOTE,
				localPath,
				repoUri,
				repoName);
	}

	/**
	 * Creates a repository from a remote repository.
	 *
	 * @param localPath Path to clone the repository to.
	 * @param repoUri The address of the remote repository
	 * @param repoName Name of the folder, where the git repository is cloned to
	 * @return A repository from a remote location (e.g. Github repository)
	 */
	public static Optional<Repository> tryFromRemote(Path localPath, String repoUri, String repoName) {
		return IO
				.tryParseURI(repoUri)
				.map(remote -> fromRemote(localPath, remote, repoName));
	}

	public LoadingParameter getRepoLocation() {
		return repoLocation;
	}

	public Path getLocalPath() {
		return localPath;
	}

	public URI getRemoteURI() {
		return remote;
	}

	public String getRepositoryName() {
		return repositoryName;
	}

	public void setSaveMemory(boolean saveMemory) {
		this.saveMemory = saveMemory;
	}

	public void setDiffFilter(final DiffFilter filter) {
		this.diffFilter = filter;
	}

	public DiffFilter getDiffFilter() {
		return diffFilter;
	}

	public boolean shouldSaveMemory() {
		return saveMemory;
	}

	public Git load() {
		Logger.info("Loading git at {} ...", getLocalPath());
		return switch (getRepoLocation()) {
			case FROM_DIR -> GitLoader.fromDirectory(getLocalPath());
			case FROM_ZIP -> GitLoader.fromZip(getLocalPath());
			case FROM_REMOTE -> GitLoader.fromRemote(getLocalPath(), getRemoteURI());
			default -> throw new UnsupportedOperationException("Unknown git repo source");
		};
	}
}