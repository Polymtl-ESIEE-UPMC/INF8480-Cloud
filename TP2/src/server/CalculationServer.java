package server;

import java.rmi.ConnectException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

import shared.AuthServerInterface;
import shared.CalculationServerInterface;
import shared.OperationTodo;

public class CalculationServer implements CalculationServerInterface {
	private AuthServerInterface authServer;
	private List<OperationTodo> tasks;

	public static void main(String[] args) {
		String distantHostname = null;
		if (args.length > 0) {
			// analyse les arguments envoyés au programme. Instancie la commande demandée
			for (int i = 0; i < args.length && command == null; i++) {
				try {
					switch (args[i]) {
					case "-i":
						distantHostname = args[++i];
						break;
					}
				} catch (IndexOutOfBoundsException e) {
					e.printStackTrace();
					return;
				}
			}
		}
		CalculationServer server = new CalculationServer(distantHostname);
		server.run();
	}

	public CalculationServer(String distantHostname) {
		super();

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		tasks = new ArrayList<OperationTodo>();

		// Récupère le stub selon l'adresse passée en paramètre (localhost par défaut)
		if (distantServerHostname != null) {
			authServer = loadAuthServer(distantHostname);
		} else {
			authServer = loadAuthServer("127.0.0.1");
		}
	}

	// lance le serveur
	private void run() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		try {
			CalculationServerInterface stub = (CalculationServerInterface) UnicastRemoteObject.exportObject(this, 0);

			Registry registry = LocateRegistry.getRegistry();
			registry.rebind("calculationServer", stub);
			System.out.println("Calculation server ready.");
		} catch (ConnectException e) {
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
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
	
	public boolean queueTask(OperationTodo operation){
		return tasks.add(operation);
	}
}
