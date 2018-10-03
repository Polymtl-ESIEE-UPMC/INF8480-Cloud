package server;

import java.rmi.ConnectException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import shared.RepartiteurInterface;

public class Repartiteur implements RepartiteurInterface {

	public static void main(String[] args) {
		Repartiteur server = new Repartiteur();
		server.run();
	}

	public Repartiteur() {
		super();
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
	}
	
	/*
	 * Méthodes accessibles par RMI. 
	 */
	 
	
}
