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
package org.springframework.data.cassandra.test.integration.repository.querymethods.declared;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.cassandra.convert.CassandraConverter;
import org.springframework.data.cassandra.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.mapping.BasicCassandraMappingContext;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.query.CassandraParametersParameterAccessor;
import org.springframework.data.cassandra.repository.query.CassandraQueryMethod;
import org.springframework.data.cassandra.repository.query.StringBasedCassandraQuery;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;

import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;

/**
 * Unit tests for {@link StringBasedCassandraQuery}.
 *
 * @author Matthew T. Adams
 * @author Oliver Gierke
 */
@RunWith(MockitoJUnitRunner.class)
public class StringBasedCassandraQueryIntegrationTests {

	@Mock CassandraOperations operations;

	RepositoryMetadata metadata;
	CassandraConverter converter;
	ProjectionFactory factory;

	@Before
	public void setUp() {

		when(operations.getConverter()).thenReturn(converter);

		this.metadata = AbstractRepositoryMetadata.getMetadata(SampleRepository.class);
		this.converter = new MappingCassandraConverter(new BasicCassandraMappingContext());
		this.factory = new SpelAwareProxyProjectionFactory();
	}

	@Test
	public void bindsSimplePropertyCorrectly() throws Exception {

		Method method = SampleRepository.class.getMethod("findByLastname", String.class);
		CassandraQueryMethod queryMethod = new CassandraQueryMethod(method, metadata, factory, converter.getMappingContext());
		StringBasedCassandraQuery cassandraQuery = new StringBasedCassandraQuery(queryMethod, operations);
		CassandraParametersParameterAccessor accesor = new CassandraParametersParameterAccessor(queryMethod, "Matthews");

		String stringQuery = cassandraQuery.createQuery(accesor);
		SimpleStatement actual = new SimpleStatement(stringQuery);

		String table = Person.class.getSimpleName().toLowerCase();
		Select expected = QueryBuilder.select().all().from(table);
		expected.setForceNoValues(true);
		expected.where(QueryBuilder.eq("lastname", "Matthews"));

		assertThat(actual.getQueryString(), is(expected.getQueryString()));
	}

	@Test
	public void bindsMultipleParametersCorrectly() throws Exception {

		Method method = SampleRepository.class.getMethod("findByLastnameAndFirstname", String.class, String.class);
		CassandraQueryMethod queryMethod = new CassandraQueryMethod(method, metadata, factory,  converter.getMappingContext());
		StringBasedCassandraQuery cassandraQuery = new StringBasedCassandraQuery(queryMethod, operations);
		CassandraParametersParameterAccessor accesor = new CassandraParametersParameterAccessor(queryMethod, "Matthews",
				"John");

		String stringQuery = cassandraQuery.createQuery(accesor);
		SimpleStatement actual = new SimpleStatement(stringQuery);

		String table = Person.class.getSimpleName().toLowerCase();
		Select expected = QueryBuilder.select().all().from(table);
		expected.setForceNoValues(true);
		expected.where(QueryBuilder.eq("lastname", "Matthews")).and(QueryBuilder.eq("firstname", "John"));

		assertThat(actual.getQueryString(), is(expected.getQueryString()));
	}

	private interface SampleRepository extends Repository<Person, String> {

		@Query("SELECT * FROM person WHERE lastname=?0;")
		Person findByLastname(String lastname);

		@Query("SELECT * FROM person WHERE lastname=?0 AND firstname=?1;")
		Person findByLastnameAndFirstname(String lastname, String firstname);
	}
}
