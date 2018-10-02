package client;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

import shared.Account;
import shared.AuthServerInterface;
import shared.FileServerInterface;

public class Client {

	public static void main(String[] args) {
		String distantHostname = null;

		if (args.length < 1) {
			printHelp();
			return;
		}

		Command command = null;
		String fileName = "";
		
		//analyse les arguments envoyés au programme. Instancie la commande demandée
		for (int i = 0; i < args.length && command == null; i++) {
			try {
				switch (args[i]) {
				case "-i":
					distantHostname = args[++i];
					break;
				case "list":
					command = new ListCommand();
					break;
				case "create":
					command = new CreateCommand();
					fileName = args[++i];
					break;
				case "get":
					command = new GetCommand();
					fileName = args[++i];
					break;
				case "push":
					command = new PushCommand();
					fileName = args[++i];
					break;
				case "lock":
					command = new LockCommand();
					fileName = args[++i];
					break;
				case "syncLocalDirectory":
					command = new SyncCommand();
					break;
				default:
					System.err.println("Mauvaise commande : " + args[i]);
					printHelp();
					return;
				}
			} catch (IndexOutOfBoundsException e) {
				System.err.println("Veuillez entrer un nom de fichier");
				return;
			}
		}

		Client client = new Client(distantHostname);
		if(command != null){
			//exécute la commande demandée
			client.run(command, fileName);
		} else {
			System.err.println("Veuillez entrer une commande!");
			printHelp();
		}
	}

	//Affiche l'aide sur les commandes
	public static void printHelp() {
		System.out.println("Liste des commandes :\n" + "-i ip_adress\n" + "list\n" + "create nomDeFichier\n" + "get nomDeFichier\n"
				+ "push nomDeFichier\n" + "lock nomDeFichier\n" + "syncLocalDirectory\n");
	}

	private AuthServerInterface authServer = null;
	private FileServerInterface fileServer = null;
	private Account account;

	public Client(String distantServerHostname) {
		super();
		
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		//Récupère le stub selon l'adresse passée en paramètre (localhost par défaut)
		if(distantServerHostname != null){
			authServer = loadAuthServer(distantServerHostname);
			fileServer = loadFileServer(distantServerHostname);
		}else{
			authServer = loadAuthServer("127.0.0.1");
			fileServer = loadFileServer("127.0.0.1");
		}
	}

	//Récupère le stub du serveur d'authentification
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

	//Récupère le stub du serveur de fichiers
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

	private void run(Command command, String fileName) {
		if (authServer != null) {
			try {
				checkExistingAccount();
				if (account.userName == null || account.password == null) {
					System.err.println("Votre fichier d'informations de compte n'a pas le format attendu.");
					return;
				}
				//Exécute la commande par polymorphisme
				command.run(account, fileServer, fileName);
			} catch (RemoteException e) {
				System.err.println("Erreur: " + e.getMessage());
			}
		}
	}

	/**
	 * Lis le fichier d'informations de compte (s'il existe) et s'assure que le compte 
	 * a un format valide
	 */
	private void checkExistingAccount() {
		try {
			BufferedReader fileReader = new BufferedReader(new FileReader("credentials"));
			try {
				String login = fileReader.readLine();
				String password = fileReader.readLine();
				account = new Account(login, password);
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
			System.out.println("Vous n'avez pas de compte, entrer les identifiants désirés :");
			createAccount();
		}
	}

	/**
	 * Crée un compte utilisateur en demandant un nom d'utilisateur et un mot de passe
	 */
	private void createAccount() {
		Scanner reader = new Scanner(System.in);
		boolean validAccount = false;
		while (!validAccount) {
			String userName = "", pass = "";
			System.out.println("Nom d'utilisateur : ");
			boolean nameValid = false;
			while (!nameValid) {
				userName = reader.nextLine();
				if (userName.length() <= 1) {
					System.out.println("Entrez un nom plus long.");
				} else {
					nameValid = true;
				}
			}
			System.out.println("Mot de passe : ");
			boolean passValid = false;
			while (!passValid) {
				pass = reader.nextLine();
				if (pass.length() <= 2) {
					System.out.println("Entrez un mot de passe plus long.");
				} else {
					passValid = true;
				}
			}

			try {
				//Demande au serveur d'authentification de créer le compte
				validAccount = authServer.newAccount(userName, pass);
				if (validAccount) {
					account = new Account(userName, pass);
					try (PrintStream ps = new PrintStream("credentials")) {
						ps.println(userName);
						ps.println(pass);
						System.out.println("Création du compte réussie!");
					} catch (FileNotFoundException e) {
						System.err.println("Problème lors de la création du fichier d'informations de compte.");
						return;
					}
				} else {
					System.out.println("Ce nom d'utilisateur n'est pas disponible, veuillez recommencer.");
				}
			} catch (RemoteException err) {
				System.err.println(
						"Erreur liée à la connexion au serveur. Abandon de la tentative de création de compte.");
				reader.close();
				return;
			}
		}

		reader.close();
	}
}