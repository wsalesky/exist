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
 */
package org.exist.xquery.functions.xmldb;

import java.util.Date;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xmldb.CollectionImpl;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.DateTimeValue;


import org.exist.security.User;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Resource;
import org.exist.xmldb.EXistResource;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
public class XMLDBCreated extends XMLDBAbstractCollectionManipulator {

	public final static FunctionSignature signatures[] = {
        new FunctionSignature(
			new QName("created", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the creation date",
			new SequenceType[] {
                new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE)
        ),
		new FunctionSignature(
			new QName("created", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the creation date",
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE)
        )
    };
	
	public XMLDBCreated(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
		throws XPathException {
		try {
			if(getSignature().getArgumentCount() == 1) {
                Date created = ((CollectionImpl)collection).getCreationTime();
                return new DateTimeValue(created.getTime());
			} else {
                Resource resource = collection.getResource(args[1].getStringValue());
                Date created = ((EXistResource)resource).getCreationTime();
                return new DateTimeValue(created.getTime());
            }
		} catch(XMLDBException e) {
			throw new XPathException(getASTNode(), "Failed to retrieve creation date: " + e.getMessage(), e);
		}
	}

}
