package com.sitrica.serverinstances.database;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;

public class InstanceMessageSerializer implements Serializer<InstanceMessage> {

	@Override
	public InstanceMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
		JsonObject object = json.getAsJsonObject();
		String name = object.get("name").getAsString();
		com.sitrica.serverinstances.database.InstanceMessage.Type type = com.sitrica.serverinstances.database.InstanceMessage.Type.valueOf(object.get("type").getAsString());
		JsonObject data = object.get("object").getAsJsonObject();
		return new InstanceMessage(name, type, data);
	}

	@Override
	public JsonElement serialize(InstanceMessage message, Type typeOfSrc, JsonSerializationContext context) {
		JsonObject object = new JsonObject();
		object.addProperty("type", message.getType().name());
		object.addProperty("name", message.getServerName());
		object.add("object", message.getJsonObject());
		return object;
	}

}
