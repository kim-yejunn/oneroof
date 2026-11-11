package com.oneroof.shared;

import java.util.List;

/**
 * Loads one module's records from the original data room. Each module owns the import of its own
 * tables; the platform only decides when imports run.
 */
public interface OriginalDataImporter {

	/** Imports the module's original records unless they are already present. */
	List<ImportResult> importIfEmpty();

	/**
	 * What one import did.
	 *
	 * @param file the data room path of the file
	 * @param sha256 the checksum of the content that was read
	 * @param rows the number of records stored; zero when the data was already present
	 * @param alreadyPresent whether the import was skipped because the data existed
	 */
	record ImportResult(String file, String sha256, int rows, boolean alreadyPresent) {
	}
}
