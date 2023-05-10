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
import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import com.siemens.ct.exi.core.types.DateTimeType;
import com.siemens.ct.exi.core.values.BooleanValue;
import com.siemens.ct.exi.core.values.DateTimeValue;
import com.siemens.ct.exi.core.values.DecimalValue;
import com.siemens.ct.exi.core.values.FloatValue;
import com.siemens.ct.exi.core.values.IntegerValue;

/**
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Richard.Kuntschke@siemens.com
 * 
 */

public abstract class AbstractDecoderChannel implements DecoderChannel {

	/* buffer for reading arbitrary large integer values */
	private final int[] maskedOctets = new int[MAX_OCTETS_FOR_LONG];
	/* long == 64 bits, 9 * 7bits = 63 bits */
	private final static int MAX_OCTETS_FOR_LONG = 9;

	/* Helper for building strings */
	protected StringBuilder sbHelper;

	/**
	 * Logger for use by this class and subclasses.
	 */
	protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractDecoderChannel.class);
			
	/**
	 * Marker to use when logging.
	 * When a log-generating function A calls a log-generating function B,
	 * A may add a {@link Marker} to marker before the call to B, and remove it after the
	 * call to B, so that B's events that occur in the context of A can be
	 * filtered out by the marker.  This is used, for example, to mark events
	 * associated with decoding the individual characters of a string.
	 */
	protected Marker marker = MarkerFactory.getMarker("");
	
	/**
	 *  Marker for individual character events.
	 */
	protected static final Marker CHAR_MARKER = 
			MarkerFactory.getMarker("CHARACTER_LEVEL");	
	
	public AbstractDecoderChannel() {
	}

	public BooleanValue decodeBooleanValue() throws IOException {
		// return new BooleanValue(decodeBoolean());
		return decodeBoolean() ? BooleanValue.BOOLEAN_VALUE_TRUE
				: BooleanValue.BOOLEAN_VALUE_FALSE;
	}

	/**
	 * Decode a string as a length-prefixed sequence of UCS codepoints, each of
	 * which is encoded as an integer. Look for codepoints of more than 16 bits
	 * that are represented as UTF-16 surrogate pairs in Java.
	 */
	public char[] decodeString() throws IOException {
		LOGGER.trace(marker, "dec string length");
		return decodeStringOnly(decodeUnsignedInteger());
	}

	/**
	 * Decode the characters of a string whose length (#code-points) has already
	 * been read. Look for codepoints of more than 16 bits that are represented
	 * as UTF-16 surrogate pairs in Java.
	 * 
	 * @param length
	 *            Length of the character sequence to read.
	 * @return The character sequence as a string.
	 */
	public char[] decodeStringOnly(int length) throws IOException {

		final char[] ca = new char[length];
		
		LOGGER.trace(marker, "dec string content, {} bytes @ pos [{}]", length, getPosition());
		
		marker.add(CHAR_MARKER);

		for (int i = 0; i < length; i++) {
			final int codePoint = decodeUnsignedInteger();
			if (Character.isSupplementaryCodePoint(codePoint)) {
				// supplementary code-point
				// Assumption: it doesn't happen very often
				return decodeStringOnlySupplementaryCodePoints(ca, length, i,
						codePoint);
			} else {
				ca[i] = (char) codePoint;
			}
		}
		
		marker.remove(CHAR_MARKER);

		return ca;
	}

	private char[] decodeStringOnlySupplementaryCodePoints(char[] ca,
			int length, int i, int codePoint) throws IOException {
		assert (Character.isSupplementaryCodePoint(codePoint));
		if (sbHelper == null) {
			sbHelper = new StringBuilder(length + 10);
		} else {
			sbHelper.setLength(0);
		}

		sbHelper.append(ca, 0, i); // append chars so far
		sbHelper.appendCodePoint(codePoint); // append current code-point
		for (int k = i + 1; k < length; k++) {
			sbHelper.appendCodePoint(decodeUnsignedInteger());
		}

		int len = sbHelper.length();
		char dst[] = new char[len];
		sbHelper.getChars(0, len, dst, 0);

		return dst;

		// return sb.toString().toCharArray(); // return char array
	}

	/**
	 * Decode an arbitrary precision non negative integer using a sequence of
	 * octets. The most significant bit of the last octet is set to zero to
	 * indicate sequence termination. Only seven bits per octet are used to
	 * store the integer's value.
	 */
	public final int decodeUnsignedInteger() throws IOException {
		// 0XXXXXXX ... 1XXXXXXX 1XXXXXXX
		
		LOGGER.trace(marker, "dec unsigned integer @ pos [{}]", getPosition());
		
		int byteCnt = 1;
		int result = decode();

		// < 128: just one byte, optimal case
		// ELSE: multiple bytes...

		if (result >= 128) {
			result = (result & 127);
			int mShift = 7;
			int b;

			do {
				// 1. Read the next octet
				byteCnt += 1;
				b = decode();
				// 2. Multiply the value of the unsigned number represented by
				// the 7 least significant
				// bits of the octet by the current multiplier and add the
				// result to the current value.
				result += (b & 127) << mShift;
				// 3. Multiply the multiplier by 128
				mShift += 7;
				// 4. If the most significant bit of the octet was 1, go back to
				// step 1
			} while (b >= 128);
		}

		LOGGER.trace(marker, "decoded {} bytes", byteCnt);
		return result;
	}

	protected long decodeUnsignedLong() throws IOException {
		long lResult = 0L;
		int mShift = 0;
		int b;
		int byteCnt = 0;

		LOGGER.trace(marker, "decode unsigned long @ pos [{}]", getPosition());
		
		do {
			b = decode();
			byteCnt++;
			lResult += ((long) (b & 127)) << mShift;
			mShift += 7;
		} while ((b >>> 7) == 1);

		LOGGER.trace(marker, "decoded {} bytes", byteCnt);
		
		return lResult;
	}

	/**
	 * Decode an arbitrary precision integer using a sign bit followed by a
	 * sequence of octets. The most significant bit of the last octet is set to
	 * zero to indicate sequence termination. Only seven bits per octet are used
	 * to store the integer's value.
	 * 
	 * @return integer
	 * @throws IOException
	 *             IO exception
	 */
	protected int decodeInteger() throws IOException {
		LOGGER.trace(marker, "dec integer minus sign flag");
		
		if (decodeBoolean()) {
			// For negative values, the Unsigned Integer holds the
			// magnitude of the value minus 1
			return (-(decodeUnsignedInteger() + 1));
		} else {
			// positive
			return decodeUnsignedInteger();
		}
	}

	protected long decodeLong() throws IOException {
		LOGGER.trace(marker, "dec integer minus sign flag");
		
		if (decodeBoolean()) {
			// For negative values, the Unsigned Integer holds the
			// magnitude of the value minus 1
			return (-(decodeUnsignedLong() + 1L));
		} else {
			// positive
			return decodeUnsignedLong();
		}
	}

	public IntegerValue decodeIntegerValue() throws IOException {
		LOGGER.trace(marker, "dec integer minus sign flag");
		
		return decodeUnsignedIntegerValue(decodeBoolean());
	}

	public IntegerValue decodeUnsignedIntegerValue() throws IOException {
		return decodeUnsignedIntegerValue(false);
	}

	protected final IntegerValue decodeUnsignedIntegerValue(boolean negative)
			throws IOException {
		int b;
		int byteCnt = 0;
		
		LOGGER.trace(marker, "dec unsigned integer value @ pos [{}]", getPosition());
		
		for (int i = 0; i < MAX_OCTETS_FOR_LONG; i++) {
			// Read the next octet
			b = decode();
			byteCnt++;
			// If the most significant bit of the octet was 1,
			// another octet is going to come
			if (b < 128) {
				/* no more octets */
				switch (i) {
				case 0:
					/* one octet only */
					LOGGER.trace(marker, "decoded 1 byte");
					return IntegerValue.valueOf(negative ? -(b + 1) : b);
				case 1:
				case 2:
				case 3:
					/* integer value */
					maskedOctets[i] = b;
					/* int == 32 bits, 4 * 7bits = 28 bits */
					int iResult = 0;
					for (int k = i; k >= 0; k--) {
						iResult = (iResult << 7) | maskedOctets[k];
					}
					// For negative values, the Unsigned Integer holds the
					// magnitude of the value minus 1
					LOGGER.trace(marker, "decoded {} bytes",
							byteCnt);
					return IntegerValue.valueOf(negative ? -(iResult + 1)
							: iResult);
				default:
					/* long value */
					maskedOctets[i] = b;
					/* long == 64 bits, 9 * 7bits = 63 bits */
					long lResult = 0L;
					for (int k = i; k >= 0; k--) {
						lResult = (lResult << 7) | maskedOctets[k];
					}
					// For negative values, the Unsigned Integer holds the
					// magnitude of the value minus 1
					LOGGER.trace(marker, "decoded {} bytes",
							byteCnt);
					return IntegerValue.valueOf(negative ? -(lResult + 1L)
							: lResult);
				}
			} else {
				// the 7 least significant bits hold the actual value
				maskedOctets[i] = (b & 127);
			}
		}

		// Grrr, we got a BigInteger value to deal with
		BigInteger bResult = BigInteger.ZERO;
		BigInteger multiplier = BigInteger.ONE;
		// already read bytes
		for (int i = 0; i < MAX_OCTETS_FOR_LONG; i++) {
			bResult = bResult.add(multiplier.multiply(BigInteger
					.valueOf(maskedOctets[i])));
			multiplier = multiplier.shiftLeft(7);
		}
		// read new bytes
		do {
			// 1. Read the next octet
			b = decode();
			byteCnt++;
			// 2. The 7 least significant bits hold the value
			bResult = bResult.add(multiplier.multiply(BigInteger
					.valueOf(b & 127)));
			// 3. Multiply the multiplier by 128
			multiplier = multiplier.shiftLeft(7);
			// If the most significant bit of the octet was 1,
			// another is going to come
		} while (b > 127);

		// For negative values, the Unsigned Integer holds the
		// magnitude of the value minus 1
		if (negative) {
			bResult = bResult.add(BigInteger.ONE).negate();
		}

		LOGGER.trace(marker, "decoded {} bytes", byteCnt);
		return IntegerValue.valueOf(bResult);
	}

	/**
	 * Decodes and returns an n-bit unsigned integer as string.
	 */
	public IntegerValue decodeNBitUnsignedIntegerValue(int n)
			throws IOException {
		LOGGER.trace(marker, "dec {}-bit unsigned integer @ pos [{}]",
				n, getPosition());
		return IntegerValue.valueOf(decodeNBitUnsignedInteger(n));
	}

	/**
	 * Decode a decimal represented as a Boolean sign followed by two Unsigned
	 * Integers. A sign value of zero (0) is used to represent positive Decimal
	 * values and a sign value of one (1) is used to represent negative Decimal
	 * values The first Integer represents the integral portion of the Decimal
	 * value. The second positive integer represents the fractional portion of
	 * the decimal with the digits in reverse order to preserve leading zeros.
	 */
	public DecimalValue decodeDecimalValue() throws IOException {
		boolean negative = decodeBoolean();

		LOGGER.trace(marker, "dec decimal integer part");
		IntegerValue integral = decodeUnsignedIntegerValue(false);
		LOGGER.trace(marker, "dec decimal fraction");
		IntegerValue revFractional = decodeUnsignedIntegerValue(false);

		return new DecimalValue(negative, integral, revFractional);
	}

	/**
	 * Decode a Float represented as two consecutive Integers. The first Integer
	 * represents the mantissa of the floating point number and the second
	 * Integer represents the 10-based exponent of the floating point number
	 */
	public FloatValue decodeFloatValue() throws IOException {
		LOGGER.trace(marker, "dec float mantissa");
		IntegerValue mantissa = decodeIntegerValue(); 
		LOGGER.trace(marker, "dec float exponent");
		return new FloatValue(mantissa, decodeIntegerValue());
	}

	/**
	 * Decode Date-Time as sequence of values representing the individual
	 * components of the Date-Time.
	 */
	public DateTimeValue decodeDateTimeValue(DateTimeType type)
			throws IOException {
		int year = 0, monthDay = 0, time = 0, fractionalSecs = 0;

		switch (type) {
		case gYear: // Year, [Time-Zone]
			LOGGER.trace(marker, "dec dt year");
			year = decodeInteger() + DateTimeValue.YEAR_OFFSET;
			break;
		case gYearMonth: // Year, MonthDay, [TimeZone]
		case date: // Year, MonthDay, [TimeZone]
			LOGGER.trace(marker, "dec dt year");
			year = decodeInteger() + DateTimeValue.YEAR_OFFSET;
			LOGGER.trace(marker, "dec dt monthDay");
			monthDay = decodeNBitUnsignedInteger(DateTimeValue.NUMBER_BITS_MONTHDAY);
			break;
		case dateTime: // Year, MonthDay, Time, [FractionalSecs], [TimeZone]
			// e.g. "0001-01-01T00:00:00.111+00:33";
			LOGGER.trace(marker, "dec dt year");
			year = decodeInteger() + DateTimeValue.YEAR_OFFSET;
			LOGGER.trace(marker, "dec dt monthDay");
			monthDay = decodeNBitUnsignedInteger(DateTimeValue.NUMBER_BITS_MONTHDAY);
			// Note: *no* break;
		case time: // Time, [FractionalSecs], [TimeZone]
			// e.g. "12:34:56.135"
			LOGGER.trace(marker, "dec dt time");
			time = decodeNBitUnsignedInteger(DateTimeValue.NUMBER_BITS_TIME);
			LOGGER.trace(marker, "dec dt frac sec presence");
			boolean presenceFractionalSecs = decodeBoolean();
			if (presenceFractionalSecs) {
				LOGGER.trace(marker, "dec dt frac secs");
				fractionalSecs = decodeUnsignedInteger();
			}
			else fractionalSecs = 0;

			break;
		case gMonth: // MonthDay, [TimeZone]
			// e.g. "--12"
		case gMonthDay: // MonthDay, [TimeZone]
			// e.g. "--01-28"
		case gDay: // MonthDay, [TimeZone]
			// "---16";
			LOGGER.trace(marker, "dec dt monthDay");
			monthDay = decodeNBitUnsignedInteger(DateTimeValue.NUMBER_BITS_MONTHDAY);
			break;
		default:
			throw new UnsupportedOperationException();
		}

		LOGGER.trace(marker, "dec dt timezone presence");
		boolean presenceTimezone = decodeBoolean();
		int timeZone;
		
		if (presenceTimezone) { 
			LOGGER.trace(marker, "dec dt timezone");
			timeZone = decodeNBitUnsignedInteger(DateTimeValue.NUMBER_BITS_TIMEZONE) - DateTimeValue.TIMEZONE_OFFSET_IN_MINUTES;				
		}
		else timeZone = 0;

		return new DateTimeValue(type, year, monthDay, time, fractionalSecs,
				presenceTimezone, timeZone);
	}
	
	/**
	 * Return an Object whose toString() method returns the current
	 * decoder position in a format that can be used for logging.
	 */
	abstract protected Object getPosition();

}
