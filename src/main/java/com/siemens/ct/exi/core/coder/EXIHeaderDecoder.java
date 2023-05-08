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

package com.siemens.ct.exi.core.coder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import com.siemens.ct.exi.core.CodingMode;
import com.siemens.ct.exi.core.Constants;
import com.siemens.ct.exi.core.DecodingOptions;
import com.siemens.ct.exi.core.EXIFactory;
import com.siemens.ct.exi.core.FidelityOptions;
import com.siemens.ct.exi.core.SchemaIdResolver;
import com.siemens.ct.exi.core.context.QNameContext;
import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.exceptions.UnsupportedOption;
import com.siemens.ct.exi.core.grammars.SchemaLessGrammars;
import com.siemens.ct.exi.core.grammars.event.EventType;
import com.siemens.ct.exi.core.helpers.DefaultEXIFactory;
import com.siemens.ct.exi.core.io.channel.BitDecoderChannel;
import com.siemens.ct.exi.core.io.channel.DecoderChannel;
import com.siemens.ct.exi.core.values.BooleanValue;
import com.siemens.ct.exi.core.values.DecimalValue;
import com.siemens.ct.exi.core.values.IntegerValue;
import com.siemens.ct.exi.core.values.IntegerValueType;
import com.siemens.ct.exi.core.values.Value;
import com.siemens.ct.exi.core.values.ValueType;

/**
 * EXI Header (see http://www.w3.org/TR/exi/#header)
 * 
 * <p>
 * Decoder
 * </p>
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Richard.Kuntschke@siemens.com
 * 
 */

public class EXIHeaderDecoder extends AbstractEXIHeader {

	protected QNameContext lastSE;

	protected boolean dtrSection;
	// protected boolean emptyExiP;
	protected List<QName> dtrMapTypes = new ArrayList<QName>();
	protected List<QName> dtrMapRepresentations = new ArrayList<QName>();

	public EXIHeaderDecoder() throws EXIException {
	}

	protected void clear() {
		lastSE = null;

		// emptyExiP = true;

		dtrSection = false;
		dtrMapTypes.clear();
		dtrMapRepresentations.clear();
	}

	public EXIFactory parse(BitDecoderChannel headerChannel,
			EXIFactory noOptionsFactory) throws EXIException {
		try {
			// EXI Cookie
			if (headerChannel.lookAhead() == '$') {
				LOGGER.atTrace().log("dec EXI cookie (4 bytes)");
				int h0 = headerChannel.decode();
				int h1 = headerChannel.decode();
				int h2 = headerChannel.decode();
				int h3 = headerChannel.decode();
				if (h0 != '$' || h1 != 'E' || h2 != 'X' || h3 != 'I') {
					throw new EXIException("No valid EXI Cookie ($EXI)");
				}
			}

			// An EXI header starts with Distinguishing Bits part, which is a
			// two bit field 1 0
			LOGGER.atTrace().log("dec EXI distinguishing bits");
			if (headerChannel
					.decodeNBitUnsignedInteger(NUMBER_OF_DISTINGUISHING_BITS) != DISTINGUISHING_BITS_VALUE) {
				throw new EXIException(
						"No valid EXI document according distinguishing bits");
			}

			// Presence Bit for EXI Options
			LOGGER.atTrace().log("dec EXI options presence");
			boolean presenceOptions = headerChannel.decodeBoolean();

			// EXI Format Version (1 4+)

			// The first bit of the version field indicates whether the version
			// is a
			// preview or final version of the EXI format.
			// A value of 0 indicates this is a final version and a value of 1
			// indicates this is a preview version.
			// @SuppressWarnings("unused")
			LOGGER.atTrace().log("dec EXI preview flag");
			boolean previewVersion = headerChannel.decodeBoolean();
			assert (!previewVersion);

			// one or more 4-bit unsigned integers represent the version number
			// 1. Read next 4 bits as an unsigned integer value.
			// 2. Add the value that was just read to the version number.
			// 3. If the value is 15, go to step 1, otherwise (i.e. the value
			// being
			// in the range of 0-14),
			// use the current value of the version number as the EXI version
			// number.
			int value;
			int version = 0;
			LOGGER.atTrace().log("dec EXI version");
			do {
				value = headerChannel
						.decodeNBitUnsignedInteger(NUMBER_OF_FORMAT_VERSION_BITS);
				version += value;
			} while (value == FORMAT_VERSION_CONTINUE_VALUE);
			assert (version == 0);

			LOGGER.atTrace().log("decoded EXI version = {}", version+1);
			
			// [EXI Options] ?
			EXIFactory exiFactory;
			if (presenceOptions) {
				// use default options and re-set if needed
				exiFactory = readEXIOptions(headerChannel, noOptionsFactory);
			} else {
				exiFactory = noOptionsFactory;
			}

			// other than bit-packed has [Padding Bits]
			CodingMode codingMode = exiFactory.getCodingMode();
			if (codingMode != CodingMode.BIT_PACKED) {
				headerChannel.align();
			}

			return exiFactory;

		} catch (IOException e) {
			throw new EXIException(e);
		}

	}

	public EXIFactory readEXIOptions(DecoderChannel decoderChannel,
			EXIFactory noOptionsFactory) throws EXIException, IOException {
		EXIBodyDecoderInOrder decoder = (EXIBodyDecoderInOrder) getHeaderFactory()
				.createEXIBodyDecoder();
		decoder.setInputChannel(decoderChannel);

		// schemaId = null;
		// schemaIdSet = false;

		// // clone factory
		// EXIFactory exiOptionsFactory = noOptionsFactory.clone();

		EXIFactory exiOptionsFactory = DefaultEXIFactory.newInstance();
		// re-use important settings
		exiOptionsFactory.setSchemaIdResolver(noOptionsFactory
				.getSchemaIdResolver());
		exiOptionsFactory.setDecodingOptions(noOptionsFactory
				.getDecodingOptions());
		// re-use schema knowledge
		exiOptionsFactory.setGrammars(noOptionsFactory.getGrammars());

		// // STRICT is special, there is no NON STRICT flag --> per default set
		// to
		// // non strict
		// if (exiOptionsFactory.getFidelityOptions().isStrict()) {
		// exiOptionsFactory.getFidelityOptions().setFidelity(
		// FidelityOptions.FEATURE_STRICT, false);
		// }

		clear();

		EventType eventType;
		while ((eventType = decoder.next()) != null) {

			switch (eventType) {
			case START_DOCUMENT:
				decoder.decodeStartDocument();
				break;
			case END_DOCUMENT:
				decoder.decodeEndDocument();
				break;
			case ATTRIBUTE_XSI_NIL:
				decoder.decodeAttributeXsiNil();
				handleXsiNil(decoder.getAttributeValue(), exiOptionsFactory);
				break;
			case ATTRIBUTE_XSI_TYPE:
				decoder.decodeAttributeXsiType();
				break;
			case ATTRIBUTE:
			case ATTRIBUTE_NS:
			case ATTRIBUTE_GENERIC:
			case ATTRIBUTE_GENERIC_UNDECLARED:
			case ATTRIBUTE_INVALID_VALUE:
			case ATTRIBUTE_ANY_INVALID_VALUE:
				decoder.decodeAttribute();
				break;
			case NAMESPACE_DECLARATION:
				decoder.decodeNamespaceDeclaration();
				break;
			case START_ELEMENT:
			case START_ELEMENT_NS:
			case START_ELEMENT_GENERIC:
			case START_ELEMENT_GENERIC_UNDECLARED:
				handleStartElement(decoder.decodeStartElement(),
						exiOptionsFactory);
				break;
			case END_ELEMENT:
			case END_ELEMENT_UNDECLARED:
				handleEndElement(decoder.decodeEndElement(), exiOptionsFactory);
				break;
			case CHARACTERS:
			case CHARACTERS_GENERIC:
			case CHARACTERS_GENERIC_UNDECLARED:
				handleCharacters(decoder.decodeCharacters(), exiOptionsFactory);
				break;
			default:
				throw new RuntimeException("Unexpected EXI Event in Header '"
						+ eventType + "' ");
			}
		}

		// dtr map?
		if (dtrMapTypes.size() == dtrMapTypes.size() && dtrMapTypes.size() > 0) {
			QName[] dtrMapTypesA = new QName[dtrMapTypes.size()];
			dtrMapTypesA = dtrMapTypes.toArray(dtrMapTypesA);
			QName[] dtrMapRepresentationsA = new QName[dtrMapRepresentations
					.size()];
			dtrMapRepresentationsA = dtrMapRepresentations
					.toArray(dtrMapRepresentationsA);
			exiOptionsFactory.setDatatypeRepresentationMap(dtrMapTypesA,
					dtrMapRepresentationsA);
		}

		return exiOptionsFactory;
	}

	protected void handleStartElement(QNameContext se, EXIFactory f)
			throws UnsupportedOption {

		if (dtrSection) {
			if (dtrMapTypes.size() == dtrMapRepresentations.size()) {
				// schema datatype
				dtrMapTypes.add(se.getQName());
			} else {
				// datatype representation
				dtrMapRepresentations.add(se.getQName());
			}
		} else if (Constants.W3C_EXI_NS_URI.equals(se.getNamespaceUri())) {
			String localName = se.getLocalName();

			if (BYTE.equals(localName)) {
				f.setCodingMode(CodingMode.BYTE_PACKED);
			} else if (PRE_COMPRESS.equals(localName)) {
				f.setCodingMode(CodingMode.PRE_COMPRESSION);
			} else if (SELF_CONTAINED.equals(localName)) {
				f.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_SC,
						true);
			} else if (DATATYPE_REPRESENTATION_MAP.equals(localName)) {
				dtrSection = true;
			} else if (DTD.equals(localName)) {
				f.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_DTD,
						true);
			} else if (PREFIXES.equals(localName)) {
				f.getFidelityOptions().setFidelity(
						FidelityOptions.FEATURE_PREFIX, true);
			} else if (LEXICAL_VALUES.equals(localName)) {
				f.getFidelityOptions().setFidelity(
						FidelityOptions.FEATURE_LEXICAL_VALUE, true);
			} else if (COMMENTS.equals(localName)) {
				f.getFidelityOptions().setFidelity(
						FidelityOptions.FEATURE_COMMENT, true);
			} else if (PIS.equals(localName)) {
				f.getFidelityOptions().setFidelity(FidelityOptions.FEATURE_PI,
						true);
			} else if (COMPRESSION.equals(localName)) {
				f.setCodingMode(CodingMode.COMPRESSION);
			} else if (FRAGMENT.equals(localName)) {
				f.setFragment(true);
			} else if (STRICT.equals(localName)) {
				f.getFidelityOptions().setFidelity(
						FidelityOptions.FEATURE_STRICT, true);
			} else if (PROFILE.equals(localName)) {
				// profile parameters, not used yet
			}
		}

		lastSE = se;
	}

	protected void handleEndElement(QNameContext ee, EXIFactory f) {
		if (Constants.W3C_EXI_NS_URI.equals(ee.getNamespaceUri())) {
			String localName = ee.getLocalName();

			if (DATATYPE_REPRESENTATION_MAP.equals(localName)) {
				dtrSection = false;
				// } else if (PROFILE.equals(localName) && this.emptyExiP) {
				// f.setLocalValuePartitions(false);
				// f.setMaximumNumberOfBuiltInElementGrammars(0);
				// f.setMaximumNumberOfBuiltInProductions(0);
			}
		}
	}

	protected void handleCharacters(Value value, EXIFactory f)
			throws EXIException {
		String localName = lastSE.getLocalName();

		if (VALUE_MAX_LENGTH.equals(localName)) {
			if (value instanceof IntegerValue) {
				IntegerValue iv = (IntegerValue) value;
				f.setValueMaxLength(iv.intValue());
			} else {
				throw new EXIException("[EXI-Header] Failure while processing "
						+ localName);
			}
		} else if (VALUE_PARTITION_CAPACITY.equals(localName)) {
			if (value instanceof IntegerValue) {
				IntegerValue iv = (IntegerValue) value;
				if (iv.getIntegerValueType() == IntegerValueType.INT) {
					f.setValuePartitionCapacity(iv.intValue());
				} else {
					throw new EXIException(
							"[EXI-Header] ValuePartitionCapacity other than int not supported: "
									+ iv);
				}

			} else {
				throw new EXIException("[EXI-Header] Failure while processing "
						+ localName);
			}
		} else if (BLOCK_SIZE.equals(localName)) {
			if (value instanceof IntegerValue) {
				IntegerValue iv = (IntegerValue) value;
				if (iv.getIntegerValueType() == IntegerValueType.INT) {
					f.setBlockSize(iv.intValue());
				} else {
					throw new EXIException(
							"[EXI-Header] BlockSize other than int not supported: "
									+ iv);
				}
			} else {
				throw new EXIException("[EXI-Header] Failure while processing "
						+ localName);
			}
		} else if (SCHEMA_ID.equals(localName)) {
			if (f.getDecodingOptions().isOptionEnabled(
					DecodingOptions.IGNORE_SCHEMA_ID)) {
				// don't do anything
			} else {
				String schemaId = value.toString();

				assert (schemaId != null);
				SchemaIdResolver sir = f.getSchemaIdResolver();
				if (sir != null) {
					f.setGrammars(sir.resolveSchemaId(schemaId));
				} else {
					// // default --> try to find GrammarFactory
					// // com.siemens.ct.exi.GrammarFactory
					// try {
					// Class<?> cls =
					// this.getClass().getClassLoader().loadClass("com.siemens.ct.exi.GrammarFactory");
					// // no parameter for static newInstance method
					// Class<?> noparams[] = {};
					// cls.getDeclaredMethods();
					// java.lang.reflect.Method methodGF =
					// cls.getDeclaredMethod("newInstance", noparams);
					// Object grammarFactory = methodGF.invoke(null);
					//
					// Grammars grs;
					// if(schemaId.length() == 0) {
					// // call the createXSDTypesOnlyGrammars method
					// java.lang.reflect.Method method =
					// grammarFactory.getClass().getDeclaredMethod("createXSDTypesOnlyGrammars");
					// grs = (Grammars) method.invoke(grammarFactory);
					// } else {
					// // call the createGrammars method, pass schemaId as
					// String parameter
					// Class<?>[] paramString = new Class[1];
					// paramString[0] = String.class;
					// java.lang.reflect.Method method =
					// grammarFactory.getClass().getDeclaredMethod("createGrammars",
					// paramString);
					// grs = (Grammars) method.invoke(grammarFactory, schemaId);
					// }
					// f.setGrammars(grs);
					// } catch (Exception e) {
					throw new EXIException("EXI Header povides schemaId '"
							+ schemaId + "'. No schemaIdResolver set!");
					// }
				}
			}
		} else if (PROFILE.equals(localName)) {
			// emptyExiP = false;
			if (value.getValueType() == ValueType.DECIMAL) {
				DecimalValue dv = (DecimalValue) value;
				f.setLocalValuePartitions(dv.isNegative());
				assert (dv.getIntegral().getIntegerValueType() == IntegerValueType.INT);
				f.setMaximumNumberOfBuiltInElementGrammars(dv.getIntegral()
						.intValue() - 1);
				assert (dv.getRevFractional().getIntegerValueType() == IntegerValueType.INT);
				f.setMaximumNumberOfBuiltInProductions(dv.getRevFractional()
						.intValue() - 1);
			}
		}
	}

	protected void handleXsiNil(Value value, EXIFactory f) throws EXIException {
		String localName = lastSE.getLocalName();

		if (SCHEMA_ID.equals(localName)) {
			if (value instanceof BooleanValue) {
				BooleanValue bv = (BooleanValue) value;
				if (bv.toBoolean()) {
					// schema-less, default
					f.setGrammars(new SchemaLessGrammars());
				}
			} else {
				throw new EXIException("[EXI-Header] Failure while processing "
						+ localName);
			}
		}
	}

}
