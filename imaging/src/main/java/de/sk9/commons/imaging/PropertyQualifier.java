package de.sk9.commons.imaging;

import java.util.function.Function;

public record PropertyQualifier<T> (String namespace, String propertyName, Class<T> propertyType,
		Function<String, T> stringToValue, Function<T, String> valueToString) {
}
