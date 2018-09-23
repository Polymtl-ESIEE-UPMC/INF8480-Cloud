package server;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import shared.FileServerInterface;

public class FileServer implements FileServerInterface {

	public static void main(String[] args) {
		FileServer server = new FileServer();
		server.run();
	}

	public FileServer() {
		super();
	}

	private void run() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		try {
			FileServerInterface stub = (FileServerInterface) UnicastRemoteObject
					.exportObject(this, 0);

			Registry registry = LocateRegistry.getRegistry();
			registry.rebind("fileServer", stub);
			System.out.println("Server ready.");
		} catch (ConnectException e) {
			System.err
					.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}
	
	/*
	 * Méthodes accessibles par RMI. 
	 */
	 
	@Override
	public void createFile(String fileName) throws RemoteException {
		return ;
	}
	
	@Override
	public String[] listFiles() throws RemoteException {
		return new String[5];
	}
	
	@Override
	public byte[] getFile(String name, String checksum) throws RemoteException {
		return new byte[5];
	}

	@Override
	public String lockFile(String name, String checksum) throws RemoteException {
		return "";
	}

	@Override
	public String pushFile(String name, byte[] fileContent)  throws RemoteException {
		return "";
	}

	@Override
	public byte[] syncLocalDirectory() throws RemoteException {
		return new byte[5];
	}
}
