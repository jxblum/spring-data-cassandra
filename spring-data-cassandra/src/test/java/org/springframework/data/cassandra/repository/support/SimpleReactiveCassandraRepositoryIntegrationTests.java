/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.data.cassandra.repository.support;

import static org.assertj.core.api.Assertions.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cassandra.test.integration.AbstractKeyspaceCreatingIntegrationTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.core.ReactiveCassandraOperations;
import org.springframework.data.cassandra.domain.Person;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.data.cassandra.test.integration.support.IntegrationTestConfig;
import org.springframework.data.repository.query.DefaultEvaluationContextProvider;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Integration tests for {@link SimpleReactiveCassandraRepository}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class SimpleReactiveCassandraRepositoryIntegrationTests extends AbstractKeyspaceCreatingIntegrationTest
		implements BeanClassLoaderAware, BeanFactoryAware {

	@Configuration
	public static class Config extends IntegrationTestConfig {

		@Override
		public String[] getEntityBasePackages() {
			return new String[] { Person.class.getPackage().getName() };
		}
	}

	@Autowired private ReactiveCassandraOperations operations;

	ReactiveCassandraRepositoryFactory factory;
	ClassLoader classLoader;
	BeanFactory beanFactory;
	PersonRepostitory repository;

	Person dave, oliver, carter, boyd;

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader == null ? org.springframework.util.ClassUtils.getDefaultClassLoader() : classLoader;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Before
	public void setUp() {

		factory = new ReactiveCassandraRepositoryFactory(operations);
		factory.setRepositoryBaseClass(SimpleReactiveCassandraRepository.class);
		factory.setBeanClassLoader(classLoader);
		factory.setBeanFactory(beanFactory);
		factory.setEvaluationContextProvider(DefaultEvaluationContextProvider.INSTANCE);

		repository = factory.getRepository(PersonRepostitory.class);

		deleteAll();

		dave = new Person("42", "Dave", "Matthews");
		oliver = new Person("4", "Oliver August", "Matthews");
		carter = new Person("49", "Carter", "Beauford");
		boyd = new Person("45", "Boyd", "Tinsley");
	}

	private void insertTestData() {
		StepVerifier.create(repository.save(Arrays.asList(oliver, dave, carter, boyd))).expectNextCount(4).verifyComplete();
	}

	private void deleteAll() {
		StepVerifier.create(repository.deleteAll()).verifyComplete();
	}

	@Test // DATACASS-335
	public void existsByIdShouldReturnTrueForExistingObject() {

		insertTestData();

		StepVerifier.create(repository.exists(dave.getId())).expectNext(true).verifyComplete();
	}

	@Test // DATACASS-335
	public void existsByIdShouldReturnFalseForAbsentObject() {
		StepVerifier.create(repository.exists("unknown")).expectNext(false).verifyComplete();
	}

	@Test // DATACASS-335
	public void existsByMonoOfIdShouldReturnTrueForExistingObject() {

		insertTestData();

		StepVerifier.create(repository.exists(Mono.just(dave.getId()))).expectNext(true).verifyComplete();
	}

	@Test // DATACASS-335
	public void existsByEmptyMonoOfIdShouldReturnEmptyMono() {
		StepVerifier.create(repository.exists(Mono.empty())).verifyComplete();
	}

	@Test // DATACASS-335
	public void findOneShouldReturnObject() {

		insertTestData();

		StepVerifier.create(repository.findOne(dave.getId())).expectNext(dave).verifyComplete();
	}

	@Test // DATACASS-335
	public void findOneShouldCompleteWithoutValueForAbsentObject() {
		StepVerifier.create(repository.findOne("unknown")).verifyComplete();
	}

	@Test // DATACASS-335
	public void findOneByMonoOfIdShouldReturnTrueForExistingObject() {

		insertTestData();

		StepVerifier.create(repository.findOne(Mono.just(dave.getId()))).expectNext(dave).verifyComplete();
	}

	@Test // DATACASS-335
	public void findOneByEmptyMonoOfIdShouldReturnEmptyMono() {
		StepVerifier.create(repository.findOne(Mono.empty())).verifyComplete();
	}

	@Test // DATACASS-335
	public void findAllShouldReturnAllResults() {

		insertTestData();

		StepVerifier.create(repository.findAll()).expectNextCount(4).verifyComplete();
	}

	@Test // DATACASS-335
	public void findAllByIterableOfIdShouldReturnResults() {

		insertTestData();

		StepVerifier.create(repository.findAll(Arrays.asList(dave.getId(), boyd.getId()))) //
				.expectNextCount(2) //
				.verifyComplete();
	}

	@Test // DATACASS-335
	public void findAllByPublisherOfIdShouldReturnResults() {

		insertTestData();

		StepVerifier.create(repository.findAll(Flux.just(dave.getId(), boyd.getId()))) //
				.expectNextCount(2) //
				.verifyComplete();
	}

	@Test // DATACASS-335
	public void findAllByEmptyPublisherOfIdShouldReturnResults() {
		StepVerifier.create(repository.findAll(Flux.empty())).verifyComplete();
	}

	@Test // DATACASS-335
	public void countShouldReturnNumberOfRecords() {

		insertTestData();

		StepVerifier.create(repository.count()).expectNext(4L).verifyComplete();
	}

	@Test // DATACASS-335
	public void insertEntityShouldInsertEntity() {

		Person person = new Person("36", "Homer", "Simpson");

		StepVerifier.create(repository.insert(person)).expectNext(person).verifyComplete();

		StepVerifier.create(repository.findAll()).expectNextCount(1L).verifyComplete();
	}

	@Test // DATACASS-335
	public void insertShouldDeferredWrite() {

		Person person = new Person("36", "Homer", "Simpson");

		repository.insert(person);

		StepVerifier.create(repository.findAll()).expectNextCount(0L).verifyComplete();
	}

	@Test // DATACASS-335
	public void insertIterableOfEntitiesShouldInsertEntity() {

		StepVerifier.create(repository.insert(Arrays.asList(dave, oliver, boyd))).expectNextCount(3L).verifyComplete();

		StepVerifier.create(repository.findAll()).expectNextCount(3L).verifyComplete();
	}

	@Test // DATACASS-335
	public void insertPublisherOfEntitiesShouldInsertEntity() {

		StepVerifier.create(repository.insert(Flux.just(dave, oliver, boyd))).expectNextCount(3L).verifyComplete();

		StepVerifier.create(repository.findAll()).expectNextCount(3L).verifyComplete();
	}

	@Test // DATACASS-335
	public void saveEntityShouldUpdateExistingEntity() {

		dave.setFirstname("Hello, Dave");
		dave.setLastname("Bowman");

		StepVerifier.create(repository.save(dave)).expectNextCount(1).verifyComplete();

		StepVerifier.create(repository.findOne(dave.getId())).consumeNextWith(actual -> {

			assertThat(actual.getFirstname()).isEqualTo(dave.getFirstname());
			assertThat(actual.getLastname()).isEqualTo(dave.getLastname());
		}).verifyComplete();
	}

	@Test // DATACASS-335
	public void saveEntityShouldInsertNewEntity() {

		Person person = new Person("36", "Homer", "Simpson");

		StepVerifier.create(repository.save(person)).expectNextCount(1).verifyComplete();

		StepVerifier.create(repository.findOne(person.getId())).expectNext(person).verifyComplete();
	}

	@Test // DATACASS-335
	public void saveIterableOfNewEntitiesShouldInsertEntity() {

		StepVerifier.create(repository.save(Arrays.asList(dave, oliver, boyd))).expectNextCount(3).verifyComplete();

		StepVerifier.create(repository.findAll()).expectNextCount(3L).verifyComplete();
	}

	@Test // DATACASS-335
	public void saveIterableOfMixedEntitiesShouldInsertEntity() {

		Person person = new Person("36", "Homer", "Simpson");

		dave.setFirstname("Hello, Dave");
		dave.setLastname("Bowman");

		StepVerifier.create(repository.save(Arrays.asList(person, dave))).expectNextCount(2).verifyComplete();

		StepVerifier.create(repository.findOne(dave.getId())).expectNext(dave).verifyComplete();

		StepVerifier.create(repository.findOne(person.getId())).expectNext(person).verifyComplete();
	}

	@Test // DATACASS-335
	public void savePublisherOfEntitiesShouldInsertEntity() {

		StepVerifier.create(repository.save(Flux.just(dave, oliver, boyd))).expectNextCount(3).verifyComplete();

		StepVerifier.create(repository.findAll()).expectNextCount(3L).verifyComplete();
	}

	@Test // DATACASS-335
	public void deleteAllShouldRemoveEntities() {

		insertTestData();

		StepVerifier.create(repository.deleteAll()).verifyComplete();

		StepVerifier.create(repository.findAll()).verifyComplete();
	}

	@Test // DATACASS-335
	public void deleteByIdShouldRemoveEntity() {

		StepVerifier.create(repository.delete(dave.getId())).verifyComplete();

		StepVerifier.create(repository.findOne(dave.getId())).expectNextCount(0).verifyComplete();
	}

	@Test // DATACASS-335
	public void deleteShouldRemoveEntity() {

		StepVerifier.create(repository.delete(dave)).verifyComplete();

		StepVerifier.create(repository.findOne(dave.getId())).expectNextCount(0).verifyComplete();
	}

	@Test // DATACASS-335
	public void deleteIterableOfEntitiesShouldRemoveEntities() {

		StepVerifier.create(repository.delete(Arrays.asList(dave, boyd))).verifyComplete();

		StepVerifier.create(repository.findOne(boyd.getId())).expectNextCount(0).verifyComplete();
	}

	@Test // DATACASS-335
	public void deletePublisherOfEntitiesShouldRemoveEntities() {

		StepVerifier.create(repository.delete(Flux.just(dave, boyd))).verifyComplete();

		StepVerifier.create(repository.findOne(boyd.getId())).expectNextCount(0).verifyComplete();
	}

	interface PersonRepostitory extends ReactiveCassandraRepository<Person, String> {}
}
