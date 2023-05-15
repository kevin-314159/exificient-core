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
import java.io.OutputStream;

import com.siemens.ct.exi.core.io.BitOutputStream;

/**
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Richard.Kuntschke@siemens.com
 * 
 */

public class BitEncoderChannel extends AbstractEncoderChannel implements
		EncoderChannel {

	/**
	 * Underlying bit output stream to which bits and bytes are written.
	 */
	protected BitOutputStream ostream;
	
	/**
	 * Merely supports getPosition().
	 */
	private Position position;

	/**
	 * For use and reuse for logging.
	 */
	private StringBuilder sb = new StringBuilder();
	
	/**
	 * Construct an encoder from output stream.
	 * 
	 * @param ostream
	 *            output stream
	 */
	public BitEncoderChannel(OutputStream ostream) {
		this.ostream = new BitOutputStream(ostream);
		this.position = new Position();
	}

	public OutputStream getOutputStream() {
		return ostream;
	}

	public int getLength() {
		return ostream.getLength();
	}

	/**
	 * Flush underlying bit output stream.
	 */
	public void flush() throws IOException {
		ostream.flush();
	}

	public void align() throws IOException {
		if (LOGGER.isTraceEnabled()) {
			if (!ostream.isByteAligned() ) {
				sb.setLength(0);
				int padding = 8 - ostream.getBitsInBuffer() % 8;
				appendBits(sb, 0, padding);
				LOGGER.trace(marker, 
						"enc alignment; pad with {} bits @ pos [{}] : {}", 
						ostream.getBitsInBuffer(),
						padding, position, sb.toString()
						);
			}
		}
		ostream.align();
	}

	public void encode(int b) throws IOException {
		if (LOGGER.isTraceEnabled()) {
			sb.setLength(0);
			appendBits(sb, b, 8);
			LOGGER.trace(marker, "enc 1 byte @ pos [{}] : {}",
					position, sb.toString());
		}
		ostream.writeBits(b, 8);
	}

	public void encode(byte b[], int off, int len) throws IOException {
		// TODO write whole bytes (if possible)
		if (LOGGER.isTraceEnabled()) {
			// log up to 8 bytes of data
			sb.setLength(0);
			int i;
			for (i = off; i < off + len && i < 8; i++) {
				appendBits(sb, b[i], 8);
			}
			if (i < off + len) { sb.append(" ..."); }
			LOGGER.trace(marker, "enc {} bytes @ pos [{}] : {}", 
					off + len, position, sb.toString());
		}
		
		for (int i = off; i < (off + len); i++) {
			ostream.writeBits(b[i], 8);
		}
	}

	/**
	 * Encode n-bit unsigned integer. The n least significant bits of parameter
	 * b starting with the most significant, i.e. from left to right.
	 */
	public void encodeNBitUnsignedInteger(int b, int n) throws IOException {
		if (LOGGER.isTraceEnabled()) {
			if (n == 0) LOGGER.trace(marker, 
					"enc 0-bit unsigned int @ pos [{}]", 
					position);
			else {
				sb.setLength(0);
				appendBits(sb, b, n);
				LOGGER.trace(marker, "enc {}-bit unsigned int @ pos [{}] : {}", 
					n, position, sb.toString());
			}
		}
		
		if (b < 0 || n < 0) {
			throw new IllegalArgumentException(
					"Encode negative value as unsigned integer is invalid!");
		}
		assert (b >= 0);
		assert (n >= 0);

		ostream.writeBits(b, n);
	}

	/**
	 * Encode a single boolean value. A false value is encoded as bit 0 and true
	 * value is encode as bit 1.
	 */
	public void encodeBoolean(boolean b) throws IOException {
		if (LOGGER.isTraceEnabled()) {
			sb.setLength(0);
			appendBits(sb, b ? 1 : 0, 1);
			LOGGER.trace(marker, "enc boolean in 1 bit @ pos [{}] : {}",
					position, sb.toString());
		}
		
		if (b) {
			ostream.writeBit1();
		} else {
			ostream.writeBit0();
		}

		// ostream.writeBit(b ? 1 : 0);
	}
	
	/**
	 * Class whose toString() function provides the encoder position of
	 * the enclosing class instance.
	 *
	 */
	private class Position {
		private final StringBuilder sb = new StringBuilder();
		public String toString() {
			sb.setLength(0);
			int bytelen = ostream.getLength();
			int nbits = 0;
			sb.append(bytelen);
			if (!ostream.isByteAligned()) {
				sb.append(':');
				nbits = ostream.getBitsInBuffer();
				sb.append(nbits);
			}
			sb.append(';');
			sb.append(bytelen *8 + nbits);
			return sb.toString();
		}
	}
	
	@Override
	protected Object getPosition() { return position; }
	
	/**
	 * Append to sb a string representing the lowest n bits of b, using
	 * vertical bar (|) to mark each byte boundary before a bit, as if the
	 * bits were being written to ostream at the current position.
	 * If sb is empty, and ostream is not aligned, this will first append
	 * dots to represent previously written bits in the current byte.
	 * 
	 * @param sb
	 * @param b
	 * @param n
	 */
	private void appendBits(StringBuilder sb, int b, int n) {
		
		int mask = 1 << (n - 1);
		int bitsInBuffer = ostream.getBitsInBuffer();
				
		if (sb.length() == 0) {
			for(int i = 0; i < bitsInBuffer; i++) {
				if (i == 4) sb.append(' ');
				sb.append('.');
			}
		}
		
		for(int i = 0; i < n; i++) {
			if (bitsInBuffer == 0) {
				sb.append("|");
			} else if (bitsInBuffer == 4) {
				sb.append(' ');
			}
			
			bitsInBuffer++;
			if (bitsInBuffer == 8) bitsInBuffer = 0;
			
			if ((b & mask) == 0) sb.append('0');
			else sb.append('1');
			
			mask = mask >>> 1;
		}
	}	
}
