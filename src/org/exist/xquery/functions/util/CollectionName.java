/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
public class CollectionName extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("collection-name", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the name of the collection to which the passed node belongs.",
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));
	
	public CollectionName(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		Item item = args[0].itemAt(0);
		if(item.getType() == Type.JAVA_OBJECT) {
			Object o = ((JavaObjectValue) item).getObject();
            if (!(o instanceof Collection))
                throw new XPathException(getASTNode(), "Passed Java object should be of type org.xmldb.api.base.Collection");
            Collection collection = (Collection)o;
            try {
				return new StringValue(collection.getName());
			} catch (XMLDBException e) {
				throw new XPathException(getASTNode(), "Failed to retrieve collection name", e);
			}
		} else if(Type.subTypeOf(item.getType(), Type.NODE)) {
			NodeValue node = (NodeValue) item;
			if(node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
				NodeProxy p = (NodeProxy) node;
				return new StringValue(p.getDocument().getCollection().getName());	
			}
		} else
			throw new XPathException(getASTNode(), "First argument to util:collection-name should be either " +
				"a Java object of type org.xmldb.api.base.Collection or a node; got: " + 
				Type.getTypeName(item.getType()));
		return Sequence.EMPTY_SEQUENCE;
	}

}
