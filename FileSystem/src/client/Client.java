package client;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import shared.AuthServerInterface;
import shared.FileServerInterface;

public class Client {
	static int size;
	static byte[] param;
	public static void main(String[] args) {
		String distantHostname = null;

		if (args.length > 0) {
			distantHostname = args[0];
			size = Integer.parseInt(args[1]);
			param = new byte[(int)(Math.pow(10, size))];
		}

		Client client = new Client(distantHostname);
		client.run();
	}

	public Client(String distantServerHostname) {
		super();

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}
	}

	private void run() {

	}
}
