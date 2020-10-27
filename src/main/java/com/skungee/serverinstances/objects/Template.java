package com.skungee.serverinstances.objects;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.skungee.serverinstances.utils.Utils;

public class Template {

	private final List<String> commands = new ArrayList<>();
	private final String name, xmx, xms, jarName, motd;
	private final boolean save, restricted, duplicates;
	private final File folder;
	private boolean disabled;
	private final int port;

	public Template(File folder, String motd, String name, String xmx, String xms, String jarName, boolean save, boolean duplicates, String... commands) {
		this(folder, false, motd, name, -1, xmx, xms, jarName, save, duplicates, commands);
	}

	public Template(File folder, boolean restricted, String motd, String name, String xmx, String xms, String jarName, boolean save, boolean duplicates, String... commands) {
		this(folder, restricted, motd, name, -1, xmx, xms, jarName, save, duplicates, commands);
	}

	public Template(File folder, boolean restricted, String motd, String name, int port, String xmx, String xms, String jarName, boolean save, boolean duplicates, String... commands) {
		this.restricted = restricted;
		this.duplicates = duplicates;
		this.jarName = jarName;
		this.folder = folder;
		this.motd = motd;
		this.name = name;
		this.save = save;
		this.port = port;
		this.xmx = xmx;
		this.xms = xms;
	}

	public void copyToDirectory(File directory) throws IOException {
		Utils.copyDirectory(folder, directory);
	}

	public List<String> getAdditionalCommands() {
		return commands;
	}

	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}

	public boolean doesAllowDuplicates() {
		return duplicates;
	}

	public boolean isRestricted() {
		return restricted;
	}

	public boolean isDisabled() {
		return disabled;
	}

	public String getJarName() {
		return jarName;
	}

	public boolean isSaving() {
		return save;
	}

	public String getName() {
		return name;
	}

	public File getFolder() {
		return folder;
	}

	public String getMotd() {
		return motd;
	}

	public String getXmx() {
		return xmx;
	}

	public String getXms() {
		return xms;
	}

	public int getPort() {
		return port;
	}

}
