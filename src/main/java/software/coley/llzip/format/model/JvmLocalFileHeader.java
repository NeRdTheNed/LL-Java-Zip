package software.coley.llzip.format.model;

import software.coley.llzip.format.compression.ZipCompressions;
import software.coley.llzip.util.ByteData;
import software.coley.llzip.util.ByteDataUtil;
import software.coley.llzip.util.lazy.LazyLong;

import javax.annotation.Nonnull;
import java.util.NavigableSet;


/**
 * An extension of {@link LocalFileHeader} with adjustments to the file-data parse logic to support
 * how the Hotspot JVM handles parsing jar files.
 *
 * @author Matt Coley
 * @author Wolfie / win32kbase <i>(Reverse engineering JVM specific zip handling)</i>
 */
public class JvmLocalFileHeader extends LocalFileHeader {
	private NavigableSet<Long> offsets;
	private long dataOffsetStart;
	private long dataOffsetEnd;
	private boolean foundData;

	/**
	 * @param offsets
	 * 		Set containing all local file header offsets.
	 */
	public void setOffsets(@Nonnull NavigableSet<Long> offsets) {
		this.offsets = offsets;
	}

	@Override
	public void read(@Nonnull ByteData data, long offset) {
		super.read(data, offset);

		// JVM file data reading does NOT use the compressed/uncompressed fields.
		// Instead, it scans data until the next header.
		long dataOffsetStart = offset + MIN_FIXED_SIZE + getFileNameLength() + getExtraFieldLength();
		Long dataOffsetEnd = offsets.ceiling(dataOffsetStart);
		this.dataOffsetStart = dataOffsetStart;
		this.dataOffsetEnd = dataOffsetEnd == null ? -1 : dataOffsetEnd;
		if (dataOffsetEnd != null) {
			// Valid data range found, map back to (localOffset, range)
			fileData = ByteDataUtil.readLazyLongSlice(data, offset,
					new LazyLong(() -> dataOffsetStart - offset),
					new LazyLong(() -> dataOffsetEnd - dataOffsetStart));
			foundData = true;
		}
	}

	@Override
	public void link(CentralDirectoryFileHeader directoryFileHeader) {
		super.link(directoryFileHeader);

		// JVM trusts central directory file header contents over local
		//  - Using fields as this maintains the lazy model
		compressionMethod = directoryFileHeader.compressionMethod;
		compressedSize = directoryFileHeader.compressedSize;
		uncompressedSize = directoryFileHeader.uncompressedSize;
		fileName = directoryFileHeader.fileName;
		generalPurposeBitFlag = directoryFileHeader.generalPurposeBitFlag;
		crc32 = directoryFileHeader.crc32;
		lastModFileDate = directoryFileHeader.lastModFileDate;
		lastModFileTime = directoryFileHeader.lastModFileTime;

		// Update file data with new compressed/uncompressed size if it was not able to be found previously
		// with only the local data available.
		if (!foundData) {
			// Extract updated length from CEN values
			long fileDataLength;
			if (getCompressionMethod() == ZipCompressions.STORED) {
				fileDataLength = getUncompressedSize() & 0xFFFFFFFFL;
			} else {
				fileDataLength = getCompressedSize() & 0xFFFFFFFFL;
			}

			// Only allow lengths that are within a sensible range.
			// Data should not be overflowing into adjacent header entries.
			// - If it is, the data here is likely intentionally tampered with to screw with parsers
			if (fileDataLength + offset < dataOffsetEnd) {
				fileData = ByteDataUtil.readLazyLongSlice(data, offset,
						new LazyLong(() -> dataOffsetStart - offset),
						new LazyLong(() -> fileDataLength));
				data = null;
			}
		}
	}
}
