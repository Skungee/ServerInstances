package com.skungee.serverinstances;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;

import com.sitrica.japson.gson.JsonObject;
import com.sitrica.japson.server.JapsonServer;
import com.sitrica.japson.shared.Executor;
import com.skungee.serverinstances.objects.RunningProperties;
import com.skungee.serverinstances.objects.Template;
import com.skungee.serverinstances.utils.Utils;

import net.md_5.bungee.api.AbstractReconnectHandler;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

public class ServerInstances implements Listener {

	final File DATA_FOLDER, INSTANCES_FOLDER, SAVED_FOLDER, TEMPLATE_FOLDER, RUNNING_SERVERS_FOLDER, RUN_SCRIPTS;
	private final List<Instance> instances = new ArrayList<>();
	private final TemplateLoader templateLoader;
	private final ServerManager manager;
	private Configuration configuration;
	private InetAddress bindAddress;
	private final Plugin plugin;
	private JapsonServer japson;
	private final int max;

	public ServerInstances(Plugin plugin) throws ClassNotFoundException, SQLException, IOException {
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
			bindAddress = InetAddress.getByName(configuration.getString("instances.address-bind", "127.0.0.1"));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		templateLoader = new TemplateLoader(this);
		manager = new ServerManager(this);
		max = configuration.getInt("instances.max-servers", 25);
		ProxyServer.getInstance().getPluginManager().registerListener(plugin, this);
		try {
			japson = new JapsonServer(configuration.getString("bootloader.address-bind", "127.0.0.1"), configuration.getInt("bootloader.port", 6110))
					.setPassword(configuration.getString("bootloader.password", "serverinstances"));
			if (configuration.getBoolean("bootloader.debug"))
				japson.enableDebug();
			japson.registerHandlers(new Executor(0x01) {
				@Override
				public void execute(InetAddress packetAddress, int packetPort, JsonObject object) {
					int port = object.get("port").getAsInt();
					String address = object.get("address").getAsString();
					InetSocketAddress socketAddress = new InetSocketAddress(address, port);
					ProxyServer.getInstance().getServers().values().stream()
							.filter(serverInfo -> serverInfo.getSocketAddress().equals(socketAddress))
							.findFirst()
							.ifPresent(manager::started);
				}
			});
		} catch (UnknownHostException | SocketException e) {
			e.printStackTrace();
		}
		//If the bungeecord gets forced closed.
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (instances.isEmpty())
				return;
			for (Instance instance : instances) {
				try {
					manager.shutdown(instance);
					Thread.sleep(2000L);
				} catch (InterruptedException | IOException ignored) {}
			}
		}));
	}

	private void loadConfiguration() {
		File file = new File(DATA_FOLDER, "serverinstances-configuration.yml");
		try (InputStream in = plugin.getResourceAsStream("serverinstances-configuration.yml")) {
			if (!file.exists())
				Files.copy(in, file.toPath());
			configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
		} catch (IOException e) {
			System.out.println("Could not create and save serverinstances configuration.yml.");
		}
	}

	public Instance createInstance(Template template) throws IOException, IllegalAccessException {
		if (template.isDisabled())
			throw new IllegalAccessException("The template " + template.getName() + " is disabled.");
		if (!template.doesAllowDuplicates() && instances.stream().anyMatch(instance -> instance.getTemplate().equals(template)))
			throw new IllegalAccessException("The template " + template.getName() + " is already running, and doesn't allow duplicates.");
		int port = template.getPort();
		if (port <= 0)
			port = Utils.findPort(bindAddress, configuration.getInt("instances.minimum-port", 3000), configuration.getInt("instances.maximum-port", 27000));
		InetSocketAddress address = new InetSocketAddress(bindAddress, port);
		if (Utils.isTaken(address))
			throw new IllegalAccessException("The port " + port + " is already in use for " + bindAddress.getHostAddress());
		Instance instance = new Instance(template, address);
		instances.add(instance);
		return instance;
	}

	public ServerManager getServerManager() {
		return manager;
	}

	public File getRunningServerFolder() {
		return RUNNING_SERVERS_FOLDER;
	}

	public File getSavedServerFolder() {
		return SAVED_FOLDER;
	}

	public List<Instance> getInstances() {
		return Collections.unmodifiableList(instances);
	}

	public Set<Template> getTemplates() {
		return templateLoader.getTemplates();
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

	@EventHandler
	public void onServerKickEvent(ServerKickEvent event) {
		if (!configuration.getBoolean("instances.fallback.enabled")) return;

		ProxyServer proxy = ProxyServer.getInstance();
		ProxiedPlayer player = event.getPlayer();
		ServerInfo kickedFrom;

		if (player.getServer() != null) {
			kickedFrom = player.getServer().getInfo();
		} else if (proxy.getReconnectHandler() != null) {
			kickedFrom = proxy.getReconnectHandler().getServer(player);
		} else {
			kickedFrom = AbstractReconnectHandler.getForcedHost(player.getPendingConnection());
			if (kickedFrom == null)
				kickedFrom = proxy.getServerInfo(player.getPendingConnection().getListener().getServerPriority().get(0));
		}

		ServerInfo fallback = proxy.getServerInfo(configuration.getString("instances.fallback.server"));

		if (fallback == null || kickedFrom == null)
			return;
		if (kickedFrom.equals(fallback))
			return;

		String reason = BaseComponent.toLegacyText(event.getKickReasonComponent());

		String[] messages = configuration.getString("instances.fallback.message")
				.replace("%previous%", kickedFrom.getName())
				.replace("%kickmsg%", reason)
				.split("\n");

		event.setCancelled(true);
		event.setCancelServer(fallback);

		if (messages.length > 0 && !messages[0].equals("")) {
			for (String line : messages)
				player.sendMessage(new TextComponent(Utils.color(line)));
		}
	}

	public enum State {RUNNING, STARTING, IDLE}

	public class Instance {

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
