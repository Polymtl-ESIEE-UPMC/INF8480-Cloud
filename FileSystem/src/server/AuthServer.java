package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import shared.AuthServerInterface;

public class AuthServer implements AuthServerInterface {
	public static void main(String[] args) {
		AuthServer server = new AuthServer();
		server.run();
	}

	public AuthServer() {
		super();
	}

	private void run() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		try {
			AuthServerInterface stub = (AuthServerInterface) UnicastRemoteObject
					.exportObject(this, 0);

			Registry registry = LocateRegistry.getRegistry();
			registry.rebind("authServer", stub);
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
	public boolean newAccount(String login, String password) throws RemoteException {
		File file = new File(login);
		if(file.exists()) { 
			// account already exists
			return false;
		}

		try {
			PrintStream ps = new PrintStream(login);
			ps.println(login);
			ps.println(password);
		} catch (FileNotFoundException e){
			return false;
		}

		return true;
	}

	@Override
	public boolean verifyAccount(String login, String password) throws RemoteException {
		try{
			FileInputStream fileReader = new FileInputStream(login);
			
		} catch (FileNotFoundException e){
			return false;
		}
		return true;
	}
}
