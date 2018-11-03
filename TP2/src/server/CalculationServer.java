package server;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Random;

import shared.AuthServerInterface;
import shared.CalculationServerInfo;
import shared.CalculationServerInterface;
import shared.InterfaceLoader;
import shared.OperationTodo;

public class CalculationServer implements CalculationServerInterface {
	private static final int DEFAULT_CAPACITY = 4;

	public static void main(String[] args) {
		String distantHostname = null;
		int capacity = DEFAULT_CAPACITY;
		int badPercent = 0;
		int port = 0;
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
					case "-m":
						badPercent = Integer.parseInt(args[++i]);
						break;
					case "-p":
						port = Integer.parseInt(args[++i]);
						break;
					default:
						System.err.println("Mauvaise commande : " + args[i]);
						return;
					}
				} catch (IndexOutOfBoundsException | NumberFormatException e) {
					printHelp();
					return;
				}
			}
		}

		CalculationServer server = new CalculationServer(distantHostname, port, capacity, badPercent);
		server.run();
	}

	// Affiche l'aide sur les commandes
	private static void printHelp() {
		System.out.println("Liste des commandes :\n" + "-i ip_adress\n-m %malicieux\n-c capacité\n-p port");
	}

	private int capacity;
	private int badPercent;
	private int port;
	private AuthServerInterface authServer;
	private Random random;

	public CalculationServer(String distantHostname, int port, int capacity, int badPercent) {
		super();

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		this.port = port;
		this.capacity = capacity;
		this.badPercent = badPercent;
		random = new Random();

		// Récupère le stub selon l'adresse passée en paramètre (localhost par défaut)
		if (distantHostname != null) {
			authServer = InterfaceLoader.loadAuthServer(distantHostname);
		} else {
			authServer = InterfaceLoader.loadAuthServer("127.0.0.1");
		}
	}

	// lance le serveur
	private void run() {
		if (authServer == null) {
			return;
		}

		try {
			CalculationServerInterface stub; 
			Registry registry;
			if(port == 0){
				stub = (CalculationServerInterface) UnicastRemoteObject.exportObject(this, 0);
				registry = LocateRegistry.getRegistry();
			} else{
				stub = (CalculationServerInterface) UnicastRemoteObject.exportObject(this, this.port);
				registry = LocateRegistry.createRegistry(this.port);
			}

			registry.rebind("calculationServer", stub);
			System.out.println("Calculation server ready.");
		
			CalculationServerInfo info = new CalculationServerInfo("", this.port, capacity);
			try {
				boolean success = authServer.registerCalculationServer(info);
				if (!success) {
					System.err.println("Le serveur de calcul n'a pas pu bien s'enregistrer");
				}
			} catch (RemoteException e) {
				System.err.println("Le serveur de calcul n'a pas pu bien s'enregistrer");
				System.err.println("Erreur: " + e.getMessage());
			}
		} catch (ConnectException e) {
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}

	private boolean acceptTask(int opCount) {
		if (opCount < capacity) {
			return true;
		}

		double percent = (opCount - capacity) / (4.0 * capacity);
		float randF = random.nextFloat();
		return percent < randF;
	}

	/*
	 * Méthodes accessibles par RMI. 
	 */
	
	@Override
	public int calculateOperations(List<OperationTodo> operations) throws RemoteException {
		if(!acceptTask(operations.size())){
			//opération refusée
			System.out.println("refuse");
			return -1;
		}

		//TODO : ajouter verifyRepartiteur

		System.out.println("Handling " + operations.size() + " operations.");
		float randF = random.nextFloat();

		if(badPercent > randF*100){
			//retourner une valeur malicieuse
			return random.nextInt(4000);
		}

		int result = 0;
		for (OperationTodo op : operations) {
			result = (result + op.execute()) % 4000;
		}
		System.out.println(result);
		return result;
	}

	@Override
	public int getCapacity() throws RemoteException{
		return this.capacity;
	}
}
