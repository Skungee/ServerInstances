package com.sitrica.serverinstances.objects;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.config.Configuration;
import net.npcnetwork.bungeecord.NpcBungee;
import net.npcnetwork.bungeecord.utils.Utils;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.sitrica.serverinstances.ServerManager;

public class WrappedServer {

	private final long started = System.currentTimeMillis();
	private InputStream inputStream, errors;
	private ProcessBuilder processBuilder;
	private boolean isRunning, useSaved, isBlocking;
	private OutputStream outputStream;
	private final String template;
	private String name, motd;
	private Process process;
	private File folder;
	private int port;

	private final List<String> commands = new ArrayList<>();
	private final Configuration configuration;
	private final ServerManager serverManager;
	private final NpcBungee instance;
	private String Xmx, Xms;

	/**
	 * Create and start a wrapped server
	 * 
	 * @param instance The Plugin running the server.
	 * @param existing if an existing server is to be cloned.
	 * @param useSaved if a saved folder was found, should it be used.
	 */
	public WrappedServer(NpcBungee instance, WrappedServer existing, boolean useSaved) {
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.commands.addAll(existing.getCommands());
		this.template = existing.getTemplate();
		this.folder = existing.getFolder();
		this.name = existing.getName();
		this.folder = setupFolder();
		this.useSaved = useSaved;
		this.instance = instance;
		this.Xmx = existing.Xmx;
		this.Xms = existing.Xms;
		if (this.folder == null)
			return;
		this.processBuilder = setupProcessBuilder();
		startup();
	}

	public WrappedServer(NpcBungee instance, String name, String template, String Xmx, boolean useSaved, int port) {
		this.port = port;
		this.name = name;
		this.instance = instance;
		this.template = template;
		this.useSaved = useSaved;
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.Xms = configuration.getString("instances.default-Xms");
		this.Xmx = Xmx;
		this.folder = setupFolder();
		if (this.folder == null)
			return;
		prepare();
		startup();
	}

	public WrappedServer(NpcBungee instance, String name, String template, boolean useSaved, int port) {
		this.port = port;
		this.name = name;
		this.instance = instance;
		this.template = template;
		this.useSaved = useSaved;
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.Xms = configuration.getString("instances.default-Xms");
		this.Xmx = configuration.getString("instances.default-Xmx");
		this.folder = setupFolder();
		if (this.folder == null)
			return;
		prepare();
		startup();
	}

	public WrappedServer(NpcBungee instance, String name, String template, boolean useSaved) {
		this.name = name;
		this.instance = instance;
		this.template = template;
		this.useSaved = useSaved;
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.Xms = configuration.getString("instances.default-Xms");
		this.Xmx = configuration.getString("instances.default-Xmx");
		this.folder = setupFolder();
		if (this.folder == null)
			return;
		prepare();
		startup();
	}

	//For starting a brand new server and checking if it exists.
	public WrappedServer(NpcBungee instance, String name, String template, String Xmx, String Xms, boolean useSaved) {
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.useSaved = useSaved;
		this.template = template;
		this.instance = instance;
		this.name = template;
		this.Xmx = Xmx;
		this.Xms = Xms;
		this.folder = setupFolder();
		if (this.folder == null)
			return;
		prepare();
		startup();
	}

	public WrappedServer(NpcBungee instance, String name, String template, List<String> commands, boolean useSaved) {
		this.commands.addAll(commands);
		this.useSaved = useSaved;
		this.template = template;
		this.instance = instance;
		this.name = name;
		this.folder = setupFolder();
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.Xms = configuration.getString("instances.default-Xms");
		this.Xmx = configuration.getString("instances.default-Xmx");
		if (this.folder == null)
			return;
		this.processBuilder = setupProcessBuilder();
		startup();
	}

	private ProcessBuilder setupProcessBuilder() {
		File runningFolder = new File(serverManager.getRunningServerFolder(), name);
		return new ProcessBuilder(commands).directory(runningFolder);
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}

	public InputStream getInputStream() {
		return inputStream;
	}

	public InputStream getErrorStream() {
		return errors;
	}

	public List<String> getCommands() {
		return commands;
	}

	public long getStartedTime() {
		return started;
	}

	public Process getProcess() {
		return process;
	}

	public File getFolder() {
		return folder;
	}

	public String getName() {
		return name;
	}

	public String getXmx() {
		return validate(Xmx, "default-Xmx");
	}

	/*
	 * This will cause the server to restart.
	*/
	public void setXmx(String xmx) {
		Xmx = xmx;
		restart(false);
	}

	public String getXms() {
		return validate(Xms, "default-Xms");
	}

	/*
	 * This will cause the server to restart.
	*/
	public void setXms(String xms) {
		Xms = xms;
		restart(false);
	}

	public boolean canUseSaved() {
		return useSaved;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public String getTemplate() {
		return template;
	}

	public String getMotd() {
		return motd;
	}

	public int getPort() {
		return port;
	}

	public void setRunning(Boolean running) {
		if (this.isRunning && !running) shutdown();
		this.isRunning = running;
	}

	//Check that the output is logical.
	private String validate(String input, String node) {
		input = input.replaceAll("( |-)", "");
		if (!input.contains("M") || !input.contains("G")) input = Integer.parseInt(input) + "M";
		if (Integer.parseInt(input.replaceAll("(M|G)", "")) < 50) input = configuration.getString("instances." + node, "250M");
		return "-" + input;
	}

	public File getJar() {
		File[] jars = folder.listFiles(file -> file.getName().equalsIgnoreCase(configuration.getString("instances.jar-name", "spigot.jar")));
		return (jars == null || jars.length <= 0) ? null : jars[0];
	}

	@SuppressWarnings("deprecation")
	public ServerInfo getServerInfo() {
		InetSocketAddress address = new InetSocketAddress("0.0.0.0", getPort());
		ProxyServer proxy = instance.getProxy();
		return proxy.getServers().values().stream()
				.filter(info -> info.getAddress().equals(address))
				.findFirst()
				.orElse(proxy.constructServerInfo(name, address, getMotd(), false));
	}

	private File setupFolder() {
		int spot = 1;
		String nameCopy = name;
		while (serverManager.containsName(nameCopy)) {
			nameCopy = name + "-" + spot;
			spot++;
		}
		this.name = nameCopy;
		File runningFolder = new File(serverManager.getRunningServerFolder(), name);
		if (runningFolder.exists()) {
			instance.consoleMessage("There was already a server directory under the name: " + name);
			if (serverManager.getInstances().stream().anyMatch(instance -> instance.getName().equalsIgnoreCase(name))) {
				instance.consoleMessage("There was already a server running under the name: " + name + ". Aborting creation.");
				//TODO maybe handle stopping the already running server?
				return null;
			}
			//TODO handle deletion if the user doesn't want it for some reason.

			// Recursively delete files since java can't delete non-empty folders

			Utils.deleteDirectory(runningFolder);
		}
		File templateFolder = new File(serverManager.getTemplateFolder() + File.separator + template);
		if (!templateFolder.exists() || templateFolder.listFiles() == null || templateFolder.listFiles().length <= 0) {
			instance.consoleMessage("The template: \"" + template + "\" was empty or non existant.");
			return null;
		} else {
			this.folder = templateFolder;
			if (getJar() == null) {
				instance.consoleMessage("The jar file for template: \"" + template + "\" was not found. Make sure the name matches what is in the config.yml");
				return null;
			}
		}
		if (serverManager.getInstances().size() >= configuration.getInt("instances.max-servers", 20)) {
			instance.consoleMessage("The maximum amount of server instances has been reached!");
			return null;
		}
		File[] files = folder.listFiles(file -> file.getName().endsWith(".properties"));
		if (files == null || files.length <= 0) {
			instance.consoleMessage("The template: \"" + template + "\" was missing a server.properties file.");
			return null;
		}
		File savedFolder = new File(serverManager.getSavedFolder() + File.separator + name);
		if (savedFolder.exists() && useSaved)
			folder = savedFolder;
		try {
			Utils.copyDirectory(folder, runningFolder);
		} catch (IOException exception) {
			instance.exception(exception, "Failed to copy the directory of template: " + template);
			return null;
		}
		setupPort();
		findMotd();
		return folder;
	}

	public void findMotd() {
		if (folder == null)
			return;
		File[] files = folder.listFiles(file -> file.getName().endsWith(".properties"));
		Properties properties = new Properties();
		InputStream input = null;
		try {
			input = new FileInputStream(files[0]);
			properties.load(input);
			this.motd = properties.getProperty("motd").replaceAll(Pattern.quote("%server%"), name);
		} catch (IOException exception) {
			instance.exception(exception, "There was an error loading the properties of template: " + name);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException exception) {
					instance.exception(exception, "There was an error closing the InputStream of the properties reader for template: " + name);
				}
			}
		}
	}

	public void setupPort() {
		if (folder == null)
			return;
		File[] files = folder.listFiles(file -> file.getName().equals("server.properties"));
		Properties properties = new Properties();
		InputStream input = null;
		Set<Entry<Object, Object>> set = new HashSet<>();
		try {
			input = new FileInputStream(files[0]);
			properties.load(input);
			set = properties.entrySet();
		} catch (IOException exception) {
			instance.exception(exception, "There was an error loading the properties while setting up port of template: " + name);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException exception) {
					instance.exception(exception, "There was an error closing the InputStream of the properties reader for template: " + name);
				}
			}
		}
		OutputStream output = null;
		try {
			output = new FileOutputStream(files[0]);
			if (port <= 0)
				this.port = Utils.findPort(configuration.getInt("instances.minimum-port", 25000), configuration.getInt("instances.maximum-port", 27000));
			for (Entry<Object, Object> entry : set) {
				if (entry.getKey().equals("server-port")) {
					properties.setProperty("server-port", this.port + "");
				} else if (entry.getKey().equals("query.port")) {
					properties.setProperty("query.port", this.port + "");
				} else properties.setProperty((String)entry.getKey(), (String) entry.getValue());
			}
			properties.store(output, null);
		} catch (IOException exception) {
			instance.exception(exception, "There was an error loading the properties of template: " + name);
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (IOException exception) {
					instance.exception(exception, "There was an error closing the OutputStream of the properties reader for template: " + name);
				}
			}
		}
	}

	private void prepare() {
		commands.add("java");
		if (Xmx.matches("(\\-Xmx)(.*)")) {
			commands.add(getXmx());
		} else {
			commands.add("-Xmx" + Xmx);
		}
		if (Xms.matches("(\\-Xms)(.*)")) {
			commands.add(getXms());
		} else {
			commands.add("-Xms" + Xms);
		}
		boolean isWindows = System.getProperty("os.name").matches("(?i)(.*)(windows)(.*)");
		if (isWindows) commands.add("-Djline.terminal=jline.UnsupportedTerminal");
		for (String command : configuration.getStringList("instances.command-arguments")) {
			commands.add(command);
		}
		commands.add("-jar");
		commands.add(getJar().getName());
		if (isWindows) commands.add("--nojline");
		processBuilder = setupProcessBuilder();
		/*linux screen support
		File screen = new File(ServerManager.getRunScriptsFolder(), "start-screen.sh");
		Object[] command = screen.exists() ? new String[]{"sh", ServerManager.getRunScriptsFolder() + "/start-screen.sh", name, ServerManager.getRunningServerFolder().getAbsolutePath(), getXmx(), getXms(), getJar().getName()} : (!getJar().getName().matches("^(?i)spigot.*\\.jar") ? new String[]{"screen", "-dmS", name, "java", getXmx(), getXms(), "-jar", getJar().getName()} : new String[]{"screen", "-dmS", name, "java", getXmx(), getXms(), "-Dcom.mojang.eula.agree=true", "-jar", getJar().getName()});
		ProcessBuilder processBuilder = new ProcessBuilder(new String[0]);
		processBuilder.command((String[])command);
		processBuilder.directory(ServerManager.getRunningServerFolder());
		getProcessRunner().queueProcess(this.getName(), processBuilder);
		*/
	}

	private void startup() {
		if (isRunning)
			return;
		setRunning(true);
		serverManager.starting(this);
		instance.consoleMessage("Starting up server " + name + "...");
		InetSocketAddress address = new InetSocketAddress("0.0.0.0", getPort());
		// If statement keeps the original motd
		if (!instance.getProxy().getServers().containsKey(name)) instance.getProxy().getServers().put(name, instance.getProxy().constructServerInfo(name, address, motd, false));
		try {
			process = processBuilder.start();
			inputStream = process.getInputStream();
			errors = process.getErrorStream();
			outputStream = process.getOutputStream();
			serverManager.getProcesses().add(process);
			//TODO make a system to read the console of this process
		} catch (IOException exception) {
			instance.exception(exception, "Failed to start server: " + name);
		}
	}

	public void shutdown() {
		if (!isRunning)
			return;
		this.isRunning = false;
		instance.consoleMessage("Stopping server \"" + name + "\"...");
		try {
			outputStream.write("stop".getBytes());
			outputStream.flush();
		} catch (IOException exception) {
		} finally {
			try {
				inputStream.close();
				outputStream.flush();
				outputStream.close();
			} catch (IOException e) {
				//e.printStackTrace();
			}
		}
		inputStream = null;
		outputStream = null;

		instance.getProxy().getServers().remove(name);
		System.gc();
		kill();

		instance.getProxy().getScheduler().schedule(instance, () -> {
			File delete = new File(serverManager.getRunningServerFolder(), name);
			Utils.deleteDirectory(delete);
			// Just delete already fuck sakes.
			delete.delete();
			try {
				Files.delete(delete.toPath());
			} catch (IOException e) {}
		}, 350, TimeUnit.MILLISECONDS);
	}

	public int getPlayerCount() {
		return getServerInfo().getPlayers().size();
	}

	public void join(ProxiedPlayer player) {
		if (isBlocking || !instance.getProxy().getServers().containsKey(name)) return;
		player.connect(instance.getProxy().getServers().get(name), ServerConnectEvent.Reason.COMMAND);
	}

	public void setBlocking(boolean blocking) {
		this.isBlocking = blocking;
	}

	private boolean autoSave = false;

	public void setAutoSave(boolean autoSave) {
		this.autoSave = autoSave;
	}

	public boolean isAutoSave() {
		return autoSave;
	}

	public void shutdown(boolean saving) {
		if (saving) {
			try {
				File savedFolder = new File(serverManager.getSavedFolder() + File.separator + name);
				if (savedFolder.exists())
					Utils.deleteDirectory(savedFolder);

				File runningFolder = new File(serverManager.getRunningServerFolder(), name);
				Utils.copyDirectory(runningFolder, savedFolder);
			} catch (IOException exception) {
				instance.exception(exception, "Failed to save the directory of server: " + template);
			}
		}
		shutdown();
	}

	public void restart(boolean save) {
		shutdown(save);
		prepare();
		startup();
	}

	public void kill() {
		process.destroy();
	}

}
