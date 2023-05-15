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

import com.siemens.ct.exi.core.util.MethodsBag;
import com.siemens.ct.exi.core.values.DateTimeValue;
import com.siemens.ct.exi.core.values.FloatValue;
import com.siemens.ct.exi.core.values.IntegerValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Richard.Kuntschke@siemens.com
 * 
 */

public abstract class AbstractEncoderChannel implements EncoderChannel {


	/**
	 * Logger for this class and for subclasses.
	 */
	protected static Logger LOGGER = LoggerFactory.getLogger(AbstractEncoderChannel.class);

	/**
	 * Marker to use when logging.
	 * When a log-generating function A calls a log-generating function B,
	 * A may add a {@link Marker} to marker before the call to B, and remove it after the
	 * call to B, so that B's events that occur in the context of A can be
	 * filtered out by the marker.  This is used, for example, to mark events
	 * associated with encoding the individual characters of a string.
	 */
	protected Marker marker = MarkerFactory.getMarker("");
	
	/**
	 *  Marker for individual character events.
	 */
	protected static final Marker CHAR_MARKER = 
			MarkerFactory.getMarker("CHARACTER_LEVEL");

	/**
	 * Encode a binary value as a length-prefixed sequence of octets.
	 */
	public void encodeBinary(byte[] b) throws IOException {
		LOGGER.trace(marker, "enc binary value length ({})", b.length);
		encodeUnsignedInteger(b.length);
		LOGGER.trace(marker, "enc binary value content");
		encode(b, 0, b.length);
	}

	/**
	 * Encode a string as a length-prefixed sequence of UCS codepoints, each of
	 * which is encoded as an integer. Look for codepoints of more than 16 bits
	 * that are represented as UTF-16 surrogate pairs in Java.
	 */
	public void encodeString(final String s) throws IOException {
		final int lenChars = s.length();
		final int lenCharacters = s.codePointCount(0, lenChars);
		
		LOGGER.trace(marker, "enc string value # of chars ({})", lenCharacters);
		encodeUnsignedInteger(lenCharacters);
		encodeStringOnly(s);
	}

	/**
	 * 
	 */
	public void encodeStringOnly(final String s) throws IOException {
		final int lenChars = s.length();
		
		boolean log = LOGGER.isTraceEnabled();
		int byteCnt = 0;
		
		if (log) LOGGER.trace(marker, "enc string value content");
		
		marker.add(CHAR_MARKER);
		
		for (int i = 0; i < lenChars; i++) {
			final char ch = s.charAt(i);

			// Is this a UTF-16 surrogate pair?
			if (Character.isHighSurrogate(ch)) {
				// use code-point and increment loop count (2 char's)
				int codePoint = s.codePointAt(i++);
				encodeUnsignedInteger(codePoint);
				if (log) byteCnt += MethodsBag.numberOf7BitBlocksToRepresent(codePoint);
			} else {
				encodeUnsignedInteger(ch);
				if (log) byteCnt += MethodsBag.numberOf7BitBlocksToRepresent(ch);
			}
		}
		
		marker.remove(CHAR_MARKER);
		
		if (log) LOGGER.trace(
				"string value content encoded in {} bytes",
				byteCnt);
	}

	/**
	 * Encode an arbitrary precision integer using a sign bit followed by a
	 * sequence of octets. The most significant bit of the last octet is set to
	 * zero to indicate sequence termination. Only seven bits per octet are used
	 * to store the integer's value.
	 */
	public void encodeInteger(int n) throws IOException {	
		LOGGER.trace(marker, "enc integer minus sign");		
		// signalize sign
		if (n < 0) {
			encodeBoolean(true);
			// For negative values, the Unsigned Integer holds the
			// magnitude of the value minus 1
			encodeUnsignedInteger((-n) - 1);
		} else {
			encodeBoolean(false);
			encodeUnsignedInteger(n);
		}		
	}

	protected void encodeLong(long l) throws IOException {
		LOGGER.trace(marker, "encode long integer minus sign");
		// signalize sign
		if (l < 0) {
			encodeBoolean(true);
			encodeUnsignedLong((-l) - 1);
		} else {
			encodeBoolean(false);
			encodeUnsignedLong(l);
		}
	}

	protected void encodeBigInteger(BigInteger bi) throws IOException {
		LOGGER.trace(marker, "encode big integer minus sign");
		if (bi.signum() < 0) {
			encodeBoolean(true); // negative
			encodeUnsignedBigInteger(bi.negate().subtract(BigInteger.ONE));
		} else {
			encodeBoolean(false); // positive
			encodeUnsignedBigInteger(bi);
		}
	}

	public void encodeIntegerValue(IntegerValue iv) throws IOException {
		switch (iv.getIntegerValueType()) {
		case INT:
			encodeInteger(iv.intValue());
			break;
		case LONG:
			encodeLong(iv.longValue());
			break;
		case BIG:
			encodeBigInteger(iv.bigIntegerValue());
			break;
		default:
			throw new IOException("Unexpcted EXI integer value type "
					+ iv.getValueType());
		}
	}

	/**
	 * Encode an arbitrary precision non negative integer using a sequence of
	 * octets. The most significant bit of the last octet is set to zero to
	 * indicate sequence termination. Only seven bits per octet are used to
	 * store the integer's value.
	 */
	public void encodeUnsignedInteger(int n) throws IOException {
		if (n < 0) {
			throw new UnsupportedOperationException();
		}

		if (n < 128) {
			// write byte as is
			encode(n);
		} else {
			final int n7BitBlocks = MethodsBag.numberOf7BitBlocksToRepresent(n);

			LOGGER.trace(marker, "enc unsigned int in {} bytes", n7BitBlocks);
			
			switch (n7BitBlocks) {
			case 5:
				encode(128 | n);
				n = n >>> 7;
			case 4:
				encode(128 | n);
				n = n >>> 7;
			case 3:
				encode(128 | n);
				n = n >>> 7;
			case 2:
				encode(128 | n);
				n = n >>> 7;
			case 1:
				// 0 .. 7 (last byte)
				encode(0 | n);
			}
		}
	}

	protected void encodeUnsignedLong(long l) throws IOException {
		int byteCnt = 0;
		
		if (l < 0) {
			throw new UnsupportedOperationException();
		}

		int lastEncode = (int) l;
		l >>>= 7;
		byteCnt += 1;
		
		while (l != 0) {
			encode(lastEncode | 128);
			lastEncode = (int) l;
			l >>>= 7;
			byteCnt += 1;
		}

		encode(lastEncode);
		byteCnt += 1;
		
		LOGGER.trace(marker, "enc unsigned long in {} bytes", byteCnt);
	}

	protected void encodeUnsignedBigInteger(BigInteger bi) throws IOException {
		
		if (bi.signum() < 0) {
			throw new UnsupportedOperationException();
		}

		// does not fit into long (64 bits)
		// approach: write byte per byte
		int m = bi.bitLength() % 7;
		int nbytes = bi.bitLength() / 7 + (m > 0 ? 1 : 0);
		int byteCnt = nbytes;
		
		while (--nbytes > 0) {
			// 1XXXXXXX ... 1XXXXXXX
			encode(128 | bi.intValue());
			bi = bi.shiftRight(7);
		}

		// 0XXXXXXX
		encode(0 | bi.intValue());
		
		LOGGER.trace(marker, "enc unsigned big int in {} bytes", byteCnt);
	}

	public void encodeUnsignedIntegerValue(IntegerValue iv) throws IOException {
		switch (iv.getIntegerValueType()) {
		case INT:
			encodeUnsignedInteger(iv.intValue());
			break;
		case LONG:
			encodeUnsignedLong(iv.longValue());
			break;
		case BIG:
			encodeUnsignedBigInteger(iv.bigIntegerValue());
			break;
		default:
			throw new IOException("Unexpcted EXI integer value type "
					+ iv.getValueType());
		}
	}

	/**
	 * Encode a decimal represented as a Boolean sign followed by two Unsigned
	 * Integers. A sign value of zero (0) is used to represent positive Decimal
	 * values and a sign value of one (1) is used to represent negative Decimal
	 * values The first Integer represents the integral portion of the Decimal
	 * value. The second positive integer represents the fractional portion of
	 * the decimal with the digits in reverse order to preserve leading zeros.
	 */

	public void encodeDecimal(boolean negative, IntegerValue integral,
			IntegerValue reverseFraction) throws IOException, RuntimeException {
		// sign, integral, reverse fractional
		LOGGER.trace(marker, "enc decimal minus sign");
		encodeBoolean(negative);
		LOGGER.trace(marker, "enc decimal integral");
		encodeUnsignedIntegerValue(integral);
		LOGGER.trace(marker, "enc decimal rev frac");
		encodeUnsignedIntegerValue(reverseFraction);
	}

	/**
	 * Encode a Float represented as two consecutive Integers. The first Integer
	 * represents the mantissa of the floating point number and the second
	 * Integer represents the 10-based exponent of the floating point number
	 */
	public void encodeFloat(FloatValue fv) throws IOException {
		// encode mantissa and exponent
		LOGGER.trace(marker, "enc float mantissa");
		encodeIntegerValue(fv.getMantissa());
		LOGGER.trace(marker, "enc float exponent");
		encodeIntegerValue(fv.getExponent());
	}

	public void encodeDateTime(DateTimeValue datetime) throws IOException {
		switch (datetime.type) {
		case gYear: // Year, [Time-Zone]
			LOGGER.trace(marker, "enc dt year");
			encodeInteger(datetime.year - DateTimeValue.YEAR_OFFSET);
			break;
		case gYearMonth: // Year, MonthDay, [TimeZone]
		case date: // Year, MonthDay, [TimeZone]
			LOGGER.trace(marker, "enc dt year");
			encodeInteger(datetime.year - DateTimeValue.YEAR_OFFSET);
			LOGGER.trace(marker, "enc dt monthDay");
			encodeNBitUnsignedInteger(datetime.monthDay,
					DateTimeValue.NUMBER_BITS_MONTHDAY);
			break;
		case dateTime: // Year, MonthDay, Time, [FractionalSecs],
			// [TimeZone]
			LOGGER.trace(marker, "enc dt year");
			encodeInteger(datetime.year - DateTimeValue.YEAR_OFFSET);
			LOGGER.trace(marker, "enc dt monthDay");
			encodeNBitUnsignedInteger(datetime.monthDay,
					DateTimeValue.NUMBER_BITS_MONTHDAY);
			// Note: *no* break;
		case time: // Time, [FractionalSecs], [TimeZone]
			LOGGER.trace(marker, "enc dt time");
			this.encodeNBitUnsignedInteger(datetime.time,
					DateTimeValue.NUMBER_BITS_TIME);
			LOGGER.trace(marker, "enc dt frac secs presence");
			if (datetime.presenceFractionalSecs) {
				encodeBoolean(true);
				LOGGER.trace(marker, "enc dt frac secs");
				encodeUnsignedInteger(datetime.fractionalSecs);
			} else {
				encodeBoolean(false);
			}
			break;
		case gMonth: // MonthDay, [TimeZone]
		case gMonthDay: // MonthDay, [TimeZone]
		case gDay: // MonthDay, [TimeZone]
			LOGGER.trace(marker, "enc dt monthDay");
			encodeNBitUnsignedInteger(datetime.monthDay,
					DateTimeValue.NUMBER_BITS_MONTHDAY);
			break;
		default:
			throw new UnsupportedOperationException();
		}
		// [TimeZone]
		LOGGER.trace(marker, "enc dt tz presence");
		if (datetime.presenceTimezone) {
			encodeBoolean(true);
			LOGGER.trace(marker, "enc dt timezone");
			encodeNBitUnsignedInteger(datetime.timezone
					+ DateTimeValue.TIMEZONE_OFFSET_IN_MINUTES,
					DateTimeValue.NUMBER_BITS_TIMEZONE);
		} else {
			encodeBoolean(false);
		}
	}
	
	/**
	 * Return an Object whose toString method returns a string that
	 * represents that current encoding position which can be used
	 * in log messages.
	 */
	protected abstract Object getPosition();
}
