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
package org.springframework.cassandra.core.cql;

import java.io.Serializable;
import java.util.regex.Pattern;

import org.springframework.cassandra.core.ReservedKeyword;
import org.springframework.util.Assert;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.TableMetadata;

/**
 * This encapsulates the logic for CQL quoted and unquoted identifiers.
 *
 * <p>CQL identifiers, when unquoted, are converted to lower case. When quoted, they are returned as-is with no lower
 * casing and encased in double quotes. To render, use any of the methods {@link #toCql()},
 * {@link #toCql(StringBuilder)}, or {@link #toString()}.
 *
 * @author John McPeek
 * @author Matthew T. Adams
 * @author Mark Paluch
 * @author John Blum
 * @see #toCql()
 * @see #toCql(StringBuilder)
 * @see #toString()
 */
public final class CqlIdentifier implements Comparable<CqlIdentifier>, Serializable {

	private static final long serialVersionUID = -974441606330912437L;

	public static final String UNQUOTED_REGEX = "(?i)[a-z][\\w]*";
	public static final Pattern UNQUOTED = Pattern.compile(UNQUOTED_REGEX);

	public static final String QUOTED_REGEX = "(?i)[a-z]([\\w]*(\"\")+[\\w]*)+";
	public static final Pattern QUOTED = Pattern.compile(QUOTED_REGEX);

	/**
	 * Factory method for {@link CqlIdentifier}. Convenient if imported statically.
	 *
	 * @see #CqlIdentifier(CharSequence)
	 */
	public static CqlIdentifier cqlId(CharSequence identifier) {
		return new CqlIdentifier(identifier);
	}

	/**
	 * Factory method for {@link CqlIdentifier}. Convenient if imported statically.
	 *
	 * @see #CqlIdentifier(CharSequence, boolean)
	 */
	public static CqlIdentifier cqlId(CharSequence identifier, boolean forceQuote) {
		return new CqlIdentifier(identifier, forceQuote);
	}

	/**
	 * Factory method for a force-quoted {@link CqlIdentifier}. Convenient if imported statically.
	 *
	 * @see #CqlIdentifier(CharSequence, boolean)
	 */
	public static CqlIdentifier quotedCqlId(CharSequence identifier) {
		return new CqlIdentifier(identifier, true);
	}

	/**
	 * Returns <code>true</code> if the given {@link CharSequence} is a legal unquoted identifier.
	 */
	public static boolean isUnquotedIdentifier(CharSequence chars) {
		return UNQUOTED.matcher(chars).matches() && !ReservedKeyword.isReserved(chars);
	}

	/**
	 * Returns <code>true</code> if the given {@link CharSequence} is a legal unquoted identifier.
	 */
	public static boolean isQuotedIdentifier(CharSequence chars) {
		return QUOTED.matcher(chars).matches() || ReservedKeyword.isReserved(chars);
	}

	private String identifier;
	private String unquoted;
	private boolean quoted;

	/**
	 * Creates a new {@link CqlIdentifier} without force-quoting it. It may end up quoted, depending on its value.
	 *
	 * @see #cqlId(CharSequence)
	 */
	public CqlIdentifier(CharSequence identifier) {
		this(identifier, false);
	}

	/**
	 * Creates a new CQL identifier, optionally force-quoting it. Force-quoting can be used to preserve identifier case.
	 * <ul>
	 * <li>If the given identifier is a legal quoted identifier or <code>forceQuote</code> is <code>true</code>,
	 * {@link #isQuoted()} will return <code>true</code> and the identifier will be quoted when rendered.</li>
	 * <li>If the given identifier is a legal unquoted identifier, {@link #isQuoted()} will return <code>false</code>,
	 * plus the name will be converted to lower case and rendered as such.</li>
	 * <li>If the given identifier is illegal, an {@link IllegalArgumentException} is thrown.</li>
	 * </ul>
	 *
	 * @see #cqlId(CharSequence, boolean)
	 * @see #quotedCqlId(CharSequence)
	 */
	public CqlIdentifier(CharSequence identifier, boolean forceQuote) {
		setIdentifier(identifier, forceQuote);
	}

	/**
	 * Tests & sets the given identifier.
	 */
	private void setIdentifier(CharSequence identifier, boolean forceQuoting) {

		Assert.notNull(identifier, "Identifier must not be null");

		String string = identifier.toString();

		Assert.hasText(string, "Identifier must not be empty");

		if (forceQuoting || isQuotedIdentifier(string)) {
			this.unquoted = string;
			this.identifier = "\"" + string + "\"";
			quoted = true;
		} else if (isUnquotedIdentifier(string)) {
			this.identifier = this.unquoted = string.toLowerCase();
		} else {
			throw new IllegalArgumentException(String.format(
					"given string [%s] is not a valid quoted or unquoted identifier", identifier));
		}
	}

	/**
	 * Returns the identifier <em>without</em> encasing quotes, regardless of the value of {@link #isQuoted()}. For
	 * example, if {@link #isQuoted()} is <code>true</code>, then this value will be the same as {@link #toCql()} and
	 * {@link #toString()}.
	 * <p/>
	 * This is needed, for example, to get the correct {@link TableMetadata} from
	 * {@link KeyspaceMetadata#getTable(String)}: the given string must <em>not</em> be quoted.
	 */
	public String getUnquoted() {
		return unquoted;
	}

	/**
	 * Renders this identifier appropriately.
	 */
	public String toCql() {
		return identifier;
	}

	/**
	 * Appends the rendering of this identifier to the given {@link StringBuilder}, then returns that
	 * {@link StringBuilder}. If <code>null</code> is given, a new {@link StringBuilder} is created, appended to, and
	 * returned.
	 */
	public StringBuilder toCql(StringBuilder builder) {
		return (builder != null ? builder : new StringBuilder()).append(toCql());
	}

	/**
	 * Whether or not this identifier is quoted.
	 */
	public boolean isQuoted() {
		return quoted;
	}

	/**
	 * Unquoted identifiers sort before quoted ones. Otherwise, they compare according to their identifiers.
	 */
	@Override
	@SuppressWarnings("all")
	public int compareTo(CqlIdentifier identifier) {

		int comparison = ((Boolean) this.quoted).compareTo(identifier.quoted);

		return (comparison != 0 ? comparison : this.identifier.compareTo(identifier.identifier));
	}

	/**
	 * Compares this {@link CqlIdentifier} to the given object. Note that if a {@link CharSequence} is given, a new
	 * {@link CqlIdentifier} is created from it and compared, such that a {@link CharSequence} can be effectively equal to
	 * a {@link CqlIdentifier}.
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof CqlIdentifier || obj instanceof CharSequence)) {
			return false;
		}

		CqlIdentifier that = (obj instanceof CqlIdentifier) ? (CqlIdentifier) obj : cqlId((CharSequence) obj);

		return (this.quoted == that.quoted && this.identifier.equals(that.identifier));
	}

	@Override
	// TODO hmmm, re-evaluate this since it is not a proper hash code matching equals!
	public int hashCode() {
		return ((Boolean) quoted).hashCode() ^ identifier.hashCode();
	}

	/**
	 * Alias for {@link #toCql()}.
	 */
	@Override
	public String toString() {
		return toCql();
	}
}
