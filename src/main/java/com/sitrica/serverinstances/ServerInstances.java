package com.sitrica.serverinstances;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import com.sitrica.serverinstances.database.H2Database;
import com.sitrica.serverinstances.database.InstanceMessage;
import com.sitrica.serverinstances.database.InstanceMessageSerializer;
import com.sitrica.serverinstances.handlers.MessageHandler;
import com.sitrica.serverinstances.objects.RunningProperties;
import com.sitrica.serverinstances.objects.Template;
import com.sitrica.serverinstances.utils.Utils;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class ServerInstances {

	private final File DATA_FOLDER, INSTANCES_FOLDER, SAVED_FOLDER, TEMPLATE_FOLDER, RUNNING_SERVERS_FOLDER, RUN_SCRIPTS;
	private final List<Instance> instances = new ArrayList<>();
	private final H2Database<InstanceMessage> database;
	private final ServerManager manager;
	private Configuration configuration;
	private InetAddress bindAddress;
	private final Plugin plugin;
	private final int max;

	public ServerInstances(Plugin plugin) throws ClassNotFoundException, SQLException {
		this.plugin = plugin;
		this.DATA_FOLDER = new File(plugin.getDataFolder().getParentFile(), "ServerInstances");
		if (!DATA_FOLDER.exists())
			DATA_FOLDER.mkdir();
		INSTANCES_FOLDER = new File(DATA_FOLDER, "instances");
		if (!INSTANCES_FOLDER.exists())
			INSTANCES_FOLDER.mkdir();
		TEMPLATE_FOLDER = new File(INSTANCES_FOLDER, "templates");
		if (!TEMPLATE_FOLDER.exists())
			TEMPLATE_FOLDER.mkdir();
		RUNNING_SERVERS_FOLDER = new File(INSTANCES_FOLDER, "running-servers");
		if (!RUNNING_SERVERS_FOLDER.exists())
			RUNNING_SERVERS_FOLDER.mkdir();
		RUN_SCRIPTS = new File(INSTANCES_FOLDER, "run-scripts");
		if (!RUN_SCRIPTS.exists())
			RUN_SCRIPTS.mkdir();
		SAVED_FOLDER = new File(INSTANCES_FOLDER, "saved-servers");
		if (!SAVED_FOLDER.exists())
			SAVED_FOLDER.mkdir();
		loadConfiguration();
		try {
			bindAddress = InetAddress.getByName(configuration.getString("instances.bind-address", "127.0.0.1"));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		database = new H2Database<InstanceMessage>(DATA_FOLDER, "server-instances", InstanceMessage.class, ImmutableMap.of(InstanceMessage.class, new InstanceMessageSerializer()));
		manager = new ServerManager(this, database);
		max = configuration.getInt("instances.max-servers", 25);
		//If the bungeecord gets forced closed.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				if (instances.isEmpty())
					return;
				for (Instance instance : instances) {
					try {
						manager.shutdown(instance);
						Thread.sleep(2000L);
					} catch (InterruptedException | IOException e) {}
				}
			}
		});
	}

	private void loadConfiguration() {
		File file = new File(DATA_FOLDER, "configuration.yml");
		try (InputStream in = ServerInstances.class.getResourceAsStream("configuration.yml")) {
			if (!file.exists())
				Files.copy(in, file.toPath());
			configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
		} catch (IOException e) {
			System.out.println("Could not create and save serverinstances configuration.yml.");
		}
	}

	public Instance createInstance(Template template) throws IOException {
		int port = template.getPort();
		if (port <= 0)
			port = Utils.findPort(bindAddress, configuration.getInt("instances.minimum-port", 3000), configuration.getInt("instances.maximum-port", 27000));
		InetSocketAddress address = new InetSocketAddress(bindAddress, port);
		Instance instance = new Instance(template, address);
		instances.add(instance);
		return instance;
	}

	public <H extends MessageHandler> void addHandler(H handler) {
		manager.addHandler(handler);
	}

	public List<Instance> getInstances() {
		return Collections.unmodifiableList(instances);
	}

	public File getRunningServerFolder() {
		return RUNNING_SERVERS_FOLDER;
	}

	public Plugin getRegistrar() {
		return plugin;
	}

	public File getDataFolder() {
		return DATA_FOLDER;
	}

	public int getMaxServers() {
		return max;
	}

	public enum State {RUNNING, STARTING, IDLE}

	class Instance {

		private final InetSocketAddress address;
		private final Template template;

		public Instance(Template template, InetSocketAddress address) {
			this.template = template;
			this.address = address;
		}

		public Optional<RunningProperties> getRunningProperties() {
			return manager.getRunningProperties(this);
		}

		public InetSocketAddress getAddress() {
			return address;
		}

		public Template getTemplate() {
			return template;
		}

		public State getState() {
			if (manager.isStarting(this))
				return State.STARTING;
			if (manager.isRunning(this))
				return State.RUNNING;
			return State.IDLE;
		}

	}

}
