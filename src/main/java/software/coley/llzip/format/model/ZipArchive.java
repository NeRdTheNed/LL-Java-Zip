package software.coley.llzip.format.model;

import software.coley.llzip.util.OffsetComparator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Zip file outline.
 *
 * @author Matt Coley
 */
public class ZipArchive {
	private final List<ZipPart> parts = new ArrayList<>();

	/**
	 * @return All parts of the zip archive.
	 */
	public List<ZipPart> getParts() {
		return parts;
	}

	/**
	 * @return Local file header entries.
	 */
	public List<LocalFileHeader> getLocalFiles() {
		return parts.stream()
				.filter(part -> part.type() == PartType.LOCAL_FILE_HEADER)
				.map(part -> (LocalFileHeader) part)
				.collect(Collectors.toList());
	}

	/**
	 * @return Central directory header entries.
	 */
	public List<CentralDirectoryFileHeader> getCentralDirectories() {
		return parts.stream()
				.filter(part -> part.type() == PartType.CENTRAL_DIRECTORY_FILE_HEADER)
				.map(part -> (CentralDirectoryFileHeader) part)
				.collect(Collectors.toList());
	}

	/**
	 * @return End of central directory.
	 */
	public EndOfCentralDirectory getEnd() {
		return parts.stream()
				.filter(part -> part.type() == PartType.END_OF_CENTRAL_DIRECTORY)
				.sorted(new OffsetComparator().reversed()) // Yield the LAST entry
				.limit(1)
				.map(part -> (EndOfCentralDirectory) part)
				.findFirst().orElse(null);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ZipArchive that = (ZipArchive) o;

		return parts.equals(that.parts);
	}

	@Override
	public int hashCode() {
		return parts.hashCode();
	}
}
