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

package com.siemens.ct.exi.core.grammars.grammar;

/**
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Richard.Kuntschke@siemens.com
 * 
 */

/*
 * first rule is different (namespace declaration, xsi:type and xsi:nil)
 */
public interface SchemaInformedFirstStartTagGrammar extends
		SchemaInformedStartTagGrammar {

	// /*
	// * returns type-name or null if anonymous type
	// */
	// public QName getTypeName();
	//
	// public void setTypeName(QName typeName);

	/*
	 * sets whether type is castable
	 */
	public void setTypeCastable(boolean hasNamedSubtypes);

	public boolean isTypeCastable();

	/*
	 * sets whether element is nillable
	 */
	public void setNillable(boolean nillable);

	public boolean isNillable();

	public void setTypeEmpty(SchemaInformedFirstStartTagGrammar typeEmpty);

	public SchemaInformedFirstStartTagGrammar getTypeEmpty();

}
