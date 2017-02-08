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
package org.springframework.data.cassandra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cassandra.test.integration.AbstractKeyspaceCreatingIntegrationTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate;
import org.springframework.data.cassandra.domain.Person;
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories;
import org.springframework.data.cassandra.test.integration.support.IntegrationTestConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.reactive.RxJava1CrudRepository;
import org.springframework.data.repository.reactive.RxJava2CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.TestSubscriber;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TableMetadata;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import rx.Observable;
import rx.Single;

/**
 * Test for {@link ReactiveCassandraRepository} using reactive wrapper type conversion.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ConvertingReactiveCassandraRepositoryTests.Config.class)
public class ConvertingReactiveCassandraRepositoryTests extends AbstractKeyspaceCreatingIntegrationTest {

	@Configuration
	@EnableReactiveCassandraRepositories(includeFilters = @Filter(value = Repository.class),
			considerNestedRepositories = true)
	public static class Config extends IntegrationTestConfig {

		@Override
		public String[] getEntityBasePackages() {
			return new String[] { Person.class.getPackage().getName() };
		}
	}

	@Autowired Session session;
	@Autowired ReactiveCassandraTemplate template;
	@Autowired MixedPersonRepository reactiveRepository;
	@Autowired PersonRepostitory reactivePersonRepostitory;
	@Autowired RxJava1PersonRepostitory rxJava1PersonRepostitory;
	@Autowired RxJava2PersonRepostitory rxJava2PersonRepostitory;

	Person dave, oliver, carter, boyd;

	@Before
	public void setUp() throws Exception {

		KeyspaceMetadata keyspace = session.getCluster().getMetadata().getKeyspace(session.getLoggedKeyspace());
		TableMetadata person = keyspace.getTable("person");

		if (person.getIndex("IX_person_lastname") == null) {
			session.execute("CREATE INDEX IX_person_lastname ON person (lastname);");
			Thread.sleep(500);
		}

		reactiveRepository.deleteAll().block();

		dave = new Person("42", "Dave", "Matthews");
		oliver = new Person("4", "Oliver August", "Matthews");
		carter = new Person("49", "Carter", "Beauford");
		boyd = new Person("45", "Boyd", "Tinsley");

		TestSubscriber<Person> subscriber = TestSubscriber.create();

		reactiveRepository.save(Arrays.asList(oliver, dave, carter, boyd)).subscribe(subscriber);

		subscriber.await().assertComplete().assertNoError();
	}

	@Test // DATACASS-335
	public void reactiveStreamsMethodsShouldWork() throws InterruptedException {

		TestSubscriber<Boolean> subscriber =
				TestSubscriber.subscribe(reactivePersonRepostitory.exists(dave.getId()));

		subscriber.awaitAndAssertNextValueCount(1).assertNoError().assertValues(true);
	}

	@Test // DATACASS-335
	public void reactiveStreamsQueryMethodsShouldWork() {

		TestSubscriber<Person> subscriber =
				TestSubscriber.subscribe(reactivePersonRepostitory.findByLastname(boyd.getLastname()));

		subscriber.awaitAndAssertNextValueCount(1).assertNoError().assertValues(boyd);
	}

	@Test // DATACASS-360
	public void dtoProjectionShouldWork() {

		TestSubscriber<PersonDto> subscriber =
				TestSubscriber.subscribe(reactivePersonRepostitory.findProjectedByLastname(boyd.getLastname()));

		subscriber.awaitAndAssertNextValueCount(1).assertNoError().assertValuesWith(personDto -> {
			assertThat(personDto.firstname).isEqualTo(boyd.getFirstname());
			assertThat(personDto.lastname).isEqualTo(boyd.getLastname());
		});
	}

	@Test // DATACASS-335
	public void simpleRxJava1MethodsShouldWork() {

		rx.observers.TestSubscriber<Boolean> subscriber = new rx.observers.TestSubscriber<>();

		rxJava1PersonRepostitory.exists(dave.getId()).subscribe(subscriber);

		subscriber.awaitTerminalEvent();
		subscriber.assertCompleted();
		subscriber.assertNoErrors();
		subscriber.assertValue(true);
	}

	@Test // DATACASS-335
	public void existsWithSingleRxJava1IdMethodsShouldWork() {

		rx.observers.TestSubscriber<Boolean> subscriber = new rx.observers.TestSubscriber<>();

		rxJava1PersonRepostitory.exists(Single.just(dave.getId())).subscribe(subscriber);

		subscriber.awaitTerminalEvent();
		subscriber.assertCompleted();
		subscriber.assertNoErrors();
		subscriber.assertValue(true);
	}

	@Test // DATACASS-335
	public void singleRxJava1QueryMethodShouldWork() {

		rx.observers.TestSubscriber<Person> subscriber = new rx.observers.TestSubscriber<>();

		rxJava1PersonRepostitory.findManyByLastname(dave.getLastname()).subscribe(subscriber);

		subscriber.awaitTerminalEvent();
		subscriber.assertNoErrors();
		subscriber.assertCompleted();
		subscriber.assertValueCount(2);
	}

	@Test // DATACASS-335
	public void singleProjectedRxJava1QueryMethodShouldWork() {

		rx.observers.TestSubscriber<ProjectedPerson> subscriber = new rx.observers.TestSubscriber<>();

		rxJava1PersonRepostitory.findProjectedByLastname(carter.getLastname()).subscribe(subscriber);

		subscriber.awaitTerminalEvent();
		subscriber.assertCompleted();
		subscriber.assertNoErrors();

		ProjectedPerson projectedPerson = subscriber.getOnNextEvents().get(0);

		assertThat(projectedPerson.getFirstname()).isEqualTo(carter.getFirstname());
	}

	@Test // DATACASS-335
	public void observableRxJava1QueryMethodShouldWork() {

		rx.observers.TestSubscriber<Person> subscriber = new rx.observers.TestSubscriber<>();

		rxJava1PersonRepostitory.findByLastname(boyd.getLastname()).subscribe(subscriber);

		subscriber.awaitTerminalEvent();
		subscriber.assertCompleted();
		subscriber.assertNoErrors();
		subscriber.assertValue(boyd);
	}

	@Test // DATACASS-398
	public void simpleRxJava2MethodsShouldWork() {

		TestObserver<Boolean> testObserver = rxJava2PersonRepostitory.exists(dave.getId()).test();

		testObserver.awaitTerminalEvent();
		testObserver.assertComplete();
		testObserver.assertNoErrors();
		testObserver.assertValue(true);
	}

	@Test // DATACASS-398
	public void existsWithSingleRxJava2IdMethodsShouldWork() {

		TestObserver<Boolean> testObserver =
				rxJava2PersonRepostitory.exists(io.reactivex.Single.just(dave.getId())).test();

		testObserver.awaitTerminalEvent();
		testObserver.assertComplete();
		testObserver.assertNoErrors();
		testObserver.assertValue(true);
	}

	@Test // DATACASS-398
	public void flowableRxJava2QueryMethodShouldWork() {

		io.reactivex.subscribers.TestSubscriber<Person> testSubscriber =
				rxJava2PersonRepostitory.findManyByLastname(dave.getLastname()).test();

		testSubscriber.awaitTerminalEvent();
		testSubscriber.assertComplete();
		testSubscriber.assertNoErrors();
		testSubscriber.assertValueCount(2);
	}

	@Test // DATACASS-398
	public void singleProjectedRxJava2QueryMethodShouldWork() {

		TestObserver<ProjectedPerson> testObserver =
				rxJava2PersonRepostitory.findProjectedByLastname(Maybe.just(carter.getLastname())).test();

		testObserver.awaitTerminalEvent();
		testObserver.assertComplete();
		testObserver.assertNoErrors();

		testObserver.assertValue(actual -> {
			assertThat(actual.getFirstname()).isEqualTo(carter.getFirstname());
			return true;
		});
	}

	@Test // DATACASS-398
	public void observableProjectedRxJava2QueryMethodShouldWork() {

		TestObserver<ProjectedPerson> testObserver =
				rxJava2PersonRepostitory.findProjectedByLastname(Single.just(carter.getLastname())).test();

		testObserver.awaitTerminalEvent();
		testObserver.assertComplete();
		testObserver.assertNoErrors();

		testObserver.assertValue(actual -> {
			assertThat(actual.getFirstname()).isEqualTo(carter.getFirstname());
			return true;
		});
	}

	@Test // DATACASS-398
	public void maybeRxJava2QueryMethodShouldWork() {

		TestObserver<Person> testObserver = rxJava2PersonRepostitory.findByLastname(boyd.getLastname()).test();

		testObserver.awaitTerminalEvent();
		testObserver.assertComplete();
		testObserver.assertNoErrors();
		testObserver.assertValue(boyd);
	}

	@Test // DATACASS-335
	public void mixedRepositoryShouldWork() {

		Person value = reactiveRepository.findByLastname(boyd.getLastname()).toBlocking().value();

		assertThat(value).isEqualTo(boyd);
	}

	@Test // DATACASS-335
	public void shouldFindOneByPublisherOfLastName() {

		Person carter = reactiveRepository.findByLastname(Single.just(this.carter.getLastname())).block();

		assertThat(carter.getFirstname()).isEqualTo(this.carter.getFirstname());
	}

	@Repository
	interface PersonRepostitory extends ReactiveCrudRepository<Person, String> {

		Publisher<Person> findByLastname(String lastname);

		Flux<PersonDto> findProjectedByLastname(String lastname);
	}

	@Repository
	interface RxJava1PersonRepostitory extends RxJava1CrudRepository<Person, String> {

		Observable<Person> findManyByLastname(String lastname);

		Single<Person> findByLastname(String lastname);

		Single<ProjectedPerson> findProjectedByLastname(String lastname);
	}

	@Repository
	interface RxJava2PersonRepostitory extends RxJava2CrudRepository<Person, String> {

		Flowable<Person> findManyByLastname(String lastname);

		Maybe<Person> findByLastname(String lastname);

		io.reactivex.Single<ProjectedPerson> findProjectedByLastname(Maybe<String> lastname);

		io.reactivex.Observable<ProjectedPerson> findProjectedByLastname(Single<String> lastname);
	}

	@Repository
	interface MixedPersonRepository extends ReactiveCassandraRepository<Person, String> {

		Single<Person> findByLastname(String lastname);

		Mono<Person> findByLastname(Single<String> lastname);
	}

	interface ProjectedPerson {

		String getId();

		String getFirstname();
	}

	static class PersonDto {

		public String firstname, lastname;

		public PersonDto(String firstname, String lastname) {

			this.firstname = firstname;
			this.lastname = lastname;
		}
	}
}
