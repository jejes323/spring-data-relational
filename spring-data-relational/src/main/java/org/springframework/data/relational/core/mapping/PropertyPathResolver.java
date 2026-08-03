/*
 * Copyright 2026-present the original author or authors.
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
package org.springframework.data.relational.core.mapping;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.data.core.PropertyPath;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentLruCache;

/**
 * Resolves a path expression (such as {@literal lastname} or {@literal home.city}) or column names like to
 * {@literal customer_id} a {@link PersistentPropertyPath} within a {@link RelationalPersistentEntity}. If no matching
 * path is found {@literal null} is returned.
 * <p>
 * The result, both positive and negative matches are cached. The cache of negative matches is limited to limit possible
 * memory consumption.
 *
 * @author Jens Schauder
 * @since 4.2
 */
public class PropertyPathResolver {

	/**
	 * Upper bound for the number of cached unresolvable path expressions. Chosen to comfortably exceed the number of
	 * distinct non-property references a typical application issues (such as raw column names) while capping the memory a
	 * flood of arbitrary references can consume.
	 */
	private static final int UNRESOLVABLE_CACHE_SIZE = 4096;

	private final MappingContext<? extends RelationalPersistentEntity<?>, RelationalPersistentProperty> mappingContext;
	private final Map<PathResolutionKey, PersistentPropertyPath<RelationalPersistentProperty>> resolved = new ConcurrentHashMap<>();
	private final ConcurrentLruCache<PathResolutionKey, Boolean> unresolvable = new ConcurrentLruCache<>(
			UNRESOLVABLE_CACHE_SIZE, key -> Boolean.TRUE);

	/**
	 * Creates a new {@link PropertyPathResolver} for the given {@link MappingContext}.
	 *
	 * @param mappingContext must not be {@literal null}.
	 */
	public PropertyPathResolver(
			MappingContext<? extends RelationalPersistentEntity<?>, RelationalPersistentProperty> mappingContext) {

		Assert.notNull(mappingContext, "MappingContext must not be null");

		this.mappingContext = mappingContext;
	}

	/**
	 * Resolves the {@link PersistentPropertyPath} for the given path expression on the given entity. Results are cached.
	 *
	 * @param entity the entity to resolve the path expression against, must not be {@literal null}.
	 * @param pathExpression the path expression to resolve, must not be {@literal null}.
	 * @return the resolved {@link PersistentPropertyPath} or {@literal null} if the expression does not denote a property
	 *         path.
	 */
	public @Nullable PersistentPropertyPath<RelationalPersistentProperty> resolve(RelationalPersistentEntity<?> entity,
			String pathExpression) {

		PathResolutionKey key = new PathResolutionKey(entity, pathExpression);

		PersistentPropertyPath<RelationalPersistentProperty> cached = this.resolved.get(key);
		if (cached != null) {
			return cached;
		}

		if (this.unresolvable.contains(key)) {
			return null;
		}

		PersistentPropertyPath<RelationalPersistentProperty> path = doResolve(entity, pathExpression);

		if (path == null) {
			this.unresolvable.get(key);
			return null;
		}

		this.resolved.put(key, path);
		return path;
	}

	private @Nullable PersistentPropertyPath<RelationalPersistentProperty> doResolve(RelationalPersistentEntity<?> entity,
			String pathExpression) {

		try {

			PropertyPath path = forName(entity, pathExpression);

			if (isPathToJavaLangClassProperty(path)) {
				return null;
			}

			return this.mappingContext.getPersistentPropertyPath(path);
		} catch (MappingException | PropertyReferenceException e) {
			return null;
		}
	}

	private static PropertyPath forName(RelationalPersistentEntity<?> entity, String path) {

		if (entity.getPersistentProperty(path) != null) {
			return PropertyPath.from(Pattern.quote(path), entity.getTypeInformation());
		}

		return PropertyPath.from(path, entity.getTypeInformation());
	}

	private static boolean isPathToJavaLangClassProperty(PropertyPath path) {
		return path.getType().equals(Class.class) && path.getLeafProperty().getOwningType().getType().equals(Class.class);
	}

	private record PathResolutionKey(RelationalPersistentEntity<?> entity, String pathExpression) {
	}
}
