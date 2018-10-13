package shared;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class InterfaceLoader {

	// Récupère le stub du serveur d'authentification
	public static AuthServerInterface loadAuthServer(String hostname) {
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

	// Récupère le stub du serveur d'authentification
	public static RepartiteurInterface loadRepartiteur(String hostname) {
		RepartiteurInterface stub = null;

		try {
			Registry registry = LocateRegistry.getRegistry(hostname);
			stub = (RepartiteurInterface) registry.lookup("repartiteur");
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage() + "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}

		return stub;
	}

	// Récupère le stub du serveur de calcul
	public static CalculationServerInterface loadCalculationServer(String hostname, int port) {
		CalculationServerInterface stub = null;

		try {
			if (port != 0) {
				Registry registry = LocateRegistry.getRegistry(hostname, port);
				stub = (CalculationServerInterface) registry.lookup("calculationServer");
			} else {
				Registry registry = LocateRegistry.getRegistry(hostname);
				stub = (CalculationServerInterface) registry.lookup("calculationServer");
			}
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage() + "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}

		return stub;
	}
}