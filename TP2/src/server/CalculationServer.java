package server;

import java.rmi.ConnectException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import shared.CalculationServerInterface;

public class CalculationServer implements CalculationServerInterface {

	public static void main(String[] args) {
		CalculationServer server = new CalculationServer();
		server.run();
	}

	public CalculationServer() {
		super();
	}

	//lance le serveur
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
}
