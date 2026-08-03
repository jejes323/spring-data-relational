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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import org.springframework.data.annotation.Id;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.mapping.PersistentPropertyPath;

/**
 * Unit tests for {@link PropertyPathResolver}.
 *
 * @author Jens Schauder
 */
class PropertyPathResolverUnitTests {

	private final RelationalMappingContext context = new RelationalMappingContext();
	private final PropertyPathResolver resolver = new PropertyPathResolver(context);

	@Test // GH-2335
	void resolvesMappedProperty() {

		PersistentPropertyPath<RelationalPersistentProperty> path = resolver.resolve(entity(), "customerId");

		assertThat(path).isNotNull();
		assertThat(path.getLeafProperty().getName()).isEqualTo("customerId");
	}

	@Test // GH-2335
	void returnsNullForRawColumnName() {

		assertThat(resolver.resolve(entity(), "customer_id")).isNull();
	}

	@Test // GH-2335
	void cachesResolutionOfRawColumnName() {

		try (MockedConstruction<PropertyReferenceException> mocked = mockConstruction(PropertyReferenceException.class)) {

			resolver.resolve(entity(), "customer_id");
			resolver.resolve(entity(), "customer_id");
			resolver.resolve(entity(), "customer_id");

			// The exception-based fallback in PropertyPath.from(...) is exercised only on the first resolution.
			assertThat(mocked.constructed()).hasSize(1);
		}
	}

	private RelationalPersistentEntity<?> entity() {
		return context.getRequiredPersistentEntity(Order.class);
	}

	static class Order {

		@Id Long orderId;
		Long customerId;
		String status;
	}
}
