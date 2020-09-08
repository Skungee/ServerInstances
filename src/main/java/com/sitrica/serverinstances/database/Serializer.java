package com.sitrica.serverinstances.database;

import java.lang.reflect.Type;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

public interface Serializer<T> extends JsonDeserializer<T>, JsonSerializer<T> {

	@SuppressWarnings({"serial"})
	public default Type getType() {
		return new TypeToken<T>(getClass()) {}.getType();
	}

}
