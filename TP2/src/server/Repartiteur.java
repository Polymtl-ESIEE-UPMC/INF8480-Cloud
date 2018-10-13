package server;

import java.rmi.AccessException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;

import shared.AuthServerInterface;
import shared.CalculationServerInfo;
import shared.CalculationServerInterface;
import shared.InterfaceLoader;
import shared.OperationTodo;
import shared.RepartiteurInterface;
import shared.Account;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Repartiteur implements RepartiteurInterface {

	private static final String CREDENTIALS_FILENAME = "credentialsRepartiteur";

	public static void main(String[] args) {
		String authServerHostName = null;
		if (args.length > 0) {
			authServerHostName = args[0];
		}
		Repartiteur server = new Repartiteur(authServerHostName);
		server.run();
	}

	private AuthServerInterface authServer = null;
	private List<CalculationServerInterface> calculationServers = null;
	private final String userName = "tempName";
	private final String password = "temppassword";
	private Account account = null;

	private Map<CalculationServerInterface, Integer> cacheCapacity = null;
	private TreeMap<Integer, Integer> numberOfServerWithGivenCapacity = null;
	private int totalCapacity = 0;
	private Map<Integer, Integer> overheads;
	private Map<Integer, Integer> dangerousOverheads;
	private ExecutorService executorService = null;
	private List<Future<Integer>> resultList = null;

	public Repartiteur(String authServerHostName) {
		super();
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		if (authServerHostName != null) {
			authServer = InterfaceLoader.loadAuthServer(authServerHostName);
		} else {
			authServer = InterfaceLoader.loadAuthServer("127.0.0.1");
		}

		calculationServers = new ArrayList<>();
	}

	private void run() {
		numberOfServerWithGivenCapacity = new TreeMap<>();
		overheads = new HashMap<>();
		dangerousOverheads = new HashMap<>();
		cacheCapacity = new HashMap<>();

		try {
			RepartiteurInterface stub = (RepartiteurInterface) UnicastRemoteObject.exportObject(this, 0);
			Registry registry = LocateRegistry.getRegistry();

			registry.rebind("repartiteur", stub);
			System.out.println("Repartiteur ready.");
		} catch (ConnectException e) {
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
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

		try {
			boolean success = authServer.loginRepartiteur(account);
			if (!success) {
				System.err.println("Erreur lors du login du répartiteur :");
				return;
			}
		} catch (RemoteException e) {
			System.err.println("Erreur lors de la récupération des serveurs de calcul :\n" + e.getMessage());
		}

		try {
			for (CalculationServerInfo sd : authServer.getCalculationServers()) {
				System.out.println(sd.ip);
				CalculationServerInterface cs = InterfaceLoader.loadCalculationServer(sd.ip);
				calculationServers.add(cs);
				
				cacheCapacity.put(cs, sd.capacity);
				int lastCScapacity = calculationServers.get(calculationServers.size()-1).getCapacity();
				totalCapacity += lastCScapacity;
				if(numberOfServerWithGivenCapacity.get(lastCScapacity) == null){
					numberOfServerWithGivenCapacity.put(lastCScapacity, 1);
				}else{
					numberOfServerWithGivenCapacity.put(lastCScapacity, numberOfServerWithGivenCapacity.get(lastCScapacity)+1);
				}
				overheads.put(lastCScapacity, 0);
				dangerousOverheads.put(lastCScapacity, 0);
			}
		} catch (RemoteException e) {
			System.err.println("Erreur lors de la récupération des serveurs de calcul :\n" + e.getMessage());
		}
		
		//creer ThreadPool size = nombre de CalculationServer
		executorService = Executors.newFixedThreadPool(calculationServers.size());
		resultList = new ArrayList<>();
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

	private void createRepartiteur() {
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

	/*
	 * Méthodes accessibles par RMI. 
	 */

	@Override
	public int handleOperations(List<String> operations) throws RemoteException {
		List<OperationTodo> list = new ArrayList<>();
		for (String op : operations) {
			String[] algo = op.split(" ");
			list.add(new OperationTodo(algo[0], Integer.parseInt(algo[1])));
		}
		
		// TODO: Faire un bon algorithme
		int result = 0;
		for (int i = 0; i < list.size(); i++) {
			List<OperationTodo> task = new ArrayList<OperationTodo>(list.subList(i, i+1));
			result = (result + calculationServers.get(0).calculateOperations(task)) % 4000;
		}
		return result;
	}

	//choisir mode securise || non-securise
	@Override
	public int handleOperations(List<String> operations, String mode) throws RemoteException {
		List<OperationTodo> list = new ArrayList<>();
		for (String op : operations) {
			String[] algo = op.split(" ");
			list.add(new OperationTodo(algo[0], Integer.parseInt(algo[1])));
		}

		switch(mode){
			case "securise":
				List<CalculationServerInterface> lonelyServers = detectLonelyServers(); //servers sans partenaire avec la meme capacite
				CalculationServerInterface idle = null;
				if(calculationServers.size() %2 !=0){
					idle = lonelyServers.get(0);
					lonelyServers.remove(0);
				}
				
				Map<Integer, Integer> mapping = new HashMap<>();
				if(lonelyServers.size() == 0){
					calculateOverheadForEach(list.size(), 2);
				}else{
					calculateOverheadForEach(list.size(), 2, lonelyServers, mapping);
				}

				List<Future<Integer>> secondResultList = new ArrayList<>();
				
				//undo modification
				if(idle != null){
					lonelyServers.add(idle);
				}
				while(lonelyServers.size() > 0){
					int capacityToUndo = lonelyServers.get(0).getCapacity();
					numberOfServerWithGivenCapacity.put(capacityToUndo, numberOfServerWithGivenCapacity.get(capacityToUndo)+1);
					calculationServers.add(lonelyServers.get(0));
					lonelyServers.remove(0);
					totalCapacity += capacityToUndo;
				}
				break;
			
			case "non-securise":
				if(list.size() > totalCapacity){
					calculateOverheadForEach(list.size());
				}

				int from = 0;
				
				for(CalculationServerInterface cs:calculationServers){
					int csCapacity = cs.getCapacity();
					int dangerousOverheadTaken = 0;
					if(dangerousOverheads.get(csCapacity) > 0){
						dangerousOverheadTaken = 1;
						dangerousOverheads.put(csCapacity, dangerousOverheads.get(csCapacity) - dangerousOverheadTaken);
					}
					int to = from + csCapacity + overheads.get(csCapacity) + dangerousOverheadTaken;
					int final_from = from;
					
					resultList.add(executorService.submit(()->{ //lambda Java 8 feature
						int result = 0;
						
						do{
							try{
								result = cs.calculateOperations(list.subList(final_from, to));
							}catch(RemoteException e){
								e.printStackTrace();
							}
						}while(result == -1);
						
						return result;
					}));
					from = to;
				}
				break;
				
			default:
				throw new RemoteException("Erreur: mode non reconnu");
		}
		
		int result = 0;
		for (int i = 0; i < resultList.size(); i++) {
			try{
				result = (result + resultList.get(i).get()) % 4000;
			}catch(ExecutionException e){
				e.printStackTrace();
			}catch(InterruptedException e){
				e.printStackTrace();
			}
		}
		return result;
	}

	// NON-SECURISE MODE
	private void calculateOverheadForEach(int operationsSize){
		double averageRefusePercent = (operationsSize - totalCapacity)/(4*totalCapacity);
		double dangerousOverhead = 0.0;

		for(Map.Entry<Integer, Integer> entry : numberOfServerWithGivenCapacity.entrySet()){
			double overhead = 0.0;
			overhead = averageRefusePercent * 4 * entry.getKey();
			overheads.put(entry.getKey(), (int) Math.floor(overhead));
			double currentDangerousOverhead = (overhead - Math.floor(overhead))*entry.getValue();
			dangerousOverhead += currentDangerousOverhead ;
		}

		dangerousOverhead = Math.ceil(dangerousOverhead);
		NavigableMap reverse = numberOfServerWithGivenCapacity.descendingMap();
		Iterator<Map.Entry<Integer, Integer>> it = reverse.entrySet().iterator();
		
		while (it.hasNext() && dangerousOverhead > 0) {
			Map.Entry<Integer, Integer> entry = it.next();
			int numberOfServerWithCurrentCapacity = entry.getValue();
			if(dangerousOverhead > numberOfServerWithCurrentCapacity){
				dangerousOverheads.put(entry.getKey(), numberOfServerWithCurrentCapacity);
				dangerousOverhead -= numberOfServerWithCurrentCapacity;
			}else{
				dangerousOverheads.put(entry.getKey(), (int) dangerousOverhead);
			}
		}
	}

	// SECURISE MODE SANS LONELY SERVERS
	private void calculateOverheadForEach(int operationsSize, int multipleCheckFactor){
		int halfTotalCapacity = (int) Math.floor(totalCapacity / multipleCheckFactor);
		double averageRefusePercent = (operationsSize - halfTotalCapacity)/(4*halfTotalCapacity);
		double dangerousOverhead = 0.0;

		for(Map.Entry<Integer, Integer> entry : numberOfServerWithGivenCapacity.entrySet()){
			double overhead = 0.0;
			overhead = averageRefusePercent * 4 * entry.getKey();
			overheads.put(entry.getKey(), (int) Math.floor(overhead));
			double currentDangerousOverhead = (overhead - Math.floor(overhead))*entry.getValue() / multipleCheckFactor;
			dangerousOverhead += currentDangerousOverhead ;
		}

		dangerousOverhead = Math.ceil(dangerousOverhead);
		NavigableMap reverse = numberOfServerWithGivenCapacity.descendingMap();
		Iterator<Map.Entry<Integer, Integer>> it = reverse.entrySet().iterator();
		
		while (it.hasNext() && dangerousOverhead > 0) {
			Map.Entry<Integer, Integer> entry = it.next();
			int numberOfServerWithCurrentCapacity = entry.getValue() / multipleCheckFactor;
			if(dangerousOverhead > numberOfServerWithCurrentCapacity){
				dangerousOverheads.put(entry.getKey(), numberOfServerWithCurrentCapacity);
				dangerousOverhead -= numberOfServerWithCurrentCapacity;
			}else{
				dangerousOverheads.put(entry.getKey(), (int) dangerousOverhead);
			}
		}
	}

	// SECURISE MODE AVEC LONELY SERVERS
	private void calculateOverheadForEach(int operationsSize, int multipleCheckFactor, List<CalculationServerInterface> lonelyServers, 
																						Map<Integer, Integer> mapping){

		Collections.sort(lonelyServers);
		int halfTotalCapacity = (int) Math.floor(totalCapacity / multipleCheckFactor);
		double averageRefusePercent = (operationsSize - halfTotalCapacity)/(4*halfTotalCapacity);
		double dangerousOverhead = 0.0;

		for(Map.Entry<Integer, Integer> entry : numberOfServerWithGivenCapacity.entrySet()){
			double overhead = 0.0;
			overhead = averageRefusePercent * 4 * entry.getKey();
			overheads.put(entry.getKey(), (int) Math.floor(overhead));
			double currentDangerousOverhead = (overhead - Math.floor(overhead))*entry.getValue() / multipleCheckFactor;
			dangerousOverhead += currentDangerousOverhead ;
		}

		dangerousOverhead = Math.ceil(dangerousOverhead);
		NavigableMap reverse = numberOfServerWithGivenCapacity.descendingMap();
		Iterator<Map.Entry<Integer, Integer>> it = reverse.entrySet().iterator();
		
		while (it.hasNext() && dangerousOverhead > 0) {
			Map.Entry<Integer, Integer> entry = it.next();
			int numberOfServerWithCurrentCapacity = entry.getValue() / multipleCheckFactor;
			if(dangerousOverhead > numberOfServerWithCurrentCapacity){
				dangerousOverheads.put(entry.getKey(), numberOfServerWithCurrentCapacity);
				dangerousOverhead -= numberOfServerWithCurrentCapacity;
			}else{
				dangerousOverheads.put(entry.getKey(), (int) dangerousOverhead);
			}
		}
	}

	private List<CalculationServerInterface> detectLonelyServers(){
		List<CalculationServerInterface> lonelyServers = new ArrayList<>();
			
		for(Map.Entry<Integer, Integer> entry:numberOfServerWithGivenCapacity.entrySet()){
			if(entry.getValue()%2 != 0){
				int capacityToRemove = entry.getKey();
				totalCapacity -= capacityToRemove;
				
				for(CalculationServerInterface cs:calculationServers){
					if(cs.getCapacity() == capacityToRemove){
						lonelyServers.add(cs);
						calculationServers.remove(cs);
						break;
					}
				}

				numberOfServerWithGivenCapacity.put(capacityToRemove, entry.getValue()-1);
			}
		}

		return lonelyServers;
	}

	private int getCapacity(CalculationServerInterface cs){
		Integer capacity = cacheCapacity.get(cs);
		if(capacity != null){
			return serverInfo.capacity;
		}else{
			int temp = cs.getCapacity();
			cacheCapacity.put(cs, temp);
			return temp;
		}
	}
}
