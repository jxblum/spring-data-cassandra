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

package org.springframework.cassandra.core.converter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.ColumnDefinitions.Definition;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Row;

/**
 * Converter to convert {@link Row}s to a {@link Map} of {@link String}/{@link Object} representation.
 *
 * @author Mark Paluch
 */
@ReadingConverter
public class RowToMapConverter implements Converter<Row, Map<String, Object>> {

	@Override
	public Map<String, Object> convert(Row row) {

		if (row == null) {
			return null;
		}

		ColumnDefinitions cols = row.getColumnDefinitions();
		Map<String, Object> map = new HashMap<String, Object>(cols.size());

		for (Definition def : cols.asList()) {

			String name = def.getName();
			map.put(
					name,
					row.isNull(name) ? null : def.getType().deserialize(row.getBytesUnsafe(name),
							ProtocolVersion.NEWEST_SUPPORTED));
		}

		return map;
	}
}
