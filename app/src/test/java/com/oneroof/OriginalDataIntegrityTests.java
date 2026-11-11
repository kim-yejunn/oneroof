package com.oneroof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import com.oneroof.shared.OriginalFile;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Guards the rule that original files are used exactly as distributed. */
class OriginalDataIntegrityTests {

	private static final Path DISTRIBUTED = Path.of("..", "01_참가자_배포용", "04_원본데이터");

	@Test
	void bundledCopiesMatchTheirRecordedChecksums() throws IOException {
		Map<String, String> manifest = manifest();

		assertThat(manifest).hasSize(4);
		manifest.forEach((path, checksum) -> assertThat(OriginalFile.open(path).sha256()).as(path).isEqualTo(checksum));
	}

	@Test
	void bundledCopiesAreIdenticalToTheDistributedFiles() throws Exception {
		assumeTrue(Files.isDirectory(DISTRIBUTED), "distributed materials are not available in this checkout");

		for (Map.Entry<String, String> entry : manifest().entrySet()) {
			byte[] distributed = Files.readAllBytes(DISTRIBUTED.resolve(entry.getKey()));
			String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(distributed));
			assertThat(checksum).as(entry.getKey()).isEqualTo(entry.getValue());
		}
	}

	private static Map<String, String> manifest() throws IOException {
		String text = new ClassPathResource("original/MANIFEST.sha256").getContentAsString(StandardCharsets.UTF_8);
		Map<String, String> manifest = new LinkedHashMap<>();
		for (String line : text.split("\\R")) {
			if (!line.isBlank()) {
				String[] parts = line.trim().split("\\s+", 2);
				manifest.put(parts[1], parts[0]);
			}
		}
		return manifest;
	}
}
