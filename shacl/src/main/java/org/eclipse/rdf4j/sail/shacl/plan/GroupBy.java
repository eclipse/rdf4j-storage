/*******************************************************************************
 * Copyright (c) 2016 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.sail.shacl.plan;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.sail.SailException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Heshan Jayasinghe
 */
public class GroupBy implements GroupPlanNode {

	PlanNode leftjoinnode;

	HashMap<Value, List<List<Value>>> hashMap = new LinkedHashMap<>();

	int skipTupleItems;

	public GroupBy(PlanNode outerLeftJoin, int skipTupleItems) {
		leftjoinnode = outerLeftJoin;
		this.skipTupleItems = skipTupleItems;
	}

	@Override
	public CloseableIteration<List<Tuple>, SailException> iterator() {
		CloseableIteration<Tuple, SailException> leftJoinIterator = leftjoinnode.iterator();
		while (leftJoinIterator.hasNext()) {
			Tuple leftjointuple = leftJoinIterator.next();
			boolean status = true;
			List<List<Value>> values1 = hashMap.computeIfAbsent(leftjointuple.line.get(0),
					k -> new ArrayList<List<Value>>());
			List<Value> sublist = leftjointuple.line.subList(skipTupleItems - 1,
					leftjointuple.line.size() - 1);
			if (sublist.size() > 0) {
				values1.add(sublist);
			}
		}

		return new CloseableIteration<List<Tuple>, SailException>() {

			Iterator<Map.Entry<Value, List<List<Value>>>> hashmapiterator = hashMap.entrySet().iterator();

			@Override
			public void close()
					throws SailException
			{

			}

			@Override
			public boolean hasNext()
					throws SailException
			{
				return hashmapiterator.hasNext();
			}

			@Override
			public List<Tuple> next()
					throws SailException
			{
				return hashmapiterator.next().getValue().stream().map(Tuple::new).collect(
						Collectors.toList());
			}

			@Override
			public void remove()
					throws SailException
			{
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public boolean validate() {
		return false;
	}

	@Override
	public int getCardinalityMin() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getCardinalityMax() {
		throw new UnsupportedOperationException();
	}
}