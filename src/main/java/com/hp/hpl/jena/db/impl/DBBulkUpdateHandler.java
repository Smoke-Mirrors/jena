/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.hpl.jena.db.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.hp.hpl.jena.util.IteratorCollection;
import com.hp.hpl.jena.util.iterator.NiceIterator;

import com.hp.hpl.jena.db.GraphRDB;
import com.hp.hpl.jena.graph.*;
import com.hp.hpl.jena.graph.impl.SimpleBulkUpdateHandler;

/**
    An implementation of the bulk update interface. Updated by kers to permit event
    handling for bulk updates.
    
 	@author csayers based on SimpleBulkUpdateHandler by kers
 	@version $Revision: 1.1 $
*/

public class DBBulkUpdateHandler implements BulkUpdateHandler {
	private GraphRDB graph;
    private GraphEventManager manager;
    
	protected static int CHUNK_SIZE = 50;

	public DBBulkUpdateHandler(GraphRDB graph) {
		this.graph = graph;
        this.manager = graph.getEventManager();
	}

    /**
        add a list of triples to the graph; the add is done as a list with notify off,
        and then the array-notify invoked.
    */
	@Override
    public void add(Triple[] triples) {
		add( Arrays.asList(triples), false );
        manager.notifyAddArray( graph, triples );
	}

	@Override
    public void add( List<Triple> triples ) 
        { add( triples, true ); }
        
    /**
        add a list of triples to the graph, notifying only if requested.
    */
    protected void add( List<Triple> triples, boolean notify ) {
		graph.add(triples);
        if (notify) manager.notifyAddList( graph, triples );
	}

    /**
        Add the [elements of the] iterator to the graph. Complications arise because
        we wish to avoid duplicating the iterator if there are no listeners; otherwise
        we have to read the entire iterator into a list and use add(List) with notification
        turned off.
     	@see com.hp.hpl.jena.graph.BulkUpdateHandler#add(java.util.Iterator)
     */
	@Override
    public void add(Iterator<Triple> it) 
        { 
        if (manager.listening())
            {
            List<Triple> L = IteratorCollection.iteratorToList( it );
            add( L, false );
            manager.notifyAddIterator( graph, L );    
            }
        else
            addIterator( it ); 
        }
    
    protected void addIterator( Iterator<Triple> it )
    {
		ArrayList<Triple> list = new ArrayList<Triple>(CHUNK_SIZE);
		while (it.hasNext()) {
			while (it.hasNext() && list.size() < CHUNK_SIZE) {
				list.add( it.next() );
			}
			graph.add(list);
			list.clear();
		}
    }
        
    @Override
    public void add( Graph g )
        { add( g, false ); }
        
    @Override
    public void add( Graph g, boolean withReifications ) {
        Iterator<Triple> triplesToAdd = GraphUtil.findAll( g );
		try { addIterator( triplesToAdd ); } finally { NiceIterator.close(triplesToAdd); }
        if (withReifications) SimpleBulkUpdateHandler.addReifications( graph, g );
        manager.notifyAddGraph( graph, g );
	}

    /**
        remove a list of triples from the graph; the remove is done as a list with notify off,
        and then the array-notify invoked.
    */
	@Override
    public void delete( Triple[] triples ) {
		delete( Arrays.asList(triples), false );
        manager.notifyDeleteArray( graph, triples );
	}

    @Override
    public void delete( List<Triple> triples )
        { delete( triples, true ); }
        
    /**
        Add a list of triples to the graph, notifying only if requested.
    */
	protected void delete(List<Triple> triples, boolean notify ) {
		graph.delete( triples );
        if (notify) manager.notifyDeleteList( graph, triples );
	}
    
    /**
        Delete the [elements of the] iterator from the graph. Complications arise 
        because we wish to avoid duplicating the iterator if there are no listeners; 
        otherwise we have to read the entire iterator into a list and use delete(List) 
        with notification turned off.
        @see com.hp.hpl.jena.graph.BulkUpdateHandler#add(java.util.Iterator)
     */
    @Override
    public void delete(Iterator<Triple> it) 
        { 
        if (manager.listening())
            {
            List<Triple> L = IteratorCollection.iteratorToList( it );
            delete( L, false );
            manager.notifyDeleteIterator( graph, L );    
            }
        else
            deleteIterator( it ); 
        }
    
	protected void deleteIterator(Iterator<Triple> it) {
		ArrayList<Triple> list = new ArrayList<Triple>(CHUNK_SIZE);
		while (it.hasNext()) {
			while (it.hasNext() && list.size() < CHUNK_SIZE) {
				list.add(it.next());
			}
			graph.delete(list);
			list.clear();
		}
	}

	@Override
    public void delete(Graph g)
        { delete( g, false ); }
        
    @Override
    public void delete( Graph g, boolean withReifications ) {
        Iterator<Triple> triplesToDelete = GraphUtil.findAll( g );
		try { deleteIterator( triplesToDelete ); } finally { NiceIterator.close(triplesToDelete) ; }
        if (withReifications) SimpleBulkUpdateHandler.deleteReifications( graph, g );
        manager.notifyDeleteGraph( graph, g );
   	}
    
    @Override
    public void removeAll()
        { graph.clear();
        manager.notifyEvent( graph, GraphEvents.removeAll ); }
    
    @Override
    public void remove( Node s, Node p, Node o )
        { SimpleBulkUpdateHandler.removeAll( graph, s, p, o ); 
        manager.notifyEvent( graph, GraphEvents.remove( s, p, o ) ); }
}