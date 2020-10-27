package com.skungee.serverinstances;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.skungee.serverinstances.objects.Template;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class TemplateLoader {

	private final Map<WatchKey, File> keys = new HashMap<>();
	private final Set<Template> templates = new HashSet<>();
	private final ServerInstances instance;
	private final WatchService watcher;

	public TemplateLoader(ServerInstances instance) throws IOException {
		this.instance = instance;
		watcher = FileSystems.getDefault().newWatchService();
		update();
	}

	public void update() {
		for (File directory : instance.TEMPLATE_FOLDER.listFiles()) {
			if (!directory.isDirectory())
				continue;
			update(directory).ifPresent(template -> templates.add(template));
			try {
				WatchKey key = directory.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
				keys.put(key, directory);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		ProxyServer.getInstance().getScheduler().runAsync(instance.getRegistrar(), () -> {
			boolean running = true;
			while (running) {
				WatchKey key;
				try {
					key = watcher.take();
				} catch (InterruptedException e) {
					continue;
				}
				File directory = keys.get(key);
				if (directory == null)
					continue;
				Optional<Template> optional = update(directory);
				if (!optional.isPresent())
					return;
				Template template = optional.get();
				templates.removeIf(t -> t.getName().equals(template.getName()));
				templates.add(template);
				System.out.println("Added template " + template.getName());
				// reset key and remove from set if directory is no longer accessible.
				boolean valid = key.reset();
				if (!valid) {
					keys.remove(key);
					if (keys.isEmpty())
						break;
				}
			}
		});
	}

	private Optional<Template> update(File directory) {
		Optional<File> optional = Arrays.stream(directory.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File file, String name) {
				return name.equals("template-configuration.yml");
			}
		})).findFirst();
		if (!optional.isPresent())
			return Optional.empty();
		File file = optional.get();
		try {
			Configuration configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
			return Optional.of(loadTemplate(directory, configuration));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Optional.empty();
	}

	private Template loadTemplate(File directory, Configuration configuration) {
		String motd = configuration.getString("motd", "A ServerInstance server");
		boolean restricted = configuration.getBoolean("restricted", false);
		boolean duplicates = configuration.getBoolean("duplicates", true);
		String jar = configuration.getString("jar-name", "spigot.jar");
		String name = configuration.getString("name", "Template");
		boolean save = configuration.getBoolean("save", false);
		String xms = configuration.getString("xms", "512M");
		String xmx = configuration.getString("xmx", "1G");
		int port = configuration.getInt("port");
		String[] commands = configuration.getStringList("command-arguments").stream().toArray(String[]::new);
		Template template = null;
		if (port > 0)
			template = new Template(directory, restricted, motd, name, port, xmx, xms, jar, save, duplicates, commands);
		template = new Template(directory, restricted, motd, name, xmx, xms, jar, save, duplicates, commands);
		if (configuration.getBoolean("disabled", false))
			template.setDisabled(true);
		return template;
	}

	public Set<Template> getTemplates() {
		return templates;
	}

}
