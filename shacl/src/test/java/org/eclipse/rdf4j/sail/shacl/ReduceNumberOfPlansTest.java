/*******************************************************************************
 * Copyright (c) 2018 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.sail.shacl;

import static junit.framework.TestCase.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.shacl.planNodes.PlanNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Håvard Ottestad
 */
public class ReduceNumberOfPlansTest {

	private SailRepository shaclRepo;

	@Before
	public void setup() {
		shaclRepo = TestUtils.getShaclRepository("reduceNumberOfPlansTest/shacl.ttl");
	}

	@After
	public void teardown() {
		shaclRepo.shutDown();
	}

	@Test
	public void testAddingTypeStatement() {
		try (SailRepositoryConnection connection = shaclRepo.getConnection()) {

			connection.begin();

			ShaclSailConnection sailConnection = (ShaclSailConnection)connection.getSailConnection();

			sailConnection.fillAddedAndRemovedStatementRepositories();
			List<PlanNode> collect = sailConnection.getShaclSail().getShapes(sailConnection).stream().flatMap(
					shape -> shape.generatePlans(sailConnection, shape).stream()).collect(
							Collectors.toList());

			assertEquals(0, collect.size());

			IRI person1 = TestUtils.Ex.createIri();
			connection.add(person1, RDF.TYPE, TestUtils.Ex.Person);
			sailConnection.fillAddedAndRemovedStatementRepositories();

			List<PlanNode> collect2 = sailConnection.getShaclSail().getShapes(
					sailConnection).stream().flatMap(
							shape -> shape.generatePlans(sailConnection, shape).stream()).collect(
									Collectors.toList());

			assertEquals(2, collect2.size());
			ValueFactory vf = connection.getValueFactory();
			connection.add(person1, TestUtils.Ex.ssn, vf.createLiteral("a"));
			connection.add(person1, TestUtils.Ex.ssn, vf.createLiteral("b"));
			connection.add(person1, TestUtils.Ex.name, vf.createLiteral("c"));

			connection.commit();

		}

	}

	@Test
	public void testRemovingPredicate() {

		try (SailRepositoryConnection connection = shaclRepo.getConnection()) {

			connection.begin();

			ShaclSailConnection sailConnection = (ShaclSailConnection)connection.getSailConnection();

			IRI person1 = TestUtils.Ex.createIri();

			ValueFactory vf = connection.getValueFactory();
			connection.add(person1, RDF.TYPE, TestUtils.Ex.Person);
			connection.add(person1, TestUtils.Ex.ssn, vf.createLiteral("a"));
			connection.add(person1, TestUtils.Ex.ssn, vf.createLiteral("b"));
			connection.add(person1, TestUtils.Ex.name, vf.createLiteral("c"));

			connection.commit();

			connection.begin();

			connection.remove(person1, TestUtils.Ex.ssn, vf.createLiteral("b"));

			sailConnection.fillAddedAndRemovedStatementRepositories();

			List<PlanNode> collect1 = sailConnection.getShaclSail().getShapes(
					sailConnection).stream().flatMap(
							shape -> shape.generatePlans(sailConnection, shape).stream()).collect(
									Collectors.toList());
			assertEquals(1, collect1.size());

			connection.remove(person1, TestUtils.Ex.ssn, vf.createLiteral("a"));

			sailConnection.fillAddedAndRemovedStatementRepositories();

			List<PlanNode> collect2 = sailConnection.getShaclSail().getShapes(
					sailConnection).stream().flatMap(
							shape -> shape.generatePlans(sailConnection, shape).stream()).collect(
									Collectors.toList());
			assertEquals(1, collect2.size());

			connection.remove(person1, TestUtils.Ex.name, vf.createLiteral("c"));
			sailConnection.fillAddedAndRemovedStatementRepositories();

			List<PlanNode> collect3 = sailConnection.getShaclSail().getShapes(
					sailConnection).stream().flatMap(
							shape -> shape.generatePlans(sailConnection, shape).stream()).collect(
									Collectors.toList());
			assertEquals(2, collect3.size());

			connection.rollback();

		}

	}

}
