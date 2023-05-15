/*
 * Copyright (c) 2007-2018 Siemens AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * 
 */

package com.siemens.ct.exi.core.io.channel;

import java.io.IOException;
import java.io.InputStream;

import com.siemens.ct.exi.core.io.BitInputStream;

/**
 * Simple datatype decoder based on an underlying <code>BitInputStream</code>.
 * Reading a single bit from the underlying stream involves several VM
 * operations. Thus, whenever possible, whole bytes should be read instead.
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Richard.Kuntschke@siemens.com
 * 
 */

public class BitDecoderChannel extends AbstractDecoderChannel implements
		DecoderChannel {
	/**
	 * Underlying bit input stream from which bits and bytes are read.
	 */
	protected BitInputStream istream;
	
	protected final Position pos = new Position();

	/**
	 * Construct a decoder from input stream
	 * 
	 * @param is
	 *            input stream
	 */
	public BitDecoderChannel(InputStream is) {
		this.istream = new BitInputStream(is);
	}

	public final int decode() throws IOException {
		return istream.read();
	}

	public void align() throws IOException {
		if (!istream.isAligned()) {
			LOGGER.trace(marker, "dec {} bits to align reader @ pos [{}]", 
					8 - istream.getBitsUsed(), pos);
			istream.align();
		}
	}

	public int lookAhead() throws IOException {
		return istream.lookAhead();
	}

	public void skip(long n) throws IOException {
		istream.skip(n);
	}

	/**
	 * Decodes and returns an n-bit unsigned integer.
	 */
	public final int decodeNBitUnsignedInteger(int n) throws IOException {
		assert (n >= 0);
		LOGGER.trace(marker, "dec {}-bit unsigned int @ pos [{}]", n, pos);
		return (n == 0 ? 0 : istream.readBits(n));
	}

	/**
	 * Decode a single boolean value. The value false is represented by the bit
	 * 0, and the value true is represented by the bit 1.
	 */
	public boolean decodeBoolean() throws IOException {
		LOGGER.trace(marker, "dec boolean, 1 bit @ pos[{}]", pos);
		return (istream.readBit() == 1);
	}

	/**
	 * Decode a binary value as a length-prefixed sequence of octets.
	 */
	public byte[] decodeBinary() throws IOException {
		LOGGER.trace(marker, "dec binary value length");
		final int length = decodeUnsignedInteger();
		byte[] result = new byte[length];

		LOGGER.trace(marker, "dec binary value content, {} bytes @ pos[{}]", length, pos);
		istream.read(result, 0, length);
		return result;
	}

	/** For implementing getPosition(). */
	private class Position {
		private final StringBuilder sb = new StringBuilder();
		
		public String toString() {
			long bytelen = istream.getBytesRead();
			int nbits = 0;
			sb.setLength(0);
			sb.append(bytelen);
			
			if (!istream.isAligned()) {
				sb.append(':');
				nbits = istream.getBitsUsed();
				sb.append(nbits);
			}
			
			sb.append(';');
			sb.append(bytelen * 8 + nbits);
			
			return sb.toString();
		}
	}
	
	protected Object getPosition() { return pos; }
}
