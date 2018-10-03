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
import java.util.ArrayList;
import java.util.List;

import shared.Account;
import shared.AuthServerInterface;
import shared.ServerDescription;

public class AuthServer implements AuthServerInterface {
	//dossier où les comptes clients sont sauvegardés
	private static final String ACCOUNTS_DIR_NAME = "accounts";
	//dossier où les répartiteurs sont sauvegardés
	private static final String REPARTITEURS_DIR_NAME = "repartiteurs";

	ServerDescription repartiteurDescription = null;
	List<ServerDescription> serverDescriptions = null;

	public static void main(String[] args) {
		//crée les dossiers s'il n'existe pas
		File accountsDir = new File(ACCOUNTS_DIR_NAME);
		accountsDir.mkdir();
		File repartiteursDir = new File(REPARTITEURS_DIR_NAME);
		repartiteursDir.mkdir();
		AuthServer server = new AuthServer();
		server.run();
	}

	public AuthServer() {
		super();
		serverDescriptions = new ArrayList<ServerDescription>();
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
	 
	/**
	 * Crée un nouveau compte à partir d'un nom d'utilisateur et un mot de passe
	 * retourne true si la création a réussie, sinon false
	 */
	@Override
	public boolean newAccount(Account account) throws RemoteException {
		return newAuth(account, ACCOUNTS_DIR_NAME);

	}

	@Override
	public boolean newRepartiteur(Account account) throws RemoteException {
		return newAuth(account, REPARTITEURS_DIR_NAME);
	}

	private boolean newAuth(Account account, String dirName){
		String filePath = dirName + "/" + account.userName;
		File file = new File(filePath);
		if(file.exists()) { 
			// le compte existe déjà
			return false;
		}

		try (PrintStream ps = new PrintStream(filePath)) {
			//écrit les informations de compte dans le fichier
			ps.println(account.userName);
			ps.println(account.password);
		} catch (FileNotFoundException e){
			return false;
		}

		return true;
	}

	@Override
	public boolean verifyAccount(Account account) throws RemoteException{
		return verifyAuth(account, ACCOUNTS_DIR_NAME);
	}

	/**
	 * Vérifie si le compte reçu existe et que le mot de passe est valide
	 */
	@Override
	public boolean verifyRepartiteur(Account account) throws RemoteException {
		return verifyAuth(account, REPARTITEURS_DIR_NAME);
	}

	private boolean verifyAuth(Account account, String dirName){
		String filePath = dirName + "/" + account.userName;
		String userName = "", validPass = "";

		try {
			//lis le contenu du fichier
			BufferedReader fileReader = new BufferedReader(new FileReader(filePath));
			try {
				userName = fileReader.readLine();
				validPass = fileReader.readLine();
			} catch (IOException e) {
				System.err.println("Un problème inconnu est survenu : " + e.getMessage());
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
		
		//retourne si le compte est valide
		return account.userName.equals(userName) && account.password.equals(validPass);
	}

	@Override
	public ServerDescription getRepartiteur() throws RemoteException{
		return repartiteurDescription;
	}
	
	@Override
	public boolean registerCalculationServer(ServerDescription serverDescription) throws RemoteException{
		return serverDescriptions.add(serverDescription);
	}
	
	@Override
	public List<ServerDescription> getCalculationServers() throws RemoteException{
		return serverDescriptions;
	}
}
