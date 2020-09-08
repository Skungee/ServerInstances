package com.sitrica.serverinstances;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.sitrica.serverinstances.ServerInstances.Instance;
import com.sitrica.serverinstances.ServerInstances.State;
import com.sitrica.serverinstances.database.H2Database;
import com.sitrica.serverinstances.database.InstanceMessage;
import com.sitrica.serverinstances.handlers.MessageHandler;
import com.sitrica.serverinstances.objects.RunningProperties;
import com.sitrica.serverinstances.objects.Template;
import com.sitrica.serverinstances.utils.Utils;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.scheduler.TaskScheduler;

public class ServerManager {

	private final Map<Instance, RunningProperties> starting = new HashMap<>();
	private final Map<Instance, RunningProperties> running = new HashMap<>();
	private final Set<MessageHandler> handlers = new HashSet<>();
	private final ServerInstances origin;

	public ServerManager(ServerInstances origin, H2Database<InstanceMessage> database) {
		this.origin = origin;
		TaskScheduler scheduler = origin.getRegistrar().getProxy().getScheduler();
		scheduler.schedule(origin.getRegistrar(), () -> {
			// Check if the server is offline.
			for (Iterator<Entry<Instance, RunningProperties>> iterator = running.entrySet().iterator(); iterator.hasNext();) {
				Entry<Instance, RunningProperties> entry = iterator.next();
				RunningProperties properties = entry.getValue();
				if (!Utils.isReachable(properties.getServerInfo().getSocketAddress())) {
					try {
						shutdown(entry.getKey());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			// Check for failed startups.
			for (Iterator<Entry<Instance, RunningProperties>> iterator = starting.entrySet().iterator(); iterator.hasNext();) {
				Entry<Instance, RunningProperties> entry = iterator.next();
				if (System.currentTimeMillis() - entry.getValue().getStartingTimestamp() <= 15 * (60 * 1000)) // 15 minutes.
					continue;
				try {
					shutdown(entry.getKey());
				} catch (IOException e) {
					e.printStackTrace();
				}
				starting.remove(entry.getKey());
			}
		}, 0, 5, TimeUnit.SECONDS);
		scheduler.schedule(origin.getRegistrar(), () -> {
			for (Iterator<String> iterator = database.getKeys().iterator(); iterator.hasNext();) {
				String key = iterator.next();
				InstanceMessage message = database.get(key);
				ServerInfo info = ProxyServer.getInstance().getServerInfo(message.getServerName());
				Optional<Entry<Instance, RunningProperties>> instance = getInstance(info);
				if (!instance.isPresent())
					return;
				handlers.stream()
						.filter(handler -> handler.getType().equals(message.getType()))
						.forEach(handler -> handler.onMessageRecieve(instance.get().getValue(), message));
				database.delete(key);
			}
		}, 0, 100, TimeUnit.MILLISECONDS);
	}

	public <H extends MessageHandler> void addHandler(H handler) {
		handlers.add(handler);
	}

	public Set<Instance> getInstancesByTemplate(String template) {
		return running.entrySet().stream()
				.map(entry -> entry.getKey())
				.filter(instance -> instance.getTemplate().getName().equalsIgnoreCase(template))
				.collect(Collectors.toSet());
	}

	public Optional<Entry<Instance, RunningProperties>> getInstance(ServerInfo info) {
		return running.entrySet().stream().filter(entry -> entry.getValue().getServerInfo().equals(info)).findFirst();
	}

	public void start(Instance instance) throws IOException {
		Template template = instance.getTemplate();
		start(instance, template.getXmx(), template.getXms(), template.getAdditionalCommands());
	}

	public boolean containsName(String name) {
		return running.values().stream().anyMatch(properties -> properties.getServerInfo().getName().equalsIgnoreCase(name));
	}

	public void start(Instance instance, String xmx, String xms, Collection<String> additional) throws IOException {
		if (instance.getState() != State.IDLE)
			return;
		Template template = instance.getTemplate();
		List<String> commands = Lists.newArrayList("java");
		commands.add(xmx.matches("(\\-Xmx)(.*)") ? xmx : "-Xmx" + xmx);
		commands.add(xms.matches("(\\-Xms)(.*)") ? xmx : "-Xms" + xms);
		boolean windows = System.getProperty("os.name").matches("(?i)(.*)(windows)(.*)");
		if (windows)
			commands.add("-Djline.terminal=jline.UnsupportedTerminal");
		for (String command : additional)
			commands.add(command);
		commands.add("-jar");
		commands.add(template.getJarName());
		if (windows)
			commands.add("--nojline");

		// Setup name
		String name = template.getName();
		int index = 1;
		while (containsName(name)) {
			name = name + "-" + index;
			index++;
		}

		// Setup
		ServerInfo info = ProxyServer.getInstance().constructServerInfo(name, instance.getAddress(), template.getMotd(), template.isRestricted());
		File folder = new File(origin.getRunningServerFolder(), name);
		if (folder.exists()) {
			if (containsName(name)) {
				//TODO maybe handle stopping the already running server?
				return;
			}
			//TODO handle deletion if the user doesn't want it for some reason.
			folder.delete();
			Files.delete(folder.toPath());
		}
		folder.mkdir();
		template.copyToDirectory(folder);
		ProcessBuilder processBuilder = new ProcessBuilder(commands).directory(folder);

		// Start
		starting.put(instance, new RunningProperties(info, processBuilder.start()));
		ProxyServer.getInstance().getServers().put(name, info);
	}

	public void shutdown(Instance instance) throws IOException {
		if (instance.getState() != State.RUNNING)
			return;
		RunningProperties properties = getRunningProperties(instance).get();
		Process process = properties.getProcess();
		OutputStream outputStream = process.getOutputStream();
		outputStream.write("stop".getBytes());
		outputStream.flush();
		outputStream.close();
		ServerInfo info = properties.getServerInfo();
		ProxyServer.getInstance().getServers().remove(info.getName());
		System.gc();
		ProxyServer.getInstance().getScheduler().schedule(origin.getRegistrar(), () -> {
			File folder = new File(origin.getRunningServerFolder(), info.getName());
//			if (instance.getTemplate().isSaving())
//				Files.copy(folder, directory);
			try {
				Files.deleteIfExists(folder.toPath());
			} catch (IOException e) {}
			// Just delete already
			folder.delete();
			process.destroy();
			running.remove(instance);
		}, 350, TimeUnit.MILLISECONDS);
	}

	public void shutdownAll() throws IOException {
		for (Iterator<Instance> iterator = running.keySet().iterator(); iterator.hasNext();)
			shutdown(iterator.next());
		running.clear();
		for (Iterator<Instance> iterator = starting.keySet().iterator(); iterator.hasNext();)
			shutdown(iterator.next());
		starting.clear();
	}

	public Optional<RunningProperties> getRunningProperties(Instance instance) {
		return running.entrySet().stream()
				.filter(entry -> entry.getKey().equals(instance))
				.map(Entry::getValue)
				.findFirst();
	}

	public boolean isStarting(Instance instance) {
		return starting.containsKey(instance);
	}

	public boolean isRunning(Instance instance) {
		return running.containsKey(instance);
	}

}
