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
	private Map<CalculationServerInterface, Integer> virtualCapacity = null;
	private ExecutorService executorService = null;

	// TRACKING VARIABLES
	private TreeMap<Integer, Integer> cacheCountCapacity = null;
	private TreeMap<Integer, Integer> countCapacity = null;
	private Map<Integer, Integer> overheads;
	private Map<Integer, Integer> dangerousOverheads;
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
		cacheCapacity = new HashMap<>();
		virtualCapacity = new HashMap<>();
		cacheCountCapacity = new TreeMap<>();
		countCapacity = new TreeMap<>();
		overheads = new HashMap<>();
		dangerousOverheads = new HashMap<>();
		resultList = new ArrayList<>();
		checkResultList = new ArrayList<>();
	}

	private void run() {

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
				Integer currentValue = cacheCountCapacity.get(lastCScapacity);
				if (currentValue == null) {
					cacheCountCapacity.put(lastCScapacity, 1);
				} else {
					cacheCountCapacity.put(lastCScapacity, currentValue + 1);
				}
				overheads.put(lastCScapacity, 0);
				dangerousOverheads.put(lastCScapacity, 0);
			}
		} catch (RemoteException e) {
			System.err.println("Erreur lors de la récupération des serveurs de calcul :\n" + e.getMessage());
		}

		// creer ThreadPool size = nombre de CalculationServer
		executorService = Executors.newFixedThreadPool(calculationServers.size());
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
		resetTrackingCapacity();

		List<OperationTodo> list = parseStringToOperations(operations);

		switch (mode) {
		case "non-securise":
			delegateHandleOperationNonSecurise(list);
			break;

		case "securise":
			if (calculationServers.size() > 1) {
				int checkFactor = 2;
				CalculationServerInterface idle = detectLonelyServers(checkFactor);
				updateTrackingCapacity(checkFactor);
				delegateHandleOperationSecurise(list, checkFactor);

				if (idle != null)
					calculationServers.add(idle); // undo modification

			} else {
				System.out.println("Il n'y a pas suffisamment de serveur de calcul");
				System.out.println("Switch au mode non-securise automatiquement");
				delegateHandleOperationNonSecurise(list);
			}
			break;

		default:
			throw new RemoteException("Erreur: mode non reconnu");
		}
		clearOverHeads();
		clearTracking();

		return result % 4000;
	}

	private List<OperationTodo> parseStringToOperations(List<String> operations) {
		List<OperationTodo> list = new ArrayList<>();
		for (String op : operations) {
			String[] algo = op.split(" ");
			list.add(new OperationTodo(algo[0], Integer.parseInt(algo[1])));
		}
		return list;
	}

	private void delegateHandleOperationNonSecurise(List<OperationTodo> list) {
		delegateHandleOperationSecurise(list, 1);
	}

	private void delegateHandleOperationSecurise(List<OperationTodo> list, int checkFactor) {

		clearOverHeads();

		boolean CHECK_WITH_SECOND_SERVER = false;
		if (checkFactor > 1) {
			CHECK_WITH_SECOND_SERVER = true;
		}

		List<OperationTodo> remainingList = new ArrayList<>();

		if (list.size() > totalCapacity) {
			double averageRefusePercent = (double) (list.size() - totalCapacity) / (4 * totalCapacity / checkFactor);
			while (averageRefusePercent > 0.33) {
				OperationTodo remain = list.get(list.size() - 1);
				remainingList.add(remain);
				list.remove(remain);
				averageRefusePercent = (double) (list.size() - totalCapacity) / (4 * totalCapacity);
			}

			calculateOverheadForEach(averageRefusePercent);
		}

		assignTasks(list, checkFactor);

		Integer temp = getResult(CHECK_WITH_SECOND_SERVER);
		if (temp != null) {
			result += temp;
			if (remainingList.size() > 0) {
				delegateHandleOperationSecurise(remainingList, checkFactor);
			}
		} else {
			result = temp;
		}
	}

	private int assignTasks(List<OperationTodo> list, int checkFactor) {
		resultList.clear();

		int from = 0;
		int i = 0;

		while (i < calculationServers.size()) {
			int csCapacity = getCapacity(calculationServers.get(i));

			int overhead = 0;
			if (overheads.get(csCapacity) != null) {
				overhead = overheads.get(csCapacity);
			}

			int dangerousOverheadTaken = 0;
			if (dangerousOverheads.get(csCapacity) != null && dangerousOverheads.get(csCapacity) > 0) {
				dangerousOverheadTaken = 1;
				dangerousOverheads.put(csCapacity, dangerousOverheads.get(csCapacity) - dangerousOverheadTaken);
			}

			int to = from + csCapacity + overhead + dangerousOverheadTaken;
			if (list.size() < to)
				to = list.size();

			List<OperationTodo> todo = new ArrayList<>(list.subList(from, to));

			sendTask(todo, calculationServers.get(i));
			if (i != i + checkFactor - 1) {
				sendTask(todo, calculationServers.get(i + checkFactor - 1), checkResultList);
			}
			i += checkFactor;

			from = to;
		}
		return from;
	}

	private void sendTask(List<OperationTodo> todos, CalculationServerInterface cs) {
		sendTask(todos, cs, resultList);
	}

	private void sendTask(List<OperationTodo> todos, CalculationServerInterface cs,
			List<Future<Integer>> customResultList) {

		customResultList.add(executorService.submit(() -> { // lambda Java 8 feature
			int res = 0;

			do {
				try {
					res = cs.calculateOperations(todos);
				} catch (RemoteException e) {

					e.printStackTrace();
				}
			} while (res == -1);

			return res;
		}));
	}

	private Integer getResult(boolean check) {
		int res = 0;
		for (int i = 0; i < resultList.size(); i++) {
			try {
				if (!check) {
					res = (res + resultList.get(i).get()) % 4000;
				} else {
					int temp = resultList.get(i).get();
					if (temp == checkResultList.get(i).get()) {
						res = (res + temp) % 4000;
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
		return res;
	}

	private void calculateOverheadForEach(double averageRefusePercent) {

		double dangerousOverhead = 0.0;
		double overhead = 0.0;

		for (Map.Entry<Integer, Integer> entry : countCapacity.entrySet()) {
			overhead = (double) averageRefusePercent * 4 * entry.getKey();
			double currentDangerousOverhead = (double) (overhead - Math.floor(overhead)) * entry.getValue();
			dangerousOverhead += currentDangerousOverhead;

			overheads.put(entry.getKey(), (int) Math.floor(overhead));
		}

		calculateDangerousOverheadForEach((int) Math.ceil(dangerousOverhead));
	}

	private void calculateDangerousOverheadForEach(int dangerousOverhead) {
		NavigableMap reverse = countCapacity.descendingMap();
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

	private CalculationServerInterface detectLonelyServers(int checkFactor) {
		List<CalculationServerInterface> lonelyServers = new ArrayList<>();

		for (Map.Entry<Integer, Integer> entry : countCapacity.entrySet()) {
			if (entry.getValue() % checkFactor != 0) {
				int capacityToRemove = entry.getKey();
				totalCapacity -= capacityToRemove;

				for (CalculationServerInterface cs : calculationServers) {
					if (getCapacity(cs) == capacityToRemove) {
						lonelyServers.add(cs);
						break;
					}
				}

				countCapacity.put(capacityToRemove, entry.getValue() - 1);
			}
		}

		CalculationServerInterface idle = isolateIdleServerIfThereIs(lonelyServers, checkFactor);
		mapLonelyServerToLowestPartner(lonelyServers, checkFactor);

		return idle;
	}

	private CalculationServerInterface isolateIdleServerIfThereIs(List<CalculationServerInterface> lonelyServers,
			int checkFactor) {

		CalculationServerInterface idle = null;
		if (calculationServers.size() % checkFactor != 0) {
			idle = lonelyServers.get(0);
			lonelyServers.remove(idle);
		}
		if (idle != null)
			calculationServers.remove(idle);
		return idle;
	}

	private void mapLonelyServerToLowestPartner(List<CalculationServerInterface> lonelyServers, int checkFactor) {
		/**
		 * EXTENDABLE FOR MULTIPLE CHECK
		 */
		Collections.sort(lonelyServers, new CalculationServerComparator());
		for (int i = lonelyServers.size() - 1; i > 1; i -= checkFactor) {
			int lowestCapacity = getCapacity(lonelyServers.get(i - (checkFactor - 1)));
			totalCapacity += lowestCapacity * checkFactor;

			virtualCapacity.put(lonelyServers.get(i), lowestCapacity);
			Integer currentValue = countCapacity.get(lowestCapacity);
			if (currentValue == null)
				currentValue = 0;
			countCapacity.put(lowestCapacity, currentValue + checkFactor);
		}
	}

	private void syncCapacity() {
		for (CalculationServerInterface cs : calculationServers) {
			Integer capacity = cacheCapacity.get(cs);
			if (capacity == null) {
				System.out.println("Object changed");
				int temp = 0;
				try {
					temp = cs.getCapacity();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				cacheCapacity.put(cs, temp);
			}
		}
	}

	private int getCapacity(CalculationServerInterface cs) {
		Integer capacity = virtualCapacity.get(cs);
		if (capacity != null) {
			syncCapacity();
			capacity = cacheCapacity.get(cs);
			virtualCapacity.put(cs, capacity);
		}
		return capacity;
	}

	private void clearOverHeads() {
		overheads.clear();
		dangerousOverheads.clear();
	}

	private void clearTracking() {
		resultList.clear();
		checkResultList.clear();
	}

	private void resetTrackingCapacity() {
		System.out.println("Reset state");

		totalCapacity = defaultTotalCapacity;
		virtualCapacity.clear();
		for (Map.Entry<CalculationServerInterface, Integer> entry : cacheCapacity.entrySet()) {
			virtualCapacity.put(entry.getKey(), entry.getValue());
		}
		countCapacity.clear();
		for (Map.Entry<Integer, Integer> entry : cacheCountCapacity.entrySet()) {
			countCapacity.put(entry.getKey(), entry.getValue());
		}

		result = 0;
	}

	private void updateTrackingCapacity(int checkFactor) {
		totalCapacity = totalCapacity / checkFactor;
		for (Map.Entry<Integer, Integer> entry : countCapacity.entrySet()) {
			countCapacity.put(entry.getKey(), entry.getValue() / checkFactor);
		}
	}

}
