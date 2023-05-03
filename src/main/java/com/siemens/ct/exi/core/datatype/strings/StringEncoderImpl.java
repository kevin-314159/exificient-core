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

package com.siemens.ct.exi.core.datatype.strings;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.siemens.ct.exi.core.context.QNameContext;
import com.siemens.ct.exi.core.io.channel.EncoderChannel;
import com.siemens.ct.exi.core.util.MethodsBag;
import com.siemens.ct.exi.core.values.StringValue;

/**
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Richard.Kuntschke@siemens.com
 * 
 */

public class StringEncoderImpl extends AbstractStringCoder implements
		StringEncoder {

	// strings (all)
	protected Map<String, ValueContainer> stringValues;

	public StringEncoderImpl(boolean localValuePartitions) {
		this(localValuePartitions, DEFAULT_INITIAL_QNAME_LISTS);
	}

	public StringEncoderImpl(boolean localValuePartitions, int initialQNameLists) {
		super(localValuePartitions, initialQNameLists);
		stringValues = new HashMap<String, ValueContainer>();
	}

	public void writeValue(QNameContext context, EncoderChannel valueChannel,
			String value) throws IOException {

		ValueContainer vc = stringValues.get(value);

		if (vc != null) {
			// hit
			if (localValuePartitions && context.equals(vc.context)) {
				/*
				 * local value hit ==> is represented as zero (0) encoded as an
				 * Unsigned Integer followed by the compact identifier of the
				 * string value in the "local" value partition
				 */
				LOGGER.atTrace().log(
						"value local partition hit; id = {}; encode 0 then id",
						vc.localValueID);				
				valueChannel.encodeUnsignedInteger(0);
				int numberBitsLocal = MethodsBag
						.getCodingLength(getNumberOfStringValues(context));
				valueChannel.encodeNBitUnsignedInteger(vc.localValueID,
						numberBitsLocal);
			} else {
				/*
				 * global value hit ==> value is represented as one (1) encoded
				 * as an Unsigned Integer followed by the compact identifier of
				 * the String value in the global value partition.
				 */
				LOGGER.atTrace().log(
						"value global partition hit; id = {}; encode 1 then id",
						vc.globalValueID);				
				valueChannel.encodeUnsignedInteger(1);
				// global value size

				int numberBitsGlobal = MethodsBag.getCodingLength(stringValues
						.size());
				valueChannel.encodeNBitUnsignedInteger(vc.globalValueID,
						numberBitsGlobal);
			}
		} else {
			/*
			 * miss [not found in local nor in global value partition] ==>
			 * string literal is encoded as a String with the length incremented
			 * by two.
			 */
			final int L = value.codePointCount(0, value.length());
			LOGGER.atTrace().log(
					"value partition miss; encode # of chars ({}) + 2",
					L);	
			valueChannel.encodeUnsignedInteger(L + 2);
			/*
			 * If length L is greater than zero the string S is added
			 */
			if (L > 0) {
				LOGGER.atTrace().log("enc string");

				valueChannel.encodeStringOnly(value);
				// After encoding the string value, it is added to both the
				// associated "local" value string table partition and the
				// global value string table partition.
				addValue(context, value);
			}
		}

	}

	public ValueContainer getValueContainer(String value) {
		return this.stringValues.get(value);
	}

	public int getValueContainerSize() {
		return stringValues.size();
	}

	// Restricted char set
	public boolean isStringHit(String value) throws IOException {
		return (stringValues.get(value) != null);
	}

	public void addValue(QNameContext qnc, String value) {
		assert (!stringValues.containsKey(value));

		ValueContainer vc = new ValueContainer(value, qnc,
				getNumberOfStringValues(qnc), stringValues.size());

		LOGGER.atTrace().log("value partition addition; partition={},"
				+ " value={}, localId={}, globalId={}",
				qnc.getQName(),
				value, vc.localValueID, vc.globalValueID);
		
		// global context
		stringValues.put(value, vc);

		// local context
		this.addLocalValue(qnc, new StringValue(value));

	}

	public void clear() {
		super.clear();
		stringValues.clear();
	}

	public void setSharedStrings(List<String> sharedStrings) {
		for (String s : sharedStrings) {
			this.addValue(null, s);
		}
	}

	public static class ValueContainer {

		public final String value;
		public final QNameContext context;
		public final int localValueID;
		public final int globalValueID;

		public ValueContainer(String value, QNameContext context,
				int localValueID, int globalValueID) {
			this.value = value;
			this.context = context;
			this.localValueID = localValueID;
			this.globalValueID = globalValueID;
		}

		@Override
		public String toString() {
			return "['" + value + "', " + context + "," + localValueID + ","
					+ globalValueID + "]";
		}
	}

}
