package com.oneroof.shared;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;

/**
 * A file from the original data room, read from the unmodified copy under
 * {@code classpath:original/}. The data room format is fixed: UTF-8, one header row,
 * pipe-separated, no pipes inside values, and {@code NONE} for "not applicable".
 */
public final class OriginalFile {

	private static final String ROOT = "original/";

	private final String relativePath;
	private final byte[] content;

	private OriginalFile(String relativePath, byte[] content) {
		this.relativePath = relativePath;
		this.content = content;
	}

	/** Opens a file by its path inside the data room, for example {@code 01_corevia/master/m_agency_cv.csv}. */
	public static OriginalFile open(String relativePath) {
		try (InputStream in = new ClassPathResource(ROOT + relativePath).getInputStream()) {
			return new OriginalFile(relativePath, in.readAllBytes());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Original file is missing: " + relativePath, ex);
		}
	}

	public String relativePath() {
		return this.relativePath;
	}

	public String sha256() {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(this.content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Parses every data row. Each row knows its line number so stored records can point back to it. */
	public List<Row> rows() {
		String[] lines = new String(this.content, StandardCharsets.UTF_8).split("\\R");
		String[] header = lines[0].split("\\|", -1);
		List<Row> rows = new ArrayList<>(lines.length - 1);
		for (int i = 1; i < lines.length; i++) {
			if (lines[i].isEmpty()) {
				continue;
			}
			String[] cells = lines[i].split("\\|", -1);
			if (cells.length != header.length) {
				throw new IllegalStateException("%s line %d has %d cells, expected %d".formatted(this.relativePath,
						i + 1, cells.length, header.length));
			}
			Map<String, String> values = new LinkedHashMap<>();
			for (int c = 0; c < header.length; c++) {
				values.put(header[c], cells[c]);
			}
			rows.add(new Row(this.relativePath + ":L" + (i + 1), values));
		}
		return rows;
	}

	/**
	 * One data row.
	 *
	 * @param sourceRef the file path and line number, for example {@code 01_corevia/master/m_agency_cv.csv:L2}
	 */
	public record Row(String sourceRef, Map<String, String> values) {

		public String text(String column) {
			String value = this.values.get(column);
			if (value == null) {
				throw new IllegalArgumentException("No column '%s' in %s".formatted(column, this.sourceRef));
			}
			return value;
		}

		/** The value, or {@code null} when the data room marks it as not applicable. */
		public String textOrNull(String column) {
			String value = text(column);
			return "NONE".equals(value) ? null : value;
		}

		public long number(String column) {
			return Long.parseLong(text(column));
		}

		public java.time.LocalDate date(String column) {
			return java.time.LocalDate.parse(text(column));
		}

		public java.time.LocalDate dateOrNull(String column) {
			String value = textOrNull(column);
			return value == null ? null : java.time.LocalDate.parse(value);
		}

		public boolean flag(String column) {
			return Boolean.parseBoolean(text(column));
		}
	}
}
