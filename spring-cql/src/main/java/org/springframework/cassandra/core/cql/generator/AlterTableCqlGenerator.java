/*
 * Copyright 2013-2014 the original author or authors.
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
package org.springframework.cassandra.core.cql.generator;

import static org.springframework.cassandra.core.cql.CqlStringUtils.*;

import java.util.Map;

import org.springframework.cassandra.core.keyspace.AddColumnSpecification;
import org.springframework.cassandra.core.keyspace.AlterColumnSpecification;
import org.springframework.cassandra.core.keyspace.AlterTableSpecification;
import org.springframework.cassandra.core.keyspace.ColumnChangeSpecification;
import org.springframework.cassandra.core.keyspace.DropColumnSpecification;
import org.springframework.cassandra.core.keyspace.Option;
import org.springframework.cassandra.core.keyspace.RenameColumnSpecification;
import org.springframework.cassandra.core.keyspace.TableOption;

/**
 * CQL generator for generating {@code ALTER TABLE} statements.
 * 
 * @author Matthew T. Adams
 * @author Mark Paluch
 * @see AlterTableSpecification
 */
public class AlterTableCqlGenerator extends TableOptionsCqlGenerator<AlterTableSpecification> {

	/**
	 * Generates a CQL statement from the given {@code specification}.
	 * 
	 * @param specification must not be {@literal null}.
	 * @return the generated CQL statement.
	 */
	public static String toCql(AlterTableSpecification specification) {
		return new AlterTableCqlGenerator(specification).toCql();
	}

	/**
	 * Creates a new {@literal {@link AlterTableCqlGenerator}.
	 * 
	 * @param specification must not be {@literal null}.
	 */
	public AlterTableCqlGenerator(AlterTableSpecification specification) {
		super(specification);
	}

	@Override
	public StringBuilder toCql(StringBuilder cql) {
		cql = noNull(cql);

		preambleCql(cql);

		if (!spec().getChanges().isEmpty()) {
			cql.append(' ');
			changesCql(cql);
		}

		if (!spec().getOptions().isEmpty()) {
			cql.append(' ');
			optionsCql(cql);
		}

		cql.append(";");

		return cql;
	}

	protected StringBuilder preambleCql(StringBuilder cql) {
		return noNull(cql).append("ALTER TABLE ").append(spec().getName());
	}

	protected StringBuilder changesCql(StringBuilder cql) {
		cql = noNull(cql);

		boolean first = true;
		for (ColumnChangeSpecification change : spec().getChanges()) {
			if (first) {
				first = false;
			} else {
				cql.append(" ");
			}
			getCqlGeneratorFor(change).toCql(cql);
		}

		return cql;
	}

	protected ColumnChangeCqlGenerator<?> getCqlGeneratorFor(ColumnChangeSpecification change) {

		if (change instanceof AddColumnSpecification) {
			return new AddColumnCqlGenerator((AddColumnSpecification) change);
		}

		if (change instanceof DropColumnSpecification) {
			return new DropColumnCqlGenerator((DropColumnSpecification) change);
		}

		if (change instanceof AlterColumnSpecification) {
			return new AlterColumnCqlGenerator((AlterColumnSpecification) change);
		}

		if (change instanceof RenameColumnSpecification) {
			return new RenameColumnCqlGenerator((RenameColumnSpecification) change);
		}

		throw new IllegalArgumentException("unknown ColumnChangeSpecification type: " + change.getClass().getName());
	}

	@SuppressWarnings("unchecked")
	protected StringBuilder optionsCql(StringBuilder cql) {

		cql = noNull(cql);

		Map<String, Object> options = spec().getOptions();
		if (options == null || options.isEmpty()) {
			return cql;
		}

		cql.append("WITH ");
		boolean first = true;
		for (String key : options.keySet()) {

			/*
			 * Compact storage is illegal on alter table.
			 * 
			 * TODO - Is there a way to handle this in the specification?
			 */
			if (key.equals(TableOption.COMPACT_STORAGE.getName())) {
				throw new IllegalArgumentException("Alter table cannot contain the COMPACT STORAGE option");
			}

			if (first) {
				first = false;
			} else {
				cql.append(" AND ");
			}

			cql.append(key);

			Object value = options.get(key);
			if (value == null) {
				continue;
			}
			cql.append(" = ");

			if (value instanceof Map) {
				optionValueMap((Map<Option, Object>) value, cql);
				continue;
			}

			// else just use value as string
			cql.append(value.toString());
		}
		return cql;
	}
}
