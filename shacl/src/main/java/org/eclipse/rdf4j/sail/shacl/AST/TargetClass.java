/*******************************************************************************
 * Copyright (c) 2016 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.sail.shacl.AST;

import org.eclipse.rdf4j.IsolationLevels;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.shacl.ShaclSailConnection;
import org.eclipse.rdf4j.sail.shacl.plan.PlanNode;
import org.eclipse.rdf4j.sail.shacl.planNodes.ExternalTypeFilterNode;
import org.eclipse.rdf4j.sail.shacl.planNodes.Select;

import java.util.stream.Stream;

/**
 * @author Heshan Jayasinghe
 */
public class TargetClass extends Shape implements QueryGenerator{

	Resource targetClass;

	TargetClass(Resource id, SailRepositoryConnection connection) {
		super(id, connection);

		try (Stream<Statement> stream = Iterations.stream(connection.getStatements(id, SHACL.TARGET_CLASS, null))) {
			targetClass = stream.map(Statement::getObject).map(v -> (Resource) v).findAny().get();
		}

	}

	@Override
	public Select getPlan(ShaclSailConnection shaclSailConnection, Shape shape) {
		return new Select(shaclSailConnection, getQuery());
	}

	@Override
	public PlanNode getPlanAddedStatements(ShaclSailConnection shaclSailConnection, Shape shape) {
		return new Select(shaclSailConnection.addedStatements, getQuery());

	}

	@Override
	public PlanNode getPlanRemovedStatements(ShaclSailConnection shaclSailConnection, Shape shape) {
		return new Select(shaclSailConnection.removedStatements, getQuery());
	}

	@Override
	public boolean requiresEvalutation(Repository addedStatements, Repository removedStatements) {
		boolean requiresEvalutation;
		try (RepositoryConnection addedStatementsConnection = addedStatements.getConnection()) {
			requiresEvalutation = addedStatementsConnection.hasStatement(null, RDF.TYPE, targetClass, false);
		}

		return super.requiresEvalutation(addedStatements, removedStatements) || requiresEvalutation;
	}

	@Override
	public String getQuery() {
		return "?a ?b ?c. ?a a <"+targetClass+">";
	}

	public PlanNode getTypeFilterPlan(ShaclSailConnection shaclSailConnection, PlanNode parent) {
		return new ExternalTypeFilterNode(shaclSailConnection, targetClass, parent);
	}
}