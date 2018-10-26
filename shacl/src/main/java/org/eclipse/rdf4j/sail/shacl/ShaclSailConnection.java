/*******************************************************************************
 * Copyright (c) 2018 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.sail.shacl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.rdf4j.IsolationLevel;
import org.eclipse.rdf4j.IsolationLevels;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.SailConnectionListener;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.helpers.NotifyingSailConnectionWrapper;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.AST.NodeShape;
import org.eclipse.rdf4j.sail.shacl.planNodes.PlanNode;
import org.eclipse.rdf4j.sail.shacl.planNodes.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Heshan Jayasinghe
 */
public class ShaclSailConnection extends NotifyingSailConnectionWrapper {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	//	private NotifyingSailConnection previousStateConnection;

	private Repository addedStatements;

	private Repository removedStatements;

	private final ShaclSail sail;

	public Stats stats;

	private HashSet<Statement> addedStatementsSet = new HashSet<>();

	private HashSet<Statement> removedStatementsSet = new HashSet<>();

	ShaclSailConnection(ShaclSail sail, NotifyingSailConnection connection) {
		super(connection);
		this.sail = sail;

		if (sail.config.validationEnabled) {

			addConnectionListener(new SailConnectionListener() {

				@Override
				public void statementAdded(Statement statement) {
					boolean add = addedStatementsSet.add(statement);
					if (!add) {
						removedStatementsSet.remove(statement);
					}

				}

				@Override
				public void statementRemoved(Statement statement) {
					boolean add = removedStatementsSet.add(statement);
					if (!add) {
						addedStatementsSet.remove(statement);
					}
				}
			}

			);
		}
	}

	public Repository getAddedStatements() {
		return addedStatements;
	}

	public Repository getRemovedStatements() {
		return removedStatements;
	}

	@Override
	public void begin() throws SailException {
		begin(sail.getDefaultIsolationLevel());
	}

	@Override
	public void begin(IsolationLevel level)
		throws SailException
	{

		assert addedStatements == null;
		assert removedStatements == null;

		stats = new Stats();

		super.begin(level);
	}

	private SailRepository getNewMemorySail() {
		MemoryStore sail = new MemoryStore();
		sail.setDefaultIsolationLevel(IsolationLevels.NONE);
		SailRepository repository = new SailRepository(sail);
		repository.initialize();
		return repository;
	}

	@Override
	public void commit()
		throws SailException
	{
		synchronized (sail) {
			try {
				boolean valid = validate();

				if (!valid) {
					rollback();
					throw new SailException("Failed SHACL validation");
				}
				else {
					super.commit();
				}
			}
			finally {
				cleanup();
			}
		}
	}

	@Override
	public void rollback()
		throws SailException
	{
		synchronized (sail) {
			//			previousStateConnection.commit();
			cleanup();
			super.rollback();
		}
	}

	private void cleanup() {
		if (addedStatements != null) {
			addedStatements.shutDown();
			addedStatements = null;
		}
		if (removedStatements != null) {
			removedStatements.shutDown();
			removedStatements = null;
		}

		addedStatementsSet.clear();
		removedStatementsSet.clear();
		stats = null;
	}

	private boolean validate() {

		if (!sail.config.validationEnabled) {
			return true;
		}

		fillAddedAndRemovedStatementRepositories();

		boolean allValid = true;

		for (NodeShape nodeShape : sail.getShapes(this)) {
			List<PlanNode> planNodes = nodeShape.generatePlans(this, nodeShape);
			for (PlanNode planNode : planNodes) {
				try (Stream<Tuple> stream = Iterations.stream(planNode.iterator())) {
					List<Tuple> collect = stream.collect(Collectors.toList());

					boolean valid = collect.size() == 0;
					if (!valid) {
						logger.warn(
								"SHACL not valid. The following experimental debug results were produced: \n\tShape: {} \n\t\t{}",
								nodeShape.toString(),
								String.join("\n\t\t",
										collect.stream().map(
												a -> a.toString() + " -cause-> " + a.getCause()).collect(
														Collectors.toList())));
					}
					allValid = allValid && valid;
				}
			}
		}

		return allValid;
	}

	ShaclSail getShaclSail() {
		return sail;
	}

	void fillAddedAndRemovedStatementRepositories() {

		addedStatements = getNewMemorySail();
		removedStatements = getNewMemorySail();

		addedStatementsSet.forEach(stats::added);
		removedStatementsSet.forEach(stats::removed);

		try (RepositoryConnection connection = addedStatements.getConnection()) {
			connection.begin(IsolationLevels.NONE);
			addedStatementsSet.stream().filter(
					statement -> !removedStatementsSet.contains(statement)).forEach(connection::add);
			connection.commit();
		}

		try (RepositoryConnection connection = removedStatements.getConnection()) {
			connection.begin(IsolationLevels.NONE);
			removedStatementsSet.stream().filter(
					statement -> !addedStatementsSet.contains(statement)).forEach(connection::add);
			connection.commit();
		}
	}
	
	@Override
	public CloseableIteration<? extends Statement, SailException> getStatements(Resource subj, IRI pred,
			Value obj, boolean includeInferred, Resource... contexts)
		throws SailException
	{
		if (contexts.length == 0) {
			List<Resource> graphs = new ArrayList<>();
			graphs.add(null);
			List<Resource> contextIDs = QueryResults.asList(getContextIDs());
			for (Resource context: contextIDs) {
				if (!context.equals(ShaclSail.SHACL_GRAPH)) {
					graphs.add(context);
				}
			}
			return super.getStatements(subj, pred, obj, includeInferred, graphs.toArray(new Resource[] {}));
		}
		else {
			return super.getStatements(subj, pred, obj, includeInferred, contexts);
		}
		
	}
	
	@Override
	synchronized public void close()
		throws SailException
	{
		if (isActive()) {
			rollback();
		}
		super.close();
	}

	public class Stats {

		boolean hasAdded;

		boolean hasRemoved;

		public void added(Statement statement) {
			hasAdded = true;
		}

		public void removed(Statement statement) {
			hasRemoved = true;

		}

		public boolean hasAdded() {
			return hasAdded;
		}

		public boolean hasRemoved() {
			return hasRemoved;
		}
	}
}
