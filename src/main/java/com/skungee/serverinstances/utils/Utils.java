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

	public static int findPort(InetAddress address, int start, int max) throws IOException {
		int port = start;
		IOException lastException = null;
		while (port < max) {
			ServerSocket socket = null;
			try {
				socket = new ServerSocket();
				socket.bind(new InetSocketAddress(address, port));
				socket.setReuseAddress(true);
				return port;
			} catch (IOException e) {
				lastException = e;
			} finally {
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e) {}
				}
			}
			port++;
		}
		if (lastException != null)
			throw lastException;
		return -1;
	}

}
