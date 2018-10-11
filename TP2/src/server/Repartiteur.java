package server;

import java.rmi.AccessException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import shared.AuthServerInterface;
import shared.RepartiteurInterface;
import shared.Account;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Repartiteur implements RepartiteurInterface {

	private static final String CREDENTIALS_FILENAME = "credentialsRepartiteur"; 
	public static void main(String[] args) {
		String authServerHostName = null;
		if(args.length > 0){
			authServerHostName = args[0];
		}
		Repartiteur server = new Repartiteur(authServerHostName);
		server.run();
	}

	private AuthServerInterface authServer = null;
	private final String userName = "tempName";
	private final String password = "temppassword";
	private Account account = null;
	
	public Repartiteur(String authServerHostName) {
		super();

		if (authServerHostName != null) {
			authServer = loadAuthServer(authServerHostName);
		} else {
			authServer = loadAuthServer("127.0.0.1");
		}
	}

	private void run() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		try {
			RepartiteurInterface stub = (RepartiteurInterface) UnicastRemoteObject
					.exportObject(this, 0);
			Registry registry = LocateRegistry.getRegistry();

			registry.rebind("repartiteur", stub);
			System.out.println("Repartiteur ready.");
		} catch (ConnectException e) {
			System.err
					.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}

		checkExistingRepartiteur();
		if (account.userName == null || account.password == null) {
			System.err.println("Le fichier d'informations du répartiteur n'a pas le format attendu.");
			return;
		}
	}

	/**
	 * Lis le fichier d'informations de compte (s'il existe) et s'assure que le
	 * compte a un format valide
	 */
	private void checkExistingRepartiteur() {
		try {
			BufferedReader fileReader = new BufferedReader(new FileReader(CREDENTIALS_FILENAME));
			try {
				String login = fileReader.readLine();
				String passwordword = fileReader.readLine();
				account = new Account(login, passwordword);
			} catch (IOException e) {
				System.err.println(e.getMessage());
			} finally {
				try {
					if (fileReader != null)
						fileReader.close();
				} catch (IOException e) {
					System.err.println("Un problème inconnu est survenu : " + e.getMessage());
				}
			}
		} catch (FileNotFoundException e) {
			System.out.println("Aucun fichier d'information de compte détecté, génération automatique d'un compte");
			createRepartiteur();
		}
	}

	private void createRepartiteur(){
		Scanner reader = new Scanner(System.in);
		try {
			// Demande au serveur d'authentification de créer le compte
			Account tempAccount = new Account(userName, password);
			boolean validAccount = authServer.newRepartiteur(tempAccount);
			if (validAccount) {
				account = tempAccount;
				try (PrintStream ps = new PrintStream(CREDENTIALS_FILENAME)) {
					ps.println(userName);
					ps.println(password);
					System.out.println("Création du répartiteur réussie!");
				} catch (FileNotFoundException e) {
					System.err.println("Problème lors de la création du fichier d'informations de compte.");
					return;
				}
			} else {
				System.out.println("Ce nom d'utilisateur n'est pas disponible, veuillez recommencer.");
			}
		} catch (RemoteException err) {
			System.err.println(
					"Erreur liée à la connexion au serveur. Abandon de la tentative de création de compte répartiteur.");
			reader.close();
			return;
		}
	
		reader.close();
	}
	
	// Récupère le stub du serveur d'authentification
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

	/*
	 * Méthodes accessibles par RMI. 
	 */

	@Override
	public int handleOperations(List<String> operations) throws RemoteException{
		//TODO
		return 0;
	}	
}
