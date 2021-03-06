/*
 * ModeShape (http://www.modeshape.org)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of 
 * individual contributors.
 *
 * ModeShape is free software. Unless otherwise indicated, all code in ModeShape
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * ModeShape is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.modeshape.jcr.value.binary;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.jcr.RepositoryException;
import org.modeshape.common.annotation.ThreadSafe;
import org.modeshape.common.logging.Logger;
import org.modeshape.common.util.CheckArg;
import org.modeshape.common.util.StringUtil;
import org.modeshape.jcr.TextExtractors;
import org.modeshape.jcr.api.mimetype.MimeTypeDetector;
import org.modeshape.jcr.mimetype.ExtensionBasedMimeTypeDetector;
import org.modeshape.jcr.text.TextExtractorContext;
import org.modeshape.jcr.value.BinaryValue;

/**
 * An abstract class for a {@link BinaryStore}, with common functionality needed by implementation classes.
 */
@ThreadSafe
public abstract class AbstractBinaryStore implements BinaryStore {

    private static final long DEFAULT_LATCH_WAIT_IN_SECONDS = 10L;

    private static final long LARGE_SIZE = 1 << 25; // 32MB
    private static final long MEDIUM_FILE_SIZE = 1 << 20; // 1MB
    private static final long SMALL_FILE_SIZE = 1 << 15; // 32K
    private static final long TINY_FILE_SIZE = 1 << 10; // 1K

    private static final int LARGE_BUFFER_SIZE = 1 << 20; // 1MB
    protected static final int MEDIUM_BUFFER_SIZE = 1 << 16; // 64K
    private static final int SMALL_BUFFER_SIZE = 1 << 12; // 4K
    private static final int TINY_BUFFER_SIZE = 1 << 11; // 2K

    public static int bestBufferSize( long fileSize ) {
        assert fileSize >= 0;
        if (fileSize < TINY_FILE_SIZE) return (int)fileSize + 2;
        if (fileSize < SMALL_FILE_SIZE) return TINY_BUFFER_SIZE;
        if (fileSize < MEDIUM_FILE_SIZE) return SMALL_BUFFER_SIZE;
        if (fileSize < LARGE_SIZE) return MEDIUM_BUFFER_SIZE;
        return LARGE_BUFFER_SIZE;
    }

    protected Logger logger = Logger.getLogger(getClass());

    private final AtomicLong minBinarySizeInBytes = new AtomicLong(DEFAULT_MINIMUM_BINARY_SIZE_IN_BYTES);
    private volatile TextExtractors extractors;
    private volatile MimeTypeDetector detector = ExtensionBasedMimeTypeDetector.INSTANCE;

    @Override
    public long getMinimumBinarySizeInBytes() {
        return minBinarySizeInBytes.get();
    }

    @Override
    public void setMinimumBinarySizeInBytes( long minSizeInBytes ) {
        CheckArg.isNonNegative(minSizeInBytes, "minSizeInBytes");
        minBinarySizeInBytes.set(minSizeInBytes);
    }

    @Override
    public void setTextExtractors( TextExtractors textExtractors ) {
        CheckArg.isNotNull(textExtractors, "textExtractors");
        this.extractors = textExtractors;
    }

    @Override
    public void setMimeTypeDetector( MimeTypeDetector mimeTypeDetector ) {
        this.detector = mimeTypeDetector != null ? mimeTypeDetector : ExtensionBasedMimeTypeDetector.INSTANCE;
    }

    @Override
    public final String getText( BinaryValue binary ) throws BinaryStoreException {
        if (!extractors.extractionEnabled()) {
            return null;
        }

        if (binary instanceof InMemoryBinaryValue) {
            // The extracted text will never be stored, so try directly using the text extractors ...
            return extractors.extract((InMemoryBinaryValue)binary, new TextExtractorContext());
        }

        // try and locate an already extracted file from the store (assuming a worker has already finished)
        String extractedText = getExtractedText(binary);
        if (extractedText != null) {
            return extractedText;
        }
        // there isn't any text available, so wait for a job to finish and then return the result
        try {
            CountDownLatch latch = extractors.getWorkerLatch(binary.getKey(), false);
            if (latch == null) {
                // There is no latch, so just compute the text here ...
                extractors.extract(this, binary, new TextExtractorContext());
                // Find the latch again ...
                latch = extractors.getWorkerLatch(binary.getKey(), false);
            }
            // There was a latch, so wait till the work is done ...
            if (latch != null && latch.await(DEFAULT_LATCH_WAIT_IN_SECONDS, TimeUnit.SECONDS)) {
                return getExtractedText(binary);
            }
            // Stopped waiting ...
            return null;
        } catch (InterruptedException e) {
            throw new BinaryStoreException(e);
        }
    }

    @Override
    public String getMimeType( BinaryValue binary,
                               String name ) throws IOException, RepositoryException {
        String storedMimeType = getStoredMimeType(binary);
        if (!StringUtil.isBlank(storedMimeType)) {
            return storedMimeType;
        }
        String detectedMimeType = detector().mimeTypeOf(name, binary);
        storeMimeType(binary, detectedMimeType);
        return detectedMimeType;
    }

    /**
     * Returns the stored mime-type of a binary value.
     * 
     * @param binaryValue a {@code non-null} {@link BinaryValue}
     * @return either a non-empty {@code String} if a stored mimetype exists, or {@code null} if such a value doesn't exist yet.
     * @throws BinaryStoreException if there's a problem accessing the binary store
     */
    protected String getStoredMimeType( BinaryValue binaryValue ) throws BinaryStoreException {
        throw new UnsupportedOperationException("getStoredMimeType not supported by " + getClass().getName());
    }

    /**
     * Stores the given mime-type for a binary value.
     * 
     * @param binaryValue a {@code non-null} {@link BinaryValue}
     * @param mimeType a non-empty {@code String}
     * @throws BinaryStoreException if there's a problem accessing the binary store
     */
    protected void storeMimeType( BinaryValue binaryValue,
                                  String mimeType ) throws BinaryStoreException {
        throw new UnsupportedOperationException("storeMimeType not supported by " + getClass().getName());
    }

    /**
     * Get the text extractor that can be used to extract text by this store.
     * 
     * @return the text extractor; never null
     */
    protected final TextExtractors extractors() {
        return this.extractors;
    }

    /**
     * Get the MIME type detector that can be used to find the MIME type for binary content
     * 
     * @return the detector; never null
     */
    protected final MimeTypeDetector detector() {
        return detector;
    }

    /**
     * Initialize the store and get ready for use.
     */
    public void start() {
    }

    public void shutdown() {
    }
}
