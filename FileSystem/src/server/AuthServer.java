package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import shared.Account;
import shared.AuthServerInterface;

public class AuthServer implements AuthServerInterface {
	private static final String ACCOUNTS_DIR_NAME = "accounts";

	public static void main(String[] args) {
		File accountsDir = new File(ACCOUNTS_DIR_NAME);
		accountsDir.mkdir();
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
		String filePath = ACCOUNTS_DIR_NAME + "/" + login;
		File file = new File(filePath);
		if(file.exists()) { 
			// le compte existe déjà
			return false;
		}

		try (PrintStream ps = new PrintStream(filePath)) {
			ps.println(login);
			ps.println(password);
		} catch (FileNotFoundException e){
			return false;
		}

		return true;
	}

	@Override
	public boolean verifyAccount(Account account) throws RemoteException {
		String filePath = ACCOUNTS_DIR_NAME + "/" + account.userName;
		String userName = "", validPass = "";

		try {
			BufferedReader fileReader = new BufferedReader(new FileReader(filePath));
			try {
				userName = fileReader.readLine();
				validPass = fileReader.readLine();
			} catch (IOException e) {
				System.err.println("?");
			} finally {
				try {
					if (fileReader != null)
						fileReader.close();
				} catch (IOException e) {
					System.err.println("Un problème inconnu est survenu : " + e.getMessage());
				}
			}
		} catch (FileNotFoundException e) {
			return false;
		}
		
		return account.userName.equals(userName) && account.password.equals(validPass);
	}
}
