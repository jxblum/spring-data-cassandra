/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.cassandra.test.integration.core;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cassandra.core.cql.CqlIdentifier;
import org.springframework.cassandra.core.keyspace.DropTableSpecification;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.convert.CassandraConverter;
import org.springframework.data.cassandra.core.CassandraAdminTemplate;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.test.integration.simpletons.Book;
import org.springframework.data.cassandra.test.integration.support.AbstractSpringDataEmbeddedCassandraIntegrationTest;
import org.springframework.data.cassandra.test.integration.support.IntegrationTestConfig;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TableMetadata;

/**
 * Integration tests for {@link CassandraAdminTemplate}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class CassandraAdminTemplateIntegrationTests extends AbstractSpringDataEmbeddedCassandraIntegrationTest {

	@Configuration
	public static class Config extends IntegrationTestConfig {

		@Override
		public String[] getEntityBasePackages() {
			return new String[] { Book.class.getPackage().getName() };
		}
	}

	@Autowired private CassandraAdminTemplate cassandraAdminTemplate;

	@Before
	public void before() {
		KeyspaceMetadata keyspace = getKeyspaceMetadata();
		Collection<TableMetadata> tables = keyspace.getTables();
		for (TableMetadata table : tables) {
			cassandraAdminTemplate.execute(DropTableSpecification.dropTable(table.getName()));
		}
	}

	private KeyspaceMetadata getKeyspaceMetadata() {
		Metadata metadata = getSession().getCluster().getMetadata();
		return metadata.getKeyspace(getSession().getLoggedKeyspace());
	}

	private Session getSession() {
		return cassandraAdminTemplate.getSession();
	}

	/**
	 *
	 * @see DATACASS-173
	 */
	@Test
	public void testCreateTables() throws Exception {

		assertThat(getKeyspaceMetadata().getTables().size(), is(0));

		cassandraAdminTemplate.createTable(true, CqlIdentifier.cqlId("book"), Book.class, null);
		assertThat(getKeyspaceMetadata().getTables().size(), is(1));

		cassandraAdminTemplate.createTable(true, CqlIdentifier.cqlId("book"), Book.class, null);
		assertThat(getKeyspaceMetadata().getTables().size(), is(1));
	}

	@Test
	public void testDropTable() throws Exception {

		cassandraAdminTemplate.createTable(true, CqlIdentifier.cqlId("book"), Book.class, null);
		assertThat(getKeyspaceMetadata().getTables().size(), is(1));

		cassandraAdminTemplate.dropTable(Book.class);
		assertThat(getKeyspaceMetadata().getTables().size(), is(0));

		cassandraAdminTemplate.createTable(true, CqlIdentifier.cqlId("book"), Book.class, null);
		cassandraAdminTemplate.dropTable(CqlIdentifier.cqlId("book"));

		assertThat(getKeyspaceMetadata().getTables().size(), is(0));
	}
}
