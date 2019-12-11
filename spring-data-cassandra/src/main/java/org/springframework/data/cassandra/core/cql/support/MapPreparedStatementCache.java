/*
 * Copyright 2017-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.cassandra.core.cql.support;

import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.util.Assert;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;

/**
 * {@link PreparedStatementCache} backed by a {@link Map} cache. Defaults to simple {@link ConcurrentHashMap} caching.
 * <p/>
 * Statements are cached with a key consisting of {@link CqlSession#getName() session name}, {@code keyspace} and the
 * {@code cql} text. Statement options (idempotency, timeouts) apply from the statement that was initially prepared.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class MapPreparedStatementCache implements PreparedStatementCache {

	private final Map<CacheKey, PreparedStatement> cache;

	/**
	 * Create a new {@link MapPreparedStatementCache}.
	 *
	 * @param cache must not be {@literal null}.
	 */
	private MapPreparedStatementCache(Map<CacheKey, PreparedStatement> cache) {

		Assert.notNull(cache, "Cache must not be null");

		this.cache = cache;
	}

	/**
	 * Create a {@link MapPreparedStatementCache} using {@link ConcurrentHashMap}.
	 *
	 * @return the new {@link MapPreparedStatementCache} backed by {@link ConcurrentHashMap}.
	 */
	public static MapPreparedStatementCache create() {
		return of(new ConcurrentHashMap<>());
	}

	/**
	 * Create a {@link MapPreparedStatementCache} using the given {@link Map}.
	 *
	 * @return the new {@link MapPreparedStatementCache} backed the given {@link Map}.
	 */
	public static MapPreparedStatementCache of(Map<CacheKey, PreparedStatement> cache) {
		return new MapPreparedStatementCache(cache);
	}

	/**
	 * @return the underlying {@link Map cache}.
	 */
	protected Map<CacheKey, PreparedStatement> getCache() {
		return this.cache;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.core.cql.support.PrepatedStatementCache#getPreparedStatement(com.datastax.oss.driver.api.core.CqlSession, com.datastax.oss.driver.api.core.cql.SimpleStatement, java.util.function.Supplier)
	 */
	@Override
	public PreparedStatement getPreparedStatement(CqlSession session, SimpleStatement statement,
			Supplier<PreparedStatement> preparer) {

		CacheKey cacheKey = new CacheKey(session, statement.toString());

		return getCache().computeIfAbsent(cacheKey, key -> preparer.get());
	}

	/**
	 * {@link CacheKey} for {@link PreparedStatement} caching.
	 */
	@EqualsAndHashCode
	protected static class CacheKey {

		final String sessionName;
		final String keyspace;
		final String cql;

		CacheKey(CqlSession session, String cql) {

			this.sessionName = session.getName();
			this.keyspace = session.getKeyspace().orElse(CqlIdentifier.fromCql("system")).asInternal();
			this.cql = cql;
		}
	}
}
