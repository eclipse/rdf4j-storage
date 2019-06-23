/*******************************************************************************
 * Copyright (c) 2018 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.sail.shacl.planNodes;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.parser.QueryParserFactory;
import org.eclipse.rdf4j.query.parser.QueryParserRegistry;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.memory_readonly.MemoryStoreReadOnlyConnection;
import org.eclipse.rdf4j.sail.shacl.ShaclSailConnection;

import java.util.ArrayDeque;

/**
 * @author Håvard Ottestad
 *         <p>
 *         This inner join algorithm assumes the left iterator is unique for tuple[0], eg. no two tuples have the same
 *         value at index 0. The right iterator is allowed to contain duplicates.
 *         <p>
 *         External means that this plan node can join the iterator from a plan node with an external source (Repository
 *         or SailConnection) based on a query or a predicate.
 */
public class BulkedExternalInnerJoin extends AbstractBulkJoinPlanNode {

	private final SailConnection connection;
	private final PlanNode leftNode;
	private final ParsedQuery parsedQuery;
	private final boolean skipBasedOnPreviousConnection;
	private boolean printed = false;

	public BulkedExternalInnerJoin(PlanNode leftNode, SailConnection connection, String query,
			boolean skipBasedOnPreviousConnection) {
		this.leftNode = leftNode;
		QueryParserFactory queryParserFactory = QueryParserRegistry.getInstance().get(QueryLanguage.SPARQL).get();
		parsedQuery = queryParserFactory.getParser()
				.parseQuery("select * where { VALUES (?a) {}" + query + "} order by ?a", null);

		this.connection = connection;
		this.skipBasedOnPreviousConnection = skipBasedOnPreviousConnection;

	}

	@Override
	public CloseableIteration<Tuple, SailException> iterator() {
		return new CloseableIteration<Tuple, SailException>() {

			ArrayDeque<Tuple> left = new ArrayDeque<>();

			ArrayDeque<Tuple> right = new ArrayDeque<>();

			ArrayDeque<Tuple> joined = new ArrayDeque<>();

			CloseableIteration<Tuple, SailException> leftNodeIterator = leftNode.iterator();

			private void calculateNext() {

				if (!joined.isEmpty()) {
					return;
				}

				while (joined.isEmpty() && leftNodeIterator.hasNext()) {

					while (left.size() < 200 && leftNodeIterator.hasNext()) {
						left.addFirst(leftNodeIterator.next());
					}

					runQuery(left, right, connection, parsedQuery, skipBasedOnPreviousConnection);

					while (!right.isEmpty()) {

						Tuple leftPeek = left.peekLast();

						Tuple rightPeek = right.peekLast();

						if (rightPeek.line.get(0) == leftPeek.line.get(0)
								|| rightPeek.line.get(0).equals(leftPeek.line.get(0))) {
							// we have a join !
							joined.addLast(TupleHelper.join(leftPeek, rightPeek));
							right.removeLast();

							Tuple rightPeek2 = right.peekLast();

							if (rightPeek2 == null || !rightPeek2.line.get(0).equals(leftPeek.line.get(0))) {
								// no more to join from right, pop left so we don't print it again.

								left.removeLast();
							}
						} else {
							int compare = rightPeek.line.get(0)
									.stringValue()
									.compareTo(leftPeek.line.get(0).stringValue());

							if (compare < 0) {
								if (right.isEmpty()) {
									throw new IllegalStateException();
								}

								right.removeLast();

							} else {
								if (left.isEmpty()) {
									throw new IllegalStateException();
								}
								left.removeLast();

							}
						}

					}

					left.clear();
				}

			}

			@Override
			public void close() throws SailException {
				leftNodeIterator.close();
				leftNodeIterator = null;
				left = null;
				right = null;
				joined = null;
			}

			@Override
			public boolean hasNext() throws SailException {
				calculateNext();
				return !joined.isEmpty();
			}

			@Override
			public Tuple next() throws SailException {
				calculateNext();
				return joined.removeFirst();

			}

			@Override
			public void remove() throws SailException {

			}
		};
	}

	@Override
	public int depth() {
		return leftNode.depth() + 1;
	}

	@Override
	public void getPlanAsGraphvizDot(StringBuilder stringBuilder) {
		if (printed) {
			return;
		}
		printed = true;
		stringBuilder.append(getId() + " [label=\"" + StringEscapeUtils.escapeJava(this.toString()) + "\"];")
				.append("\n");
		stringBuilder.append(leftNode.getId() + " -> " + getId() + " [label=\"left\"]").append("\n");

		if (connection instanceof MemoryStoreReadOnlyConnection) {
			stringBuilder
					.append(System.identityHashCode(((MemoryStoreReadOnlyConnection) connection).getSail()) + " -> "
							+ getId() + " [label=\"right\"]")
					.append("\n");
		} else {
			stringBuilder.append(System.identityHashCode(connection) + " -> " + getId() + " [label=\"right\"]")
					.append("\n");
		}

		if (skipBasedOnPreviousConnection) {
			if (connection instanceof ShaclSailConnection) {
				NotifyingSailConnection previousStateConnection = ((ShaclSailConnection) connection)
						.getPreviousStateConnection();

				stringBuilder
						.append(System.identityHashCode(previousStateConnection) + " -> " + getId()
								+ " [label=\"skip if not present\"]")
						.append("\n");
			}
		}

		leftNode.getPlanAsGraphvizDot(stringBuilder);
	}

	@Override
	public String toString() {
		return "BulkedExternalInnerJoin{" + "parsedQuery=" + parsedQuery.getSourceString() + '}';
	}

	@Override
	public String getId() {
		return System.identityHashCode(this) + "";
	}

	@Override
	public IteratorData getIteratorDataType() {
		return leftNode.getIteratorDataType();
	}
}
