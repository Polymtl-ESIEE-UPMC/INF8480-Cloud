package server;

import java.rmi.AccessException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.LinkedBlockingQueue;

import shared.AuthServerInterface;
import shared.CalculationServerInterface;
import shared.OperationTodo;

public class CalculationServer implements CalculationServerInterface {
	static final private int DEFAULT_CAPACITY = 10;

	private AuthServerInterface authServer;
	private LinkedBlockingQueue<OperationTodo> tasks; //list of operations todo

	public static void main(String[] args) {
		String distantHostname = null;
		int capacity = DEFAULT_CAPACITY;
		if (args.length > 0) {
			// analyse les arguments envoyés au programme
			for (int i = 0; i < args.length; i++) {
				try {
					switch (args[i]) {
					case "-i":
						distantHostname = args[++i];
						break;
					case "-c":
						capacity = Integer.parseInt(args[++i]);
						break;
					default:
						System.err.println("Mauvaise commande : " + args[i]);
						return;	
					}
				} catch (IndexOutOfBoundsException e) {
					e.printStackTrace();
					return;
				}
			}
		}else{
			System.err.println("Capacite default : " + DEFAULT_CAPACITY);
		}
		CalculationServer server = new CalculationServer(distantHostname, capacity);
		server.run();
	}

	public CalculationServer(String distantHostname, int capacity) {
		super();

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		tasks = new LinkedBlockingQueue<OperationTodo>(capacity);
		
		// Récupère le stub selon l'adresse passée en paramètre (localhost par défaut)
		if (distantHostname != null) {
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

	/*
	 * Méthodes accessibles par RMI. 
	 */
	public int remainingCapacity(){
		return tasks.remainingCapacity();
	}
	//ask task to queue
	public boolean queueTask(OperationTodo operation){
		return tasks.add(operation);
	}

	public int calculate(){
		while(!tasks.isEmpty()){
			OperationTodo todo = tasks.poll();
		}
		return 0;
	}
}
