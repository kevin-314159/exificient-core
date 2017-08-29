/*
 * Copyright (c) 2007-2016 Siemens AG
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

package com.siemens.ct.exi.context;

import javax.xml.namespace.QName;

import com.siemens.ct.exi.grammars.event.Attribute;
import com.siemens.ct.exi.grammars.event.StartElement;
import com.siemens.ct.exi.grammars.grammar.SchemaInformedFirstStartTagGrammar;

/**
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Joerg.Heuer@siemens.com
 * 
 * @version 0.9.7-SNAPSHOT
 */

public class QNameContext {
	
	/**
	 * namespace URI ID
	 */
	int namespaceUriID;
	/**
	 * local-name ID
	 */
	int localNameID;
	/**
	 * qualified name
	 */
	QName qName;
	/**
	 * default qualified name (as String)
	 */
	String defaultQNameAsString;
	/**
	 * default prefix (if none specified)
	 */
	String defaultPrefix;

	/**
	 *  global element
	 */
	StartElement grammarGlobalElement;

	/**
	 *  global grammar attribute (if any)
	 */
	Attribute grammarGlobalAttribute;
	
	/**
	 *  type grammar
	 */
	SchemaInformedFirstStartTagGrammar typeGrammar;

	public QNameContext() {
	}
	
	public QNameContext(int namespaceUriID, int localNameID, QName qName) {
		setQName(qName);
		setNamespaceUriID(namespaceUriID);
		setLocalNameID(localNameID);
	}

	public QName getQName() {
		return this.qName;
	}
	
	public void setQName(QName qName) {
		this.qName = qName;
	}

	/**
	 * Returns the default qname as string with either the pre-populated
	 * prefixes or ns&lt;UriID&gt;. e.g.
	 * <p>
	 * 0, "" &rarr; ""
	 * </p>
	 * <p>
	 * 1, "http://www.w3.org/XML/1998/namespace"" &rarr; "xml"
	 * </p>
	 * <p>
	 * 2, "http://www.w3.org/2001/XMLSchema-instance" &rarr; "xsi"
	 * </p>
	 * <p>
	 * 3, "..." &rarr; ns3
	 * </p>
	 * <p>
	 * 4, "..." &rarr; ns4
	 * </p>
	 * 
	 * @return qname as String
	 */
	public String getDefaultQNameAsString() {
		return defaultQNameAsString;
	}
	
	public void setDefaultQNameAsString(String defaultQNameAsString) {
		this.defaultQNameAsString = defaultQNameAsString;
	}
	
	public String getDefaultPrefix() {
		return defaultPrefix;
	}
	
	public void setDefaultPrefix(String defaultPrefix) {
		this.defaultPrefix = defaultPrefix;
	}

	public int getLocalNameID() {
		return localNameID;
	}
	
	public void setLocalNameID(int localNameID) {
		this.localNameID = localNameID;
	}

	public String getLocalName() {
		return qName.getLocalPart();
	}
	
	public void setGlobalStartElement(StartElement grammarGlobalElement) {
		this.grammarGlobalElement = grammarGlobalElement;
	}
	
	public StartElement getGlobalStartElement() {
		return grammarGlobalElement;
	}

	public void setGlobalAttribute(Attribute grammarGlobalAttribute) {
		this.grammarGlobalAttribute = grammarGlobalAttribute;
	}

	public Attribute getGlobalAttribute() {
		return grammarGlobalAttribute;
	}
	
	public void setTypeGrammar(SchemaInformedFirstStartTagGrammar typeGrammar) {
		this.typeGrammar = typeGrammar;
	}

	// null if none
	public SchemaInformedFirstStartTagGrammar getTypeGrammar() {
		return this.typeGrammar;
	}

	public int getNamespaceUriID() {
		return this.namespaceUriID;
	}
	
	public void setNamespaceUriID(int namespaceUriID) {
		this.namespaceUriID = namespaceUriID;
		
		switch (namespaceUriID) {
		case 0:
			// "" [empty string]
			setDefaultPrefix("");
			setDefaultQNameAsString(this.qName.getLocalPart());
			break;
		case 1:
			setDefaultPrefix("xml");
			setDefaultQNameAsString(defaultPrefix + ":" + this.qName.getLocalPart());
			break;
		case 2:
			setDefaultPrefix("xsi");
			setDefaultQNameAsString(defaultPrefix + ":" + this.qName.getLocalPart());
			break;
		default:
			setDefaultPrefix("ns" + namespaceUriID);
			setDefaultQNameAsString(defaultPrefix + ":" + this.qName.getLocalPart());
		}
	}


	public String getNamespaceUri() {
		return this.qName.getNamespaceURI();
	}

	protected int compareTo(String localName) {
		return this.getQName().getLocalPart().compareTo(localName);
	}

	public String toString() {
		return "{" + namespaceUriID + "}" + localNameID + ","
				+ this.getLocalName();
	}

//	public void setSimpleBaseType(QNameContext simpleBaseType) {
//		this.simpleBaseType = simpleBaseType;
//	}
//	
//	public QNameContext getSimpleBaseType() {
//		return this.simpleBaseType;
//	}
	
	@Override
	public final boolean equals(Object o) {
		if (o instanceof QNameContext) {
			QNameContext other = (QNameContext) o;
			 return (other.localNameID == this.localNameID && other
					.namespaceUriID == this.namespaceUriID);
			// return (other.qNameID == this.qNameID);
		}
		return false;
	}

	@Override
	public final int hashCode() {
		return namespaceUriID ^ localNameID;
		// return qNameID;
	}

}
