package com.sitrica.serverinstances.objects;

import net.md_5.bungee.api.config.ServerInfo;

public class RunningProperties {

	private final long started = System.currentTimeMillis();
	private final ServerInfo info;
	private final Process process;

	public RunningProperties(ServerInfo info, Process process) {
		this.process = process;
		this.info = info;
	}

	public long getStartingTimestamp() {
		return started;
	}

	public ServerInfo getServerInfo() {
		return info;
	}

	public Process getProcess() {
		return process;
	}

}
