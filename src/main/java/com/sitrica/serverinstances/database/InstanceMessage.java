package com.sitrica.serverinstances.database;

import com.google.gson.JsonObject;

public class InstanceMessage {

	public enum Type {STARTUP, SHUTDOWN}

	private final JsonObject object;
	private final String server;
	private final Type type;

	public InstanceMessage(String server, Type type, JsonObject object) {
		this.object = object;
		this.server = server;
		this.type = type;
	}

	public JsonObject getJsonObject() {
		return object;
	}

	public String getServerName() {
		return server;
	}

	public Type getType() {
		return type;
	}

}
