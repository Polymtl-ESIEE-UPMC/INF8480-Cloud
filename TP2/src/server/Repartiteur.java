package server;

import java.rmi.ConnectException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import shared.RepartiteurInterface;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

public class Repartiteur implements RepartiteurInterface {
	final private String OPERATIONS_DIR = "";

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

	public void assignTasks(String fileName){
		List<String> operations = readAllText(OPERATIONS_DIR+fileName);
	}

	//Fonction qui retourne tout le texte d'un fichier passé en paramètre sous forme de ArrayList
	private static List<String> readAllText(String filePath) {
		List<String> text = new ArrayList<String>();

		try {
			BufferedReader fileReader = new BufferedReader(new FileReader(filePath));
			try {
				String line;
				while ((line = fileReader.readLine()) != null) {
					text.add(line);
				}
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
			System.err.println(e.getMessage());
		}

		return text;
	}
	
}
