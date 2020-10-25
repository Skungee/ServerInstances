package com.skungee.serverinstances.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

public class Utils {

	public static boolean isReachable(SocketAddress address) {
		Socket socket = new Socket();
		try {
			socket.connect(address, 500);
			socket.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean isTaken(InetSocketAddress address) {
		ServerSocket socket = null;
		try {
			socket = new ServerSocket();
			socket.bind(address);
			socket.setReuseAddress(true);
			return false;
		} catch (IOException e) {
			return true;
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {}
			}
		}
	}

	public static int findPort(InetAddress address, int start, int max) {
		int port = start;
		while (port < max) {
			if (!isTaken(new InetSocketAddress(address, port)))
				return port;
			port++;
		}
		return -1;
	}

}
