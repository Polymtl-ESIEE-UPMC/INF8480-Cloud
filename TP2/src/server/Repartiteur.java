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
import java.util.Comparator;
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

	private class CalculationServerComparator implements Comparator<CalculationServerInterface> {
		@Override
		public int compare(CalculationServerInterface o1, CalculationServerInterface o2) {
			return Integer.compare(getCapacity(o1), getCapacity(o2));
		}
	}

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
	private Integer result = 0;
	private int defaultTotalCapacity = 0;
	private int totalCapacity = 0;
	private Map<CalculationServerInterface, Integer> cacheCapacity = null;
	
	private TreeMap<Integer, Integer> numberOfServerWithGivenCapacity = null;
	private Map<Integer, Integer> overheads;
	private Map<Integer, Integer> dangerousOverheads;
	private ExecutorService executorService = null;
	private List<Future<Integer>> resultList = null;
	private List<Future<Integer>> checkResultList = null;

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
				System.out.println(sd);
				CalculationServerInterface cs = InterfaceLoader.loadCalculationServer(sd.ip, sd.port);
				calculationServers.add(cs);

				cacheCapacity.put(cs, sd.capacity);
				int lastCScapacity = sd.capacity;
				defaultTotalCapacity += lastCScapacity;
				Integer currentValue = numberOfServerWithGivenCapacity.get(lastCScapacity);
				if (currentValue == null) {
					numberOfServerWithGivenCapacity.put(lastCScapacity, 1);
				} else {
					numberOfServerWithGivenCapacity.put(lastCScapacity, currentValue + 1);
				}
				overheads.put(lastCScapacity, 0);
				dangerousOverheads.put(lastCScapacity, 0);
			}
		} catch (RemoteException e) {
			System.err.println("Erreur lors de la récupération des serveurs de calcul :\n" + e.getMessage());
		}

		// creer ThreadPool size = nombre de CalculationServer
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
				}
			} else {
				System.out.println("Ce nom d'utilisateur n'est pas disponible, veuillez recommencer.");
			}
		} catch (RemoteException err) {
			System.err.println(
					"Erreur liée à la connexion au serveur. Abandon de la tentative de création de compte répartiteur.");
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
			List<OperationTodo> task = new ArrayList<OperationTodo>(list.subList(i, i + 1));
			result = (result + calculationServers.get(i % 2).calculateOperations(task)) % 4000;
		}
		return result;
	}

	// choisir mode securise || non-securise
	@Override
	public Integer handleOperations(List<String> operations, String mode) throws RemoteException {
		totalCapacity = defaultTotalCapacity;
		result = 0;

		List<OperationTodo> list = new ArrayList<>();
		for (String op : operations) {
			String[] algo = op.split(" ");
			list.add(new OperationTodo(algo[0], Integer.parseInt(algo[1])));
		}

		switch (mode) {
		case "non-securise":
		delegateHandleOperationNonSecurise(list);
		break;
	
		case "securise":
		delegateHandleOperationSecurise(list);
		break;

		default:
			throw new RemoteException("Erreur: mode non reconnu");
		}

		return result;
	}

	private void delegateHandleOperationNonSecurise(List<OperationTodo> list){


		List<OperationTodo> remainingList = new ArrayList<>();

		if (list.size() > totalCapacity) {
			double averageRefusePercent = (double) (list.size() - totalCapacity) / (4 * totalCapacity);
			while(averageRefusePercent > 0.5){
				System.out.println("refuse rate "+averageRefusePercent);
				System.out.println("Too much, reduce list size to "+(list.size()-1));
				OperationTodo remain = list.get(list.size()-1);
				remainingList.add(remain);
				list.remove(remain);
				averageRefusePercent = (double) (list.size() - totalCapacity) / (4 * totalCapacity);
			}
			System.out.println("refuse rate "+averageRefusePercent);	
			calculateOverheadForEach(list.size());
		}else{
			overheads.clear();
			dangerousOverheads.clear();
		}
		
		assignTasks(list);

		boolean CHECK_WITH_SECOND_SERVER = false;
		Integer temp = getResult(CHECK_WITH_SECOND_SERVER);
		if(temp != null){
			result += temp;
			System.out.println("current result "+result);
			if(remainingList.size() > 0){
				System.out.println("remaining size "+(remainingList.size()));
				delegateHandleOperationNonSecurise(remainingList);
			}
		}else{
			result = temp;
		}
	}

	private void delegateHandleOperationSecurise(List<OperationTodo> list){
		int REQUIRED_CHECK = 2;

		List<OperationTodo> remainingList = new ArrayList<>();

		//TODO: MAPPING EVEN WITHOUT OVERHEAD
		if (list.size() > totalCapacity/REQUIRED_CHECK) {
			double averageRefusePercent = (double) (list.size() - totalCapacity/REQUIRED_CHECK) / 
															(4 * totalCapacity/REQUIRED_CHECK);
			while(averageRefusePercent > 0.4){
				OperationTodo remain = list.get(list.size()-1);
				remainingList.add(remain);
				list.remove(remain);
				averageRefusePercent = (double) (list.size() - totalCapacity/REQUIRED_CHECK) / 
															(4 * totalCapacity/REQUIRED_CHECK);
			}
		}

		// servers sans partenaire avec la meme capacite
		List<CalculationServerInterface> lonelyServers = detectLonelyServers();

		CalculationServerInterface idle = isolateIdleServerIfThereIs(lonelyServers);
		
		if (list.size() > totalCapacity/REQUIRED_CHECK) {
			if (lonelyServers.size() == 0) {
				calculateOverheadForEach(list.size(), REQUIRED_CHECK);
			} else {
				try {
					calculateOverheadForEach(list.size(), REQUIRED_CHECK, lonelyServers);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}else{
			overheads.clear();
			dangerousOverheads.clear();
		}

		List<Future<Integer>> secondResultList = new ArrayList<>();
		int last_operation_index = assignTasks(list, secondResultList);
		boolean CLEAR_RESULT_LIST = true;
		assignTasks(list, secondResultList, lonelyServers, last_operation_index, CLEAR_RESULT_LIST);
		checkResultList = secondResultList;

		boolean CHECK_WITH_SECOND_SERVER = true;
		Integer temp = getResult(CHECK_WITH_SECOND_SERVER);
		if(temp != null){
			result += temp;
			if(remainingList.size() > 0){
				delegateHandleOperationSecurise(remainingList);
			}
		}else{
			result = temp;
		}

		for (CalculationServerInterface ls : lonelyServers) {
			undoModification(ls);
		}
		if (idle != null) {
			undoModification(idle);
		}
	}

	private Integer getResult(boolean check){
		int result = 0;
		for (int i = 0; i < resultList.size(); i++) {
			try {
				if (!check) {
					result = (result + resultList.get(i).get()) % 4000;
				} else {
					int temp = resultList.get(i).get();
					if (temp == checkResultList.get(i).get()) {
						result = (result + temp) % 4000;
					} else {
						return null;
					}
				}
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	private int assignTasks(List<OperationTodo> list) {
		resultList.clear();

		int from = 0;

		for (CalculationServerInterface cs : calculationServers) {
			int csCapacity = getCapacity(cs);
			int dangerousOverheadTaken = 0;
			
			if (dangerousOverheads.get(csCapacity) != null && dangerousOverheads.get(csCapacity) > 0) {
				dangerousOverheadTaken = 1;
				dangerousOverheads.put(csCapacity, dangerousOverheads.get(csCapacity) - dangerousOverheadTaken);
			}
			int overhead = 0;
			if (overheads.get(csCapacity) != null) {
				overhead = overheads.get(csCapacity);
			}
			int to = from + csCapacity + overhead + dangerousOverheadTaken;
			int final_from = from;

			sendTask(list, from, to, cs);
			from = to;
		}
		return from;
	}

	private int assignTasks(List<OperationTodo> list, 
							List<Future<Integer>> secondResultList) {
		resultList.clear();
		secondResultList.clear();

		int from = 0;

		Collections.sort(calculationServers, new CalculationServerComparator());

		for (int i = 0; i < calculationServers.size(); i = i + 2) {
			CalculationServerInterface cs = calculationServers.get(i);
			int csCapacity = getCapacity(cs);
			int dangerousOverheadTaken = 0;
			if (dangerousOverheads.get(csCapacity) > 0) {
				dangerousOverheadTaken = 1;
				dangerousOverheads.put(csCapacity, dangerousOverheads.get(csCapacity) - dangerousOverheadTaken);
			}

			int to = from + csCapacity + overheads.get(csCapacity) + dangerousOverheadTaken;
			int final_from = from;

			sendTask(list, from, to, cs);

			cs = calculationServers.get(i + 1);
			sendTask(list, from, to, cs, secondResultList);

			from = to;
		}
		return from;
	}

	private int assignTasks(List<OperationTodo> list, 
				List<Future<Integer>> secondResultList,
				List<CalculationServerInterface> customServers, 
				int from, boolean clear){
		if(clear){
			resultList.clear();
			secondResultList.clear();
		}

		Collections.sort(customServers, new CalculationServerComparator());

		for (int i = 0; i < customServers.size(); i = i + 2) {
			CalculationServerInterface cs = customServers.get(i);
			int csCapacity = getCapacity(cs);
			int dangerousOverheadTaken = 0;
			if (dangerousOverheads.get(csCapacity) > 0) {
				dangerousOverheadTaken = 1;
				dangerousOverheads.put(csCapacity, dangerousOverheads.get(csCapacity) - dangerousOverheadTaken);
			}

			int to = from + csCapacity + overheads.get(csCapacity) + dangerousOverheadTaken;
			int final_from = from;

			sendTask(list, from, to, cs);

			cs = customServers.get(i + 1);
			sendTask(list, from, to, cs, secondResultList);
			from = to;
		}
		return from;
	}

	private void sendTask(List<OperationTodo> list, int from, int to, CalculationServerInterface cs) {
		resultList.add(executorService.submit(() -> { // lambda Java 8 feature
			int result = 0;

			do {
				try {
					List<OperationTodo> sublist = new ArrayList<OperationTodo>(list.subList(from, to));
					result = cs.calculateOperations(sublist);
				} catch (RemoteException e) {

					e.printStackTrace();
				}
			} while (result == -1);

			return result;
		}));
	}

	private void sendTask(List<OperationTodo> list, int from, int to,
							CalculationServerInterface cs, List<Future<Integer>> customResultList){
		customResultList.add(executorService.submit(() -> { // lambda Java 8 feature
			int result = 0;

			do {
				try {
					List<OperationTodo> sublist = new ArrayList<OperationTodo>(list.subList(from, to));
					result = cs.calculateOperations(sublist);
				} catch (RemoteException e) {

					e.printStackTrace();
				}
			} while (result == -1);

			return result;
		}));
	}

	private List<CalculationServerInterface> detectLonelyServers() {
		List<CalculationServerInterface> lonelyServers = new ArrayList<>();

		for (Map.Entry<Integer, Integer> entry : numberOfServerWithGivenCapacity.entrySet()) {
			if (entry.getValue() % 2 != 0) {
				int capacityToRemove = entry.getKey();
				totalCapacity -= capacityToRemove;

				for (CalculationServerInterface cs : calculationServers) {
					if (getCapacity(cs) == capacityToRemove) {
						lonelyServers.add(cs);
						calculationServers.remove(cs);
						break;
					}
				}

				numberOfServerWithGivenCapacity.put(capacityToRemove, entry.getValue() - 1);
			}
		}

		return lonelyServers;
	}

	private CalculationServerInterface isolateIdleServerIfThereIs(List<CalculationServerInterface> lonelyServers){
		if (calculationServers.size() % 2 != 0) {
			CalculationServerInterface idle = lonelyServers.get(0);
			lonelyServers.remove(0);
			return idle;
		}
		return null;
	}

	private int getCapacity(CalculationServerInterface cs) {
		Integer capacity = cacheCapacity.get(cs);
		if (capacity != null) {
			return capacity;
		} else {
			int temp = 0;
			try {
				temp = cs.getCapacity();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			cacheCapacity.put(cs, temp);
			return temp;
		}
	}

	private void undoModification(CalculationServerInterface cs){
		int capacityToUndo = getCapacity(cs);
		numberOfServerWithGivenCapacity.put(capacityToUndo, numberOfServerWithGivenCapacity.get(capacityToUndo) + 1);
		calculationServers.add(cs);
	}

	// NON-SECURISE MODE
	private void calculateOverheadForEach(int operationsSize) {
		double averageRefusePercent = (double) (operationsSize - totalCapacity) / (4 * totalCapacity);
		double dangerousOverhead = 0.0;
		double overhead = 0.0;

		for (Map.Entry<Integer, Integer> entry : numberOfServerWithGivenCapacity.entrySet()) {
			overhead = (double) averageRefusePercent * 4 * entry.getKey();
			double currentDangerousOverhead = (overhead - Math.floor(overhead)) * entry.getValue();
			dangerousOverhead += currentDangerousOverhead;

			overheads.put(entry.getKey(), (int) Math.floor(overhead));
		}

		calculateDangerousOverheadForEarch((int) Math.ceil(dangerousOverhead));
	}

	// SECURISE MODE SANS LONELY SERVERS
	private void calculateOverheadForEach(int operationsSize, int multipleCheckFactor) {
		totalCapacity = (int) Math.floor(totalCapacity / multipleCheckFactor);

		double averageRefusePercent = (double) (operationsSize - totalCapacity) / (4 * totalCapacity);
		double dangerousOverhead = 0.0;
		double overhead = 0.0;

		for (Map.Entry<Integer, Integer> entry : numberOfServerWithGivenCapacity.entrySet()) {
			overhead = (double) averageRefusePercent * 4 * entry.getKey();
			double currentDangerousOverhead = (overhead - Math.floor(overhead)) * entry.getValue()
					/ multipleCheckFactor;
			dangerousOverhead += currentDangerousOverhead;

			overheads.put(entry.getKey(), (int) Math.floor(overhead));
		}
		try {
			calculateDangerousOverheadForEarch((int) Math.ceil(dangerousOverhead), multipleCheckFactor);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// SECURISE MODE AVEC LONELY SERVERS
	private void calculateOverheadForEach(int operationsSize, int multipleCheckFactor,
										List<CalculationServerInterface> lonelyServers) throws Exception {

		if (lonelyServers.size() % multipleCheckFactor != 0) {
			throw new Exception("FATAL: le nombre de lonelyServer est incoherent");
		}

		Collections.sort(lonelyServers, new CalculationServerComparator());
		TreeMap<Integer, Integer> lonelyServersWithGivenCapacity = new TreeMap<>();
		Map<Integer, Integer> mapping = new HashMap<>();
		mapLonelyServerToLowestPartner(lonelyServers, lonelyServersWithGivenCapacity, mapping, multipleCheckFactor);

		totalCapacity = (int) Math.floor(totalCapacity / multipleCheckFactor);

		double averageRefusePercent = (double) (operationsSize - totalCapacity) / (4 * totalCapacity);
		double dangerousOverhead = 0.0;
		double overhead = 0.0;

		for (Map.Entry<Integer, Integer> entry : numberOfServerWithGivenCapacity.entrySet()) {
			overhead = (double) averageRefusePercent * 4 * entry.getKey();
			double currentDangerousOverhead = (overhead - Math.floor(overhead)) * entry.getValue() / multipleCheckFactor;
			dangerousOverhead += currentDangerousOverhead;

			overheads.put(entry.getKey(), (int) Math.floor(overhead));
		}

		for (Map.Entry<Integer, Integer> entry : lonelyServersWithGivenCapacity.entrySet()) {

			Integer mappedCapacity = mapping.get(entry.getKey());
			if (mappedCapacity == null) {
				mappedCapacity = entry.getKey();
			}

			overhead = (double) averageRefusePercent * 4 * mappedCapacity;
			double currentDangerousOverhead = (overhead - Math.floor(overhead)) * entry.getValue()/ multipleCheckFactor;
			dangerousOverhead += currentDangerousOverhead;

			Integer currentValue = overheads.get(mappedCapacity);
			if (currentValue == null) {
				overheads.put(mappedCapacity, (int) Math.floor(overhead));
			}
		}

		try {
			calculateDangerousOverheadForEarch((int) Math.ceil(dangerousOverhead), 
									multipleCheckFactor, lonelyServersWithGivenCapacity);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void calculateDangerousOverheadForEarch(int dangerousOverhead, int multipleCheckFactor,
			TreeMap<Integer, Integer> lonelyServersWithGivenCapacity) throws Exception {

		TreeMap<Integer, Integer> concatenateMap = new TreeMap<>();

		for (Map.Entry<Integer, Integer> entry : lonelyServersWithGivenCapacity.entrySet()) {
			Integer currentValue = numberOfServerWithGivenCapacity.get(entry.getKey());
			if (currentValue == null) {
				concatenateMap.put(entry.getKey(), entry.getValue());
			} else {
				concatenateMap.put(entry.getKey(), currentValue + entry.getValue());
			}
		}

		NavigableMap reverse = concatenateMap.descendingMap();
		Iterator<Map.Entry<Integer, Integer>> it = reverse.entrySet().iterator();

		while (it.hasNext() && dangerousOverhead > 0) {
			Map.Entry<Integer, Integer> entry = it.next();

			if (entry.getValue() % multipleCheckFactor != 0) {
				throw new Exception("FATAL: le nombre de CalculationServer est incoherent");
			}

			int numberOfServerWithCurrentCapacity = entry.getValue() / multipleCheckFactor;
			if (dangerousOverhead > numberOfServerWithCurrentCapacity) {
				dangerousOverheads.put(entry.getKey(), numberOfServerWithCurrentCapacity);
				dangerousOverhead -= numberOfServerWithCurrentCapacity;
			} else {
				dangerousOverheads.put(entry.getKey(), dangerousOverhead);
			}
		}
	}

	private void calculateDangerousOverheadForEarch(int dangerousOverhead, int multipleCheckFactor) throws Exception {
		NavigableMap reverse = numberOfServerWithGivenCapacity.descendingMap();
		Iterator<Map.Entry<Integer, Integer>> it = reverse.entrySet().iterator();

		while (it.hasNext() && dangerousOverhead > 0) {
			Map.Entry<Integer, Integer> entry = it.next();

			if (entry.getValue() % multipleCheckFactor != 0) {
				throw new Exception("FATAL: le nombre de CalculationServer est incoherent");
			}

			int numberOfServerWithCurrentCapacity = entry.getValue() / multipleCheckFactor;
			if (dangerousOverhead > numberOfServerWithCurrentCapacity) {
				dangerousOverheads.put(entry.getKey(), numberOfServerWithCurrentCapacity);
				dangerousOverhead -= numberOfServerWithCurrentCapacity;
			} else {
				dangerousOverheads.put(entry.getKey(), dangerousOverhead);
			}
		}
	}

	private void calculateDangerousOverheadForEarch(int dangerousOverhead) {
		NavigableMap reverse = numberOfServerWithGivenCapacity.descendingMap();
		Iterator<Map.Entry<Integer, Integer>> it = reverse.entrySet().iterator();

		while (it.hasNext() && dangerousOverhead > 0) {
			Map.Entry<Integer, Integer> entry = it.next();
			int numberOfServerWithCurrentCapacity = entry.getValue();
			if (dangerousOverhead > numberOfServerWithCurrentCapacity) {
				dangerousOverheads.put(entry.getKey(), numberOfServerWithCurrentCapacity);
				dangerousOverhead -= numberOfServerWithCurrentCapacity;
			} else {
				dangerousOverheads.put(entry.getKey(), dangerousOverhead);
			}
		}
	}

	private void mapLonelyServerToLowestPartner(List<CalculationServerInterface> lonelyServers,
												TreeMap<Integer, Integer> lonelyServersWithGivenCapacity,
												Map<Integer, Integer> mapping, int multipleCheckFactor){
		/**
		 * TRACKING COUNT OF LONELY SERVERS CAPACITY
		 */
		for (int i = lonelyServers.size()-1; i > -1; i -= multipleCheckFactor) {
			int lowestCapacity = getCapacity(lonelyServers.get(i - (multipleCheckFactor-1)));
			totalCapacity += lowestCapacity * multipleCheckFactor;

			lonelyServersWithGivenCapacity.put(lowestCapacity, multipleCheckFactor);
			mapping.put(getCapacity(lonelyServers.get(i)), lowestCapacity);
		}
	}
}
