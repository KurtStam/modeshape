/*
 * JBoss DNA (http://www.jboss.org/dna)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of 
 * individual contributors. 
 *
 * JBoss DNA is free software. Unless otherwise indicated, all code in JBoss DNA
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * JBoss DNA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.dna.graph.property.basic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import net.jcip.annotations.Immutable;
import org.jboss.dna.common.util.CheckArg;
import org.jboss.dna.graph.property.Binary;

/**
 * An implementation of {@link Binary} that keeps the binary data in-memory.
 * 
 * @author Randall Hauch
 */
@Immutable
public class InMemoryBinary extends AbstractBinary {

    /**
     * Version {@value} .
     */
    private static final long serialVersionUID = 2L;

    private final byte[] bytes;
    private byte[] sha1hash;

    public InMemoryBinary( byte[] bytes ) {
        CheckArg.isNotNull(bytes, "bytes");
        this.bytes = bytes;
    }

    /**
     * {@inheritDoc}
     */
    public long getSize() {
        return this.bytes.length;
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.jboss.dna.graph.property.Binary#getHash()
     */
    public byte[] getHash() {
        if (sha1hash == null) {
            // Idempotent, so doesn't matter if we recompute in concurrent threads ...
            sha1hash = computeHash(bytes);
        }
        return sha1hash;
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getBytes() {
        return this.bytes;
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getStream() {
        return new ByteArrayInputStream(this.bytes);
    }

    /**
     * {@inheritDoc}
     */
    public void acquire() {
        // do nothing
    }

    /**
     * {@inheritDoc}
     */
    public void release() {
        // do nothing
    }
}