package com.sitrica.serverinstances.handlers;

import com.sitrica.serverinstances.database.InstanceMessage;
import com.sitrica.serverinstances.database.InstanceMessage.Type;
import com.sitrica.serverinstances.objects.RunningProperties;

public abstract class MessageHandler {

	private final Type type;

	public MessageHandler(Type type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	public abstract void onMessageRecieve(RunningProperties properties, InstanceMessage message);

}
