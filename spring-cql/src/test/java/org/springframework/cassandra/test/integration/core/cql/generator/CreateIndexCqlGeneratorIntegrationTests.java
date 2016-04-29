/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cassandra.test.integration.core.cql.generator;

import static org.springframework.cassandra.test.integration.core.cql.generator.CqlIndexSpecificationAssertions.assertIndex;

import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.Test;
import org.springframework.cassandra.test.integration.AbstractKeyspaceCreatingIntegrationTest;
import org.springframework.cassandra.core.cql.generator.CreateIndexCqlGeneratorUnitTests.BasicTest;
import org.springframework.cassandra.core.cql.generator.CreateIndexCqlGeneratorUnitTests.CreateIndexTest;

/**
 * Integration tests that reuse unit tests.
 *
 * @author Matthew T. Adams
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public class CreateIndexCqlGeneratorIntegrationTests {

	/**
	 * Integration test base class that knows how to do everything except instantiate the concrete unit test type T.
	 *
	 * @author Matthew T. Adams
	 * @param <T> The concrete unit test class to which this integration test corresponds.
	 */
	public static abstract class Base<T extends CreateIndexTest> extends AbstractKeyspaceCreatingIntegrationTest {
		T unit;

		public abstract T unit();

		@Test
		public void test() {
			unit = unit();
			unit.prepare();

			session.execute(unit.cql);

			assertIndex(unit.specification, keyspace, session);
		}
	}

	public static class BasicIntegrationTest extends Base<BasicTest> {

		public BasicIntegrationTest() {

			cassandraRule.before(new ClassPathCQLDataSet(
				"integration/cql/generator/CreateIndexCqlGeneratorIntegrationTests-BasicTest.cql", this.keyspace));
		}

		@Override
		public BasicTest unit() {
			return new BasicTest();
		}

	}

}
