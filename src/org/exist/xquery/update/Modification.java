/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.update;

import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.DocumentTriggers;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.StoredNode;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.persistent.NodeHandle;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.xquery.*;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author wolf
 *
 */
public abstract class Modification extends AbstractExpression
{

    protected final static Logger LOG = LogManager.getLogger(Modification.class);

    protected final Expression select;
    protected final Expression value;

    protected DocumentSet lockedDocuments = null;
    protected MutableDocumentSet modifiedDocuments = new DefaultDocumentSet();
    protected Int2ObjectHashMap<DocumentTrigger> triggers;

    /**
     * @param context
     */
    public Modification(XQueryContext context, Expression select, Expression value) {
        super(context);
        this.select = select;
        this.value = value;
        this.triggers = new Int2ObjectHashMap<>(10);
    }

    public int getCardinality() {
        return Cardinality.EMPTY;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#returnsType()
     */
    public int returnsType() {
        return Type.EMPTY;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#resetState()
     */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        select.resetState(postOptimization);
        if (value != null) {
            value.resetState(postOptimization);
        }
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        select.accept(visitor);
        if (value != null) {
            value.accept(visitor);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression, int)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        contextInfo.addFlag(IN_UPDATE);
        select.analyze(contextInfo);
        if (value != null) {
            value.analyze(contextInfo);
        }
    }

    /**
     * Acquire a lock on all documents processed by this modification.
     * We have to avoid that node positions change during the
     * operation.
     *
     * @param nodes
     *
     * @throws LockException
     * @throws TriggerException
     */
    protected StoredNode[] selectAndLock(Txn transaction, Sequence nodes) throws LockException, PermissionDeniedException,
        XPathException, TriggerException {
        final java.util.concurrent.locks.Lock globalLock = context.getBroker().getBrokerPool().getGlobalUpdateLock();
        globalLock.lock();
        try {
            lockedDocuments = nodes.getDocumentSet();

            // acquire a lock on all documents
            // we have to avoid that node positions change
            // during the modification
            lockedDocuments.lock(context.getBroker(), true);

            final StoredNode ql[] = new StoredNode[nodes.getItemCount()];
            for (int i = 0; i < ql.length; i++) {
                final Item item = nodes.itemAt(i);
                if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                    throw new XPathException(this, "XQuery update expressions can only be applied to nodes. Got: " +
                        item.getStringValue());
                }
                final NodeValue nv = (NodeValue)item;
                if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                    throw new XPathException(this, "XQuery update expressions can not be applied to in-memory nodes.");
                }
                final Node n = nv.getNode();
                if (n.getNodeType() == Node.DOCUMENT_NODE) {
                    throw new XPathException(this, "Updating the document object is not allowed.");
                }
                ql[i] = (StoredNode) n;
                final DocumentImpl doc = ql[i].getOwnerDocument();
                //prepare Trigger
                prepareTrigger(transaction, doc);
            }
            return ql;
        } finally {
            globalLock.unlock();
        }
    }

    protected Sequence deepCopy(Sequence inSeq) throws XPathException {
        context.pushDocumentContext();
        final MemTreeBuilder builder = context.getDocumentBuilder();
        final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
        final Serializer serializer = context.getBroker().getSerializer();
        serializer.setReceiver(receiver);

        try {
            final Sequence out = new ValueSequence();
            for (final SequenceIterator i = inSeq.iterate(); i.hasNext(); ) {
                Item item = i.nextItem();
                if (item.getType() == Type.DOCUMENT) {
                    if (((NodeValue)item).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        final NodeHandle root = (NodeHandle) ((NodeProxy)item).getOwnerDocument().getDocumentElement();
                        item = new NodeProxy(root);
                    } else {
                        item = (Item)((NodeValue) item).getOwnerDocument().getDocumentElement();
                    }
                }
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    if (((NodeValue)item).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        final int last = builder.getDocument().getLastNode();
                        final NodeProxy p = (NodeProxy) item;
                        serializer.toReceiver(p, false, false);
                        if (p.getNodeType() == Node.ATTRIBUTE_NODE)
                            {item = builder.getDocument().getLastAttr();}
                        else
                            {item = builder.getDocument().getNode(last + 1);}
                    } else {
                        ((org.exist.dom.memtree.NodeImpl)item).deepCopy();
                    }
                }
                out.add(item);
            }
            return out;
        } catch(final SAXException | DOMException e) {
            throw new XPathException(this, e.getMessage(), e);
        } finally {
            context.popDocumentContext();
        }
    }

    protected void finishTriggers(Txn transaction) throws TriggerException {
        final Iterator<DocumentImpl> iterator = modifiedDocuments.getDocumentIterator();

        while(iterator.hasNext()) {
            final DocumentImpl doc = iterator.next();
            context.addModifiedDoc(doc);
            finishTrigger(transaction, doc);
        }
        
        triggers.clear();
    }

    /**
     * Release all acquired document locks.
     */
    protected void unlockDocuments()
    {
        if(lockedDocuments == null) {
            return;
        }

        modifiedDocuments.clear();

        //unlock documents
        lockedDocuments.unlock();
        lockedDocuments = null;
    }

    public static void checkFragmentation(XQueryContext context, DocumentSet docs) throws EXistException {
        int fragmentationLimit = -1;
        final Object property = context.getBroker().getBrokerPool().getConfiguration().getProperty(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR);
        if (property != null) {
            fragmentationLimit = (Integer) property;
        }
        checkFragmentation(context, docs, fragmentationLimit);
    }

    /**
     * Check if any of the modified documents needs defragmentation.
     *
     * Defragmentation will take place if the number of split pages in the
     * document exceeds the limit defined in the configuration file.
     *
     * @param docs
     */
    public static void checkFragmentation(XQueryContext context, DocumentSet docs, int splitCount) throws EXistException {
        final DBBroker broker = context.getBroker();
        
        //if there is no batch update transaction, start a new individual transaction
        try (final Txn transaction = broker.getBrokerPool().getTransactionManager().beginTransaction()) {
            for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
                final DocumentImpl next = i.next();
                if(next.getMetadata().getSplitCount() > splitCount) {
                    Lock lock = next.getUpdateLock();
                    try {
                        lock.acquire(LockMode.WRITE_LOCK);
                        broker.defragXMLResource(transaction, next);
                    } finally {
                        lock.release(LockMode.WRITE_LOCK);
                    }}
                broker.checkXMLResourceConsistency(next);
            }

            transaction.commit();
        } catch (final Exception e) {
            LOG.error(e, e);
        }
    }

    /**
     * Fires the prepare function for the UPDATE_DOCUMENT_EVENT trigger for the Document doc
     *
     * @param transaction	The transaction
     * @param doc	The document to trigger for
     *
     * @throws TriggerException
     */
    private void prepareTrigger(Txn transaction, DocumentImpl doc) throws TriggerException {

        final Collection col = doc.getCollection();
        final DBBroker broker = context.getBroker();

        final DocumentTrigger trigger = new DocumentTriggers(broker, col);

        //prepare the trigger
        trigger.beforeUpdateDocument(context.getBroker(), transaction, doc);
        triggers.put(doc.getDocId(), trigger);
    }

    /** Fires the finish function for UPDATE_DOCUMENT_EVENT for the documents trigger
     *
     * @param transaction	The transaction
     * @param doc	The document to trigger for
     *
     * @throws TriggerException
     */
    private void finishTrigger(Txn transaction, DocumentImpl doc) throws TriggerException {
        //finish the trigger
        final DocumentTrigger trigger = triggers.get(doc.getDocId());
        if(trigger != null) {
            trigger.afterUpdateDocument(context.getBroker(), transaction, doc);
        }
    }

    /**
     * Gets the Transaction to use for the update (can be batch or individual)
     *
     * @return The transaction
     */
    protected Txn getTransaction() {
        return context.getBroker().getBrokerPool().getTransactionManager().beginTransaction();
    }
}
