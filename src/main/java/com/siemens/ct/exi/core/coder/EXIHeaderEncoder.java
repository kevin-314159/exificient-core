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

import javax.xml.namespace.QName;

import com.siemens.ct.exi.core.CodingMode;
import com.siemens.ct.exi.core.Constants;
import com.siemens.ct.exi.core.EXIFactory;
import com.siemens.ct.exi.core.EncodingOptions;
import com.siemens.ct.exi.core.FidelityOptions;
import com.siemens.ct.exi.core.datatype.strings.StringCoder;
import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.grammars.Grammars;
import com.siemens.ct.exi.core.io.channel.BitEncoderChannel;
import com.siemens.ct.exi.core.io.channel.EncoderChannel;
import com.siemens.ct.exi.core.values.BooleanValue;
import com.siemens.ct.exi.core.values.DecimalValue;
import com.siemens.ct.exi.core.values.IntegerValue;
import com.siemens.ct.exi.core.values.QNameValue;
import com.siemens.ct.exi.core.values.StringValue;

/**
 * EXI Header (see http://www.w3.org/TR/exi/#header)
 * 
 * <p>
 * Encoder
 * </p>
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Richard.Kuntschke@siemens.com
 * 
 */

public class EXIHeaderEncoder extends AbstractEXIHeader {

	public EXIHeaderEncoder() throws EXIException {
	}

	/**
	 * Writes the EXI header according to the header options with optional
	 * cookie, EXI options, ..
	 * 
	 * @param headerChannel
	 *            header channel
	 * @param f
	 *            factory
	 * @throws EXIException
	 *             EXI exception
	 */
	public void write(BitEncoderChannel headerChannel, EXIFactory f)
			throws EXIException {
		try {
			EncodingOptions headerOptions = f.getEncodingOptions();
			CodingMode codingMode = f.getCodingMode();

			// EXI Cookie
			if (headerOptions.isOptionEnabled(EncodingOptions.INCLUDE_COOKIE)) {
				LOGGER.atTrace().log("enc EXI cookie (4 bytes)");
				// four byte field consists of four characters " $ " , " E ",
				// " X " and " I " in that order
				headerChannel.encode('$');
				headerChannel.encode('E');
				headerChannel.encode('X');
				headerChannel.encode('I');
			}

			// Distinguishing Bits 10
			LOGGER.atTrace().log("enc distinguishing bits");
			headerChannel.encodeNBitUnsignedInteger(2, 2);

			// Presence Bit for EXI Options 0
			boolean includeOptions = headerOptions
					.isOptionEnabled(EncodingOptions.INCLUDE_OPTIONS);
			
			LOGGER.atTrace().log("enc header options presence");
			headerChannel.encodeBoolean(includeOptions);

			// EXI Format Version 0-0000
			LOGGER.atTrace().log("enc EXI preview flag");
			headerChannel.encodeBoolean(false); // preview
			LOGGER.atTrace().log("enc EXI format version");
			headerChannel.encodeNBitUnsignedInteger(0, 4);

			// EXI Header options and so forth
			if (includeOptions) {
				writeEXIOptions(f, headerChannel);
			}

			// other than bit-packed requires [Padding Bits]
			if (codingMode != CodingMode.BIT_PACKED) {
				headerChannel.align();
				headerChannel.flush();
			}

		} catch (IOException e) {
			throw new EXIException(e);
		}
	}

	public void writeEXIOptions(EXIFactory f, EncoderChannel encoderChannel)
			throws EXIException, IOException {

		LOGGER.atTrace().log("enc header options");
		
		EXIBodyEncoderInOrder encoder = (EXIBodyEncoderInOrder) getHeaderFactory()
				.createEXIBodyEncoder();
		encoder.setOutputChannel(encoderChannel);

		encoder.encodeStartDocument();
		encoder.encodeStartElement(Constants.W3C_EXI_NS_URI, HEADER, null);

		// final boolean isCanonical = f.getEncodingOptions().isOptionEnabled(
		// EncodingOptions.CANONICAL_EXI);

		/*
		 * lesscommon
		 */
		if (isLessCommon(f)) {
			encoder.encodeStartElement(Constants.W3C_EXI_NS_URI, LESSCOMMON,
					null);
			/*
			 * uncommon
			 */
			if (isUncommon(f)) {
				encoder.encodeStartElement(Constants.W3C_EXI_NS_URI, UNCOMMON,
						null);
				/*
				 * isUserDefinedMetaData
				 */
				if (isUserDefinedMetaData(f)) {

					if (f.getEncodingOptions().isOptionEnabled(
							EncodingOptions.INCLUDE_PROFILE_VALUES)) {
						// EXI profile options
						encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
								PROFILE, null);

						// feature empty exi:p element has been removed
						// if(!f.isLocalValuePartitions() && f
						// .getMaximumNumberOfBuiltInElementGrammars() == 0 &&
						// f
						// .getMaximumNumberOfBuiltInProductions() == 0) {
						// // empty exi:p element
						// } else {
						/*
						 * 1. The localValuePartitions parameter is encoded as
						 * the sign of the decimal value: the parameter is equal
						 * to 0 if the decimal value is positive and 1 if the
						 * decimal value is negative.
						 */
						boolean negative = f.isLocalValuePartitions();
						/*
						 * 2. The maximumNumberOfBuiltInElementGrammars
						 * parameter is represented by the first unsigned
						 * integer corresponding to integral portion of the
						 * decimal value: the
						 * maximumNumberOfBuiltInElementGrammars parameter is
						 * unbounded if the unsigned integer value is 0;
						 * otherwise it is equal to the unsigned integer value -
						 * 1.
						 */
						IntegerValue integral = IntegerValue.valueOf(1 + f
								.getMaximumNumberOfBuiltInElementGrammars());
						/*
						 * 3. The maximumNumberOfBuiltInProductions parameter is
						 * represented by the second unsigned integer
						 * corresponding to the fractional portion in reverse
						 * order of the decimal value: the
						 * maximumNumberOfBuiltInProductions parameter is
						 * unbounded if the unsigned integer value is 0;
						 * otherwise it is equal to the unsigned integer value -
						 * 1.
						 */
						IntegerValue revFractional = IntegerValue.valueOf(1 + f
								.getMaximumNumberOfBuiltInProductions());

						QNameValue qnv = new QNameValue(
								Constants.XML_SCHEMA_NS_URI, "decimal", null);
						encoder.encodeAttributeXsiType(qnv, null);
						DecimalValue dv = new DecimalValue(negative, integral,
								revFractional);
						encoder.encodeCharactersForce(dv);
						// }

						encoder.encodeEndElement(); // p
					}

				}
				/*
				 * alignment
				 */
				if (isAlignment(f)) {
					encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
							ALIGNMENT, null);

					/*
					 * byte
					 */
					if (isByte(f)) {
						encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
								BYTE, null);
						encoder.encodeEndElement(); // byte
					}
					/*
					 * pre-compress
					 */
					if (isPreCompress(f)) {
						encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
								PRE_COMPRESS, null);
						encoder.encodeEndElement(); // pre-compress
					}

					encoder.encodeEndElement(); // alignment
				}

				/*
				 * selfContained
				 */
				if (isSelfContained(f)) {
					encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
							SELF_CONTAINED, null);
					encoder.encodeEndElement();
				}

				/*
				 * valueMaxLength
				 */
				if (isValueMaxLength(f)) {
					encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
							VALUE_MAX_LENGTH, null);
					// encoder.encodeCharacters(f.getValueMaxLength() + "");
					encoder.encodeCharacters(IntegerValue.valueOf(f
							.getValueMaxLength()));
					encoder.encodeEndElement();
				}

				/*
				 * valuePartitionCapacity
				 */
				if (isValuePartitionCapacity(f)) {
					encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
							VALUE_PARTITION_CAPACITY, null);
					// encoder.encodeCharacters(f.getValuePartitionCapacity()+
					// "");
					encoder.encodeCharacters(IntegerValue.valueOf(f
							.getValuePartitionCapacity()));
					encoder.encodeEndElement();
				}

				/*
				 * datatypeRepresentationMap
				 */
				if (isDatatypeRepresentationMap(f)) {

					QName[] types = f.getDatatypeRepresentationMapTypes();
					QName[] representations = f
							.getDatatypeRepresentationMapRepresentations();
					assert (types.length == representations.length);

					// sequence "schema datatype" + datatype representation
					for (int i = 0; i < types.length; i++) {
						encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
								DATATYPE_REPRESENTATION_MAP, null);

						// schema datatype
						QName type = types[i];
						encoder.encodeStartElement(type.getNamespaceURI(),
								type.getLocalPart(), null);
						encoder.encodeEndElement();

						// datatype representation
						QName representation = representations[i];
						encoder.encodeStartElement(
								representation.getNamespaceURI(),
								representation.getLocalPart(), null);
						encoder.encodeEndElement();

						encoder.encodeEndElement(); // datatypeRepresentationMap
					}

				}

				encoder.encodeEndElement(); // uncommon
			}

			/*
			 * preserve
			 */
			if (isPreserve(f)) {
				encoder.encodeStartElement(Constants.W3C_EXI_NS_URI, PRESERVE,
						null);

				FidelityOptions fo = f.getFidelityOptions();

				/*
				 * dtd
				 */
				if (fo.isFidelityEnabled(FidelityOptions.FEATURE_DTD)) {
					encoder.encodeStartElement(Constants.W3C_EXI_NS_URI, DTD,
							null);
					encoder.encodeEndElement();
				}
				/*
				 * prefixes
				 */
				if (fo.isFidelityEnabled(FidelityOptions.FEATURE_PREFIX)) {
					encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
							PREFIXES, null);
					encoder.encodeEndElement();
				}
				/*
				 * lexicalValues
				 */
				if (fo.isFidelityEnabled(FidelityOptions.FEATURE_LEXICAL_VALUE)) {
					encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
							LEXICAL_VALUES, null);
					encoder.encodeEndElement();
				}
				/*
				 * comments
				 */
				if (fo.isFidelityEnabled(FidelityOptions.FEATURE_COMMENT)) {
					encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
							COMMENTS, null);
					encoder.encodeEndElement();
				}
				/*
				 * pis
				 */
				if (fo.isFidelityEnabled(FidelityOptions.FEATURE_PI)) {
					encoder.encodeStartElement(Constants.W3C_EXI_NS_URI, PIS,
							null);
					encoder.encodeEndElement();
				}

				encoder.encodeEndElement(); // preserve
			}

			/*
			 * blockSize
			 */
			if (isBlockSize(f)) {
				encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
						BLOCK_SIZE, null);
				// TODO typed fashion
				// encoder.encodeCharacters(f.getBlockSize() + "");
				encoder.encodeCharacters(IntegerValue.valueOf(f.getBlockSize()));
				encoder.encodeEndElement();
			}

			encoder.encodeEndElement(); // lesscommon
		}

		/*
		 * common
		 */
		if (isCommon(f)) {
			encoder.encodeStartElement(Constants.W3C_EXI_NS_URI, COMMON, null);
			/*
			 * compression
			 */
			if (isCompression(f)) {
				encoder.encodeStartElement(Constants.W3C_EXI_NS_URI,
						COMPRESSION, null);
				encoder.encodeEndElement();
			}
			/*
			 * fragment
			 */
			if (isFragment(f)) {
				encoder.encodeStartElement(Constants.W3C_EXI_NS_URI, FRAGMENT,
						null);
				encoder.encodeEndElement();
			}
			/*
			 * schemaId
			 */
			if (isSchemaId(f)) {
				encoder.encodeStartElement(Constants.W3C_EXI_NS_URI, SCHEMA_ID,
						null);

				Grammars g = f.getGrammars();

				// When the value of the "schemaID" element is empty, no user
				// defined schema information is used for processing the EXI
				// body; however, the built-in XML schema types are available
				// for use in the EXI body.
				if (g.isBuiltInXMLSchemaTypesOnly()) {
					assert (Constants.EMPTY_STRING.equals(g.getSchemaId()));
					// encoder.encodeCharacters(Constants.EMPTY_STRING);
					encoder.encodeCharacters(StringCoder.EMPTY_STRING_VALUE);
				} else {
					if (g.isSchemaInformed()) {
						// schema-informed
						// An example schemaID scheme is the use of URI that is
						// apt for globally identifying schema resources on the
						// Web.

						// HeaderOptions ho = f.getHeaderOptions();
						// Object schemaId =
						// ho.getOptionValue(HeaderOptions.INCLUDE_SCHEMA_ID);
						String schemaId = g.getSchemaId();
						assert (schemaId != null && schemaId.length() > 0);
						// encoder.encodeCharacters(schemaId.toString());
						encoder.encodeCharacters(new StringValue(schemaId));
					} else {
						// schema-less
						// When the "schemaID" element in the EXI options
						// document
						// contains the xsi:nil attribute with its value set to
						// true, no
						// schema information is used for processing the EXI
						// body.
						// TODO typed fashion
						encoder.encodeAttributeXsiNil(
								BooleanValue.BOOLEAN_VALUE_TRUE, null);
					}
				}

				encoder.encodeEndElement();
			}

			encoder.encodeEndElement(); // common
		}

		/*
		 * strict
		 */
		if (isStrict(f)) {
			encoder.encodeStartElement(Constants.W3C_EXI_NS_URI, STRICT, null);
			encoder.encodeEndElement();
		}

		encoder.encodeEndElement(); // header
		encoder.encodeEndDocument();
		
		LOGGER.atTrace().log("finished encoding header options");
	}

	protected boolean isLessCommon(EXIFactory f) {
		return (isUncommon(f) || isPreserve(f) || isBlockSize(f));
	}

	protected boolean isUncommon(EXIFactory f) {
		// user defined meta-data, alignment, selfContained, valueMaxLength,
		// valuePartitionCapacity, datatypeRepresentationMap
		return (isUserDefinedMetaData(f) || isAlignment(f)
				|| isSelfContained(f) || isValueMaxLength(f)
				|| isValuePartitionCapacity(f) || isDatatypeRepresentationMap(f));
	}

	protected boolean isUserDefinedMetaData(EXIFactory f) {
		// EXI profile options
		return (f.isGrammarLearningDisabled() || !f.isLocalValuePartitions());
	}

	protected boolean isAlignment(EXIFactory f) {
		// byte, pre-compress
		return (isByte(f) || isPreCompress(f));
	}

	protected boolean isByte(EXIFactory f) {
		return (f.getCodingMode() == CodingMode.BYTE_PACKED);
	}

	protected boolean isPreCompress(EXIFactory f) {
		return (f.getCodingMode() == CodingMode.PRE_COMPRESSION);
	}

	protected boolean isSelfContained(EXIFactory f) {
		return f.getFidelityOptions().isFidelityEnabled(
				FidelityOptions.FEATURE_SC);
	}

	protected boolean isValueMaxLength(EXIFactory f) {
		return (f.getValueMaxLength() != Constants.DEFAULT_VALUE_MAX_LENGTH);
	}

	protected boolean isValuePartitionCapacity(EXIFactory f) {
		return (f.getValuePartitionCapacity() >= 0);
	}

	protected boolean isDatatypeRepresentationMap(EXIFactory f) {
		// Canonical EXI: When the value of the Preserve.lexicalValues fidelity
		// option is true the element datatypeRepresentationMap MUST be omitted
		return (!f.getFidelityOptions().isFidelityEnabled(
				FidelityOptions.FEATURE_LEXICAL_VALUE)
				&& f.getDatatypeRepresentationMapTypes() != null && f
				.getDatatypeRepresentationMapTypes().length > 0);
	}

	protected boolean isPreserve(EXIFactory f) {
		FidelityOptions fo = f.getFidelityOptions();
		// dtd, prefixes, lexicalValues, comments, pis
		return (fo.isFidelityEnabled(FidelityOptions.FEATURE_DTD)
				|| fo.isFidelityEnabled(FidelityOptions.FEATURE_PREFIX)
				|| fo.isFidelityEnabled(FidelityOptions.FEATURE_LEXICAL_VALUE)
				|| fo.isFidelityEnabled(FidelityOptions.FEATURE_COMMENT) || fo
					.isFidelityEnabled(FidelityOptions.FEATURE_PI));
	}

	protected boolean isBlockSize(EXIFactory f) {
		// Canonical EXI : The element blockSize MUST be omitted if neither
		// compression nor pre-compress is present
		return (f.getBlockSize() != Constants.DEFAULT_BLOCK_SIZE && (f
				.getCodingMode() == CodingMode.COMPRESSION || f.getCodingMode() == CodingMode.PRE_COMPRESSION));
	}

	protected boolean isCommon(EXIFactory f) {
		// compression, fragment, schemaId
		return (isCompression(f) || isFragment(f) || isSchemaId(f));
	}

	protected boolean isCompression(EXIFactory f) {
		return (f.getCodingMode() == CodingMode.COMPRESSION);
	}

	protected boolean isFragment(EXIFactory f) {
		return f.isFragment();
	}

	protected boolean isSchemaId(EXIFactory f) {
		EncodingOptions ho = f.getEncodingOptions();
		return ho.isOptionEnabled(EncodingOptions.INCLUDE_SCHEMA_ID);
	}

	protected boolean isStrict(EXIFactory f) {
		return f.getFidelityOptions().isStrict();
	}

}
