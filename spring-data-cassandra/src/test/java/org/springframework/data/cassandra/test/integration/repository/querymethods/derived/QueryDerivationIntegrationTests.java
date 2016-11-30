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
package org.springframework.data.cassandra.test.integration.repository.querymethods.derived;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assume.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.SpringVersion;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;
import org.springframework.data.cassandra.test.integration.repository.querymethods.declared.Address;
import org.springframework.data.cassandra.test.integration.repository.querymethods.declared.Person;
import org.springframework.data.cassandra.test.integration.repository.querymethods.derived.PersonRepository.NumberOfChildren;
import org.springframework.data.cassandra.test.integration.repository.querymethods.derived.PersonRepository.PersonDto;
import org.springframework.data.cassandra.test.integration.repository.querymethods.derived.PersonRepository.PersonProjection;
import org.springframework.data.cassandra.test.integration.support.AbstractSpringDataEmbeddedCassandraIntegrationTest;
import org.springframework.data.cassandra.test.integration.support.CassandraVersion;
import org.springframework.data.cassandra.test.integration.support.IntegrationTestConfig;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Version;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.datastax.driver.core.Session;

/**
 * Integration tests for query derivation through {@link PersonRepository}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@SuppressWarnings("all")
public class QueryDerivationIntegrationTests extends AbstractSpringDataEmbeddedCassandraIntegrationTest {

	@Configuration
	@EnableCassandraRepositories
	public static class Config extends IntegrationTestConfig {

		@Override
		public String[] getEntityBasePackages() {
			return new String[] { Person.class.getPackage().getName() };
		}

		@Override
		public SchemaAction getSchemaAction() {
			return SchemaAction.RECREATE_DROP_UNUSED;
		}

	}

	@Autowired CassandraOperations template;
	@Autowired Session session;
	@Autowired PersonRepository personRepository;

	private Person walter;
	private Person skyler;
	private Person flynn;

	@Before
	public void before() {
		deleteAllEntities();

		Person person = new Person("Walter", "White");
		person.setNumberOfChildren(2);

		person.setMainAddress(new Address("Albuquerque", "USA"));
		person.setAlternativeAddresses(Arrays.asList(new Address("Albuquerque", "USA"), new Address("New Hampshire", "USA"),
				new Address("Grocery Store", "Mexico")));

		walter = personRepository.save(person);
		skyler = personRepository.save(new Person("Skyler", "White"));
		flynn = personRepository.save(new Person("Flynn (Walter Jr.)", "White"));
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-7">DATACASS-7</a>
	 */
	@Test
	public void shouldFindByLastname() {

		List<Person> result = personRepository.findByLastname("White");

		assertThat(result).contains(walter, skyler, flynn);
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-7">DATACASS-7</a>
	 */
	@Test
	public void shouldFindByLastnameAndDynamicSort() {

		List<Person> result = personRepository.findByLastname("White", new Sort("firstname"));

		assertThat(result).contains(flynn, skyler, walter);
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-7">DATACASS-7</a>
	 */
	@Test
	public void shouldFindByLastnameWithOrdering() {

		List<Person> result = personRepository.findByLastnameOrderByFirstnameAsc("White");

		assertThat(result).contains(flynn, skyler, walter);
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-7">DATACASS-7</a>
	 */
	@Test
	public void shouldFindByFirstnameAndLastname() {

		Person result = personRepository.findByFirstnameAndLastname("Walter", "White");

		assertThat(result).isEqualTo(walter);
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-172">DATACASS-172</a>
	 */
	@Test
	public void shouldFindByMappedUdt() throws InterruptedException {

		template.getCqlOperations().execute("CREATE INDEX IF NOT EXISTS person_main_address ON person (mainaddress);");

		// Give Cassandra some time to build the index
		Thread.sleep(500);

		Person result = personRepository.findByMainAddress(walter.getMainAddress());

		assertThat(result).isEqualTo(walter);
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-172">DATACASS-172</a>
	 */
	@Test
	public void shouldFindByMappedUdtStringQuery() throws InterruptedException {

		template.getCqlOperations().execute("CREATE INDEX IF NOT EXISTS person_main_address ON person (mainaddress);");

		// Give Cassandra some time to build the index
		Thread.sleep(500);

		Person result = personRepository.findByAddress(walter.getMainAddress());

		assertThat(result).isEqualTo(walter);
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-7">DATACASS-7</a>
	 */
	@Test
	public void executesCollectionQueryWithProjection() {

		Collection<PersonProjection> collection = personRepository.findPersonProjectedBy();

		assertThat(collection).hasSize(3).extracting("firstname").contains(
				flynn.getFirstname(), skyler.getFirstname(), walter.getFirstname());
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-359">DATACASS-359</a>
	 */
	@Test
	public void executesCollectionQueryWithDtoProjection() {

		Collection<PersonDto> collection = personRepository.findPersonDtoBy();

		assertThat(collection).hasSize(3).extracting("firstname").contains(
				flynn.getFirstname(), skyler.getFirstname(), walter.getFirstname());
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-359">DATACASS-359</a>
	 */
	@Test
	public void executesCollectionQueryWithDtoDynamicallyProjected() throws Exception {

		template.getCqlOperations().execute(
				"CREATE CUSTOM INDEX IF NOT EXISTS fn_starts_with ON person (nickname) USING 'org.apache.cassandra.index.sasi.SASIIndex';");

		// Give Cassandra some time to build the index
		Thread.sleep(500);

		walter.setNickname("Heisenberg");
		personRepository.save(walter);

		PersonDto heisenberg = personRepository.findDtoByNicknameStartsWith("Heisen", PersonDto.class);

		assertThat(heisenberg.firstname).isEqualTo("Walter");
		assertThat(heisenberg.lastname).isEqualTo("White");
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-7">DATACASS-7</a>
	 */
	@Test
	public void shouldFindByNumberOfChildren() throws Exception {

		assumeTrue(Version.parse(SpringVersion.getVersion()).isGreaterThanOrEqualTo(Version.parse("4.3")));

		template.getCqlOperations()
				.execute("CREATE INDEX IF NOT EXISTS person_number_of_children ON person (numberofchildren);");

		// Give Cassandra some time to build the index
		Thread.sleep(500);

		Person result = personRepository.findByNumberOfChildren(NumberOfChildren.TWO);

		assertThat(result).isEqualTo(walter);
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-7">DATACASS-7</a>
	 */
	@Test
	public void shouldFindByLocalDate() throws InterruptedException {

		template.getCqlOperations().execute("CREATE INDEX IF NOT EXISTS person_created_date ON person (createddate);");

		// Give Cassandra some time to build the index
		Thread.sleep(500);

		walter.setCreatedDate(LocalDate.now());
		personRepository.save(walter);

		Person result = personRepository.findByCreatedDate(walter.getCreatedDate());

		assertThat(result).isEqualTo(walter);
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-7">DATACASS-7</a>
	 */
	@Test
	public void shouldUseQueryOverride() {

		Person otherWalter = new Person("Walter", "Black");

		personRepository.save(otherWalter);

		List<Person> result = personRepository.findByFirstname("Walter");

		assertThat(result).hasSize(1);
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-7">DATACASS-7</a>
	 */
	@Test
	public void shouldUseStartsWithQuery() throws InterruptedException {

		assumeTrue(CassandraVersion.get(session).isGreaterThanOrEqualTo(Version.parse("3.4")));

		template.getCqlOperations().execute(
				"CREATE CUSTOM INDEX IF NOT EXISTS fn_starts_with ON person (nickname) USING 'org.apache.cassandra.index.sasi.SASIIndex';");

		// Give Cassandra some time to build the index
		Thread.sleep(500);

		walter.setNickname("Heisenberg");
		personRepository.save(walter);

		assertThat(personRepository.findByNicknameStartsWith("Heis")).isEqualTo(walter);
	}

	/**
	 * @see <a href="https://jira.spring.io/browse/DATACASS-7">DATACASS-7</a>
	 */
	@Test
	public void shouldUseContainsQuery() throws InterruptedException {

		assumeTrue(CassandraVersion.get(session).isGreaterThanOrEqualTo(Version.parse("3.4")));

		template.getCqlOperations().execute(
				"CREATE CUSTOM INDEX IF NOT EXISTS fn_contains ON person (nickname) USING 'org.apache.cassandra.index.sasi.SASIIndex'\n"
						+ "WITH OPTIONS = { 'mode': 'CONTAINS' };");

		// Give Cassandra some time to build the index
		Thread.sleep(500);

		walter.setNickname("Heisenberg");
		personRepository.save(walter);

		assertThat(personRepository.findByNicknameContains("eisenber")).isEqualTo(walter);
	}
}
