package client;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import shared.AuthServerInterface;
import shared.FileServerInterface;

public class Client {

	public static void main(String[] args) {
		String distantHostname = null;

		// gérer les args

		Client client = new Client(distantHostname);
		client.run();
	}

	private AuthServerInterface authServer = null;
	private FileServerInterface fileServer = null;

	public Client(String distantServerHostname) {
		super();

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}
		authServer = loadAuthServer("127.0.0.1");
		//fileServer = loadFileServer("127.0.0.1");
	}

	private AuthServerInterface loadAuthServer(String hostname) {
		AuthServerInterface stub = null;

		try {
			Registry registry = LocateRegistry.getRegistry(hostname);
			stub = (AuthServerInterface) registry.lookup("authServer");
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage() + "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}

		return stub;
	}

	private FileServerInterface loadFileServer(String hostname) {
		FileServerInterface stub = null;

		try {
			Registry registry = LocateRegistry.getRegistry(hostname);
			stub = (FileServerInterface) registry.lookup("fileServer");
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage() + "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}

		return stub;
	}

	private void run() {
		if (authServer != null) {
			try {
				boolean response;
				response = authServer.newAccount("test", "admin");
				System.out.print(response);
			} catch (RemoteException e) {
				System.out.println("Erreur: " + e.getMessage());
			}
		}
	}
}
