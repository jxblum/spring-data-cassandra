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
package org.springframework.data.cassandra.repository.query;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.cassandra.convert.CassandraConverter;
import org.springframework.data.cassandra.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.mapping.CassandraPersistentProperty;
import org.springframework.data.cassandra.mapping.CassandraSimpleTypeHolder;
import org.springframework.data.cassandra.mapping.CassandraType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;

import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.DataType.CollectionType;
import com.datastax.driver.core.TypeCodec;

/**
 * Custom {@link org.springframework.data.repository.query.ParameterAccessor} that uses a {@link CassandraConverter} to
 * convert parameters.
 *
 * @author Mark Paluch
 * @since 1.5
 */
class ConvertingParameterAccessor implements CassandraParameterAccessor {

	private static final TypeInformation<Set> SET = ClassTypeInformation.from(Set.class);

	private final CassandraConverter converter;

	private final CassandraParameterAccessor delegate;

	ConvertingParameterAccessor(CassandraConverter converter, CassandraParameterAccessor delegate) {

		this.converter = converter;
		this.delegate = delegate;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.query.ParameterAccessor#getPageable()
	 */
	@Override
	public Pageable getPageable() {
		return delegate.getPageable();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.query.ParameterAccessor#getSort()
	 */
	@Override
	public Sort getSort() {
		return delegate.getSort();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.query.ParameterAccessor#getDynamicProjection()
	 */
	@Override
	public Optional<Class<?>> getDynamicProjection() {
		return delegate.getDynamicProjection();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.query.ParameterAccessor#getBindableValue(int)
	 */
	@Override
	public Object getBindableValue(int index) {
		return potentiallyConvert(index, Optional.ofNullable(delegate.getBindableValue(index)));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.repository.query.CassandraParameterAccessor#findCassandraType(int)
	 */
	@Override
	public CassandraType findCassandraType(int index) {
		return delegate.findCassandraType(index);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.repository.query.CassandraParameterAccessor#getDataType(int)
	 */
	@Override
	public DataType getDataType(int index) {

		DataType dataType = delegate.getDataType(index);

		return (dataType != null ? dataType : converter.getMappingContext().getDataType(getParameterType(index)));
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.repository.query.CassandraParameterAccessor#getParameterType(int)
	 */
	@Override
	public Class<?> getParameterType(int index) {
		return delegate.getParameterType(index);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.query.ParameterAccessor#hasBindableNullValue()
	 */
	@Override
	public boolean hasBindableNullValue() {
		return delegate.hasBindableNullValue();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.repository.query.ParameterAccessor#iterator()
	 */
	public Iterator<Object> iterator() {
		return new ConvertingIterator(delegate.iterator());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.repository.query.CassandraParameterAccessor#getValues()
	 */
	@Override
	public Object[] getValues() {
		return delegate.getValues();
	}

	@SuppressWarnings("unchecked")
	private Object potentiallyConvert(int index, Optional<Object> bindableValue) {

		return bindableValue
				.flatMap(
						v -> converter.convertToColumnType(bindableValue, findTypeInformation(index, v, Optional.empty())))
				.orElse(null);
	}

	@SuppressWarnings("unchecked")
	private Object potentiallyConvert(int index, Optional<Object> bindableValue, CassandraPersistentProperty property) {

		return bindableValue.flatMap(
				v -> converter.convertToColumnType(bindableValue, findTypeInformation(index, v, Optional.of(property))))
				.orElse(null);
	}

	private TypeInformation<?> findTypeInformation(int index, Object bindableValue,
			Optional<CassandraPersistentProperty> property) {

		if (delegate.findCassandraType(index) != null) {
			TypeCodec<?> typeCodec = CodecRegistry.DEFAULT_INSTANCE.codecFor(getDataType(index, property));

			if (typeCodec.getJavaType().getType() instanceof Class<?>) {
				return ClassTypeInformation.from((Class<?>) typeCodec.getJavaType().getType());
			}

			return ClassTypeInformation.from(typeCodec.getJavaType().getRawType());
		}

		return property.map(PersistentProperty::getTypeInformation)
				.orElseGet(() -> (TypeInformation) ClassTypeInformation.from(bindableValue.getClass()));
	}

	/**
	 * Return the {@link DataType} based on annotated parameters with {@link CassandraType}, the
	 * {@link CassandraPersistentProperty} type or the declared parameter type.
	 *
	 * @param index index of parameter.
	 * @param property {@link CassandraPersistentProperty}.
	 * @return the {@link DataType}
	 */
	DataType getDataType(int index, Optional<CassandraPersistentProperty> optionalProperty) {

		CassandraType cassandraType = delegate.findCassandraType(index);

		if (cassandraType != null) {
			return CassandraSimpleTypeHolder.getDataTypeFor(cassandraType.type());
		}

		CassandraMappingContext mappingContext = converter.getMappingContext();
		TypeInformation<?> typeInformation = ClassTypeInformation.from(getParameterType(index));

		return optionalProperty.map(property -> getDataType(mappingContext, typeInformation, property))
				.orElseGet(() -> mappingContext.getDataType(typeInformation.getType()));

	}

	private DataType getDataType(CassandraMappingContext mappingContext, TypeInformation<?> typeInformation,
			CassandraPersistentProperty property) {

		DataType dataType = mappingContext.getDataType(property);

		if (property.isCollectionLike() && !typeInformation.isCollectionLike()) {
			if (dataType instanceof CollectionType) {
				CollectionType collectionType = (CollectionType) dataType;

				if (collectionType.getTypeArguments().size() == 1) {
					return collectionType.getTypeArguments().get(0);
				}
			}
		}

		if (!property.isCollectionLike() && typeInformation.isCollectionLike()) {
			if (typeInformation.isAssignableFrom(SET)) {
				return DataType.set(dataType);
			}

			return DataType.list(dataType);
		}

		if (property.isMap()) {
			if (dataType instanceof CollectionType) {
				CollectionType collectionType = (CollectionType) dataType;

				if (collectionType.getTypeArguments().size() == 2) {
					return collectionType.getTypeArguments().get(0);
				}
			}
		}

		return mappingContext.getDataType(property);
	}

	/**
	 * Custom {@link Iterator} to convert items before returning them.
	 *
	 * @author Mark Paluch
	 */
	private class ConvertingIterator implements PotentiallyConvertingIterator {

		private final Iterator<Object> delegate;

		private int index = 0;

		/**
		 * Create a new {@link ConvertingIterator} for the given delegate.
		 *
		 * @param delegate must not be {@literal null}.
		 */
		ConvertingIterator(Iterator<Object> delegate) {
			this.delegate = delegate;
		}

		/*
		 * (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			return delegate.hasNext();
		}

		/*
		 * (non-Javadoc)
		 * @see java.util.Iterator#next()
		 */
		public Object next() {
			return potentiallyConvert(index++, Optional.ofNullable(delegate.next()));
		}

		/*
		 * (non-Javadoc)
		 * @see java.util.Iterator#remove()
		 */
		public void remove() {
			delegate.remove();
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.repository.query.ConvertingParameterAccessor.PotentiallyConvertingIterator#nextConverted(org.springframework.data.cassandra.mapping.CassandraPersistentProperty)
		 */
		@Override
		public Object nextConverted(CassandraPersistentProperty property) {
			return potentiallyConvert(index++, Optional.ofNullable(delegate.next()), property);
		}
	}

	/**
	 * Custom {@link Iterator} that adds a method to access elements in a converted manner.
	 *
	 * @author Mark Paluch
	 */
	interface PotentiallyConvertingIterator extends Iterator<Object> {

		/**
		 * Returns the next element and pass in type information for potential conversion.
		 *
		 * @return the converted object, may be {@literal null}.
		 */
		Object nextConverted(CassandraPersistentProperty property);

	}
}
