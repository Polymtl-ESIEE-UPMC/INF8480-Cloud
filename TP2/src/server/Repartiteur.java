package server;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
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

import shared.AuthServerInterface;
import shared.CalculationServerInfo;
import shared.CalculationServerInterface;
import shared.InterfaceLoader;
import shared.OperationRefusedException;
import shared.OperationTodo;
import shared.PortsSettings;
import shared.RepartiteurInterface;
import shared.Response;
import shared.Account;

public class Repartiteur implements RepartiteurInterface {

	private class CalculationServerComparator implements Comparator<CalculationServerInterface> {
		private boolean ascending;

		public CalculationServerComparator() {
			ascending = true;
		}

		public CalculationServerComparator(boolean ascending) {
			this.ascending = ascending;
		}

		@Override
		public int compare(CalculationServerInterface o1, CalculationServerInterface o2) {
			if (ascending)
				return Integer.compare(getCapacity(o1), getCapacity(o2));
			return Integer.compare(getCapacity(o2), getCapacity(o1));
		}
	}

	private static final String CREDENTIALS_FILENAME = "credentialsRepartiteur";
	private static final double TOLERANCE_REFUSE_RATE = 0.33;
	private static final int NUMBER_OF_CHECK_REQUIRED = 2;
	private static String mode = "non-securise";

	public static void main(String[] args) {
		String authServerHostName = null;

		for (int i = 0; i < args.length; i++) {
			try {
				switch (args[i]) {
				case "-i":
					authServerHostName = args[++i];
					break;
				case "-s":
					mode = "securise";
					break;
				default:
					System.err.println("Paramètre inconnu et ignoré : " + args[i]);
					break;
				}
			} catch (IndexOutOfBoundsException e) {
				System.err.println("Paramètres invalides");
				printHelp();
				return;
			}
		}

		Repartiteur server = new Repartiteur(authServerHostName);
		server.run();
	}

	// Affiche l'aide sur les commandes
	public static void printHelp() {
		System.out.println("Le mode non-sécurisé est le mode par défaut\nListe des commandes :\n" + "-i ip_adress\n-s : sécurisé");
	}

	private AuthServerInterface authServer = null;
	private List<CalculationServerInterface> calculationServers = null;
	private CalculationServerInterface idle = null;
	private final String userName = "tempName";
	private final String password = "temppassword";
	private Account account = null;
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
	private List<CalculationServerInterface> disconnectedServers = null;
	private boolean handledDisconnected = true;

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
		disconnectedServers = new ArrayList<>();
	}

	private void run() {
		if (authServer == null) {
			return;
		}
		
		try {
			RepartiteurInterface stub = (RepartiteurInterface) UnicastRemoteObject.exportObject(this, PortsSettings.repartiteurPort);
			Registry registry = LocateRegistry.createRegistry(PortsSettings.repartiteurPort);
			
			registry.rebind("repartiteur", stub);
			
			checkExistingRepartiteur();
			
			if (account.userName == null || account.password == null) {
				System.err.println("Le fichier d'informations du répartiteur n'a pas le format attendu.");
				return;
			}

			boolean success = authServer.loginRepartiteur(account);
			if (!success) {
				System.err.println("Erreur lors du login du répartiteur.");
				return;
			}

			System.out.println("Serveurs disponibles : ");
			for (CalculationServerInfo sd : authServer.getCalculationServers()) {
				CalculationServerInterface cs = InterfaceLoader.loadCalculationServer(sd.ip, sd.port);
				
				if(cs == null)
					continue;
				
				System.out.println(sd);

				calculationServers.add(cs);

				defaultTotalCapacity += sd.capacity;
				
				cacheCapacity.put(cs, sd.capacity);
				cacheCountCapacity.merge(sd.capacity, 1, Integer::sum);

				overheads.put(sd.capacity, 0);
				dangerousOverheads.put(sd.capacity, 0);
			}

			executorService = Executors.newFixedThreadPool(calculationServers.size());

			System.out.println("Repartiteur ready. MODE: " + mode);
		} catch (ConnectException e) {
			
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.err.println("Erreur lors de la récupération des serveurs de calcul :\n" + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
		}

		resetTrackingCapacity();
		handleMode();
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
				String password = fileReader.readLine();
				account = new Account(login, password);
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
			System.err.println("Erreur liée à la connexion au serveur. Abandon de la tentative de création de compte répartiteur.");
		}

		reader.close();
	}

	/*
	 * Méthodes accessibles par RMI.
	 */
	@Override
	public int handleOperations(List<String> operations) throws RemoteException {
		System.out.println("BEGIN ===============================================");
		List<OperationTodo> list = parseStringToOperations(operations);
		int finalResult = handleOperations(list, null);
		System.out.println("FINISH ===============================================");
		System.out.println("");
		return finalResult;
	}

	private int handleOperations(List<OperationTodo> list, Integer unused) {
		int finalResult = 0;

		switch (mode) {
			case "securise":
				/*
				* Le mode securise consiste les etapes 4 5 7 de l'algo non-securise
				*/
				finalResult = delegateHandleOperationSecurise(list);
				break;

			case "non-securise":

				/*
				*	Algorithme du répartiteur mode non-securisé

				!!! REMARQUE: l'algorithme peut comparer le résultat de 2...n serveurs en modifiant la 
				constante NUMBER_OF_CHECK_REQUIRED. La fonction checkMalicious va alors comparer le nombre 
				de résultats choisis à chaque fois, le reste est géré automatiquement.

				Il y a globalement 7 étapes: (ici par serveur nous entendons serveur de calcul)
					1. Détecter les serveurs sans partenaires
					2. Isoler un serveur si le nombre total de serveurs est impair
					3. Associeer les serveurs sans partenaires au serveur de plus petite capacité
					4. Traiter les overheads ou prendre une partie des opérations si necessaire
					5. Donner les opérations aux serveurs
					6. Récupérer le résultat et mettre de côté les résultats non valides
					7. S'il reste des opérations, revenir à l'étape 4

				Le détail de chaque étape:
					1. Détecter les serveurs qui n'ont pas de partenaire de même capacité
					e.g. Si on veut tester le résultat entre 2 serveurs et qu'il existe 3 serveurs de capacité 4
					le 3e est un serveur sans partenaire
					
					2. Si le nombre total des serveurs est impair, il y aura surement un serveur qui n'aura pas
					de partenaire, même avec une capacité differente. On cherche alors à isoler ce serveur pour
					le calcul. On isole le serveur avec la capacité minimale.

					3. Comme on compare le résultat de 2 serveurs, ces 2 serveurs devraient faire les mêmes
					opérations, leur capacité doit être similaire. Puisque à la 4e étape on va calculer le nombre
					d'operation maximal pour chaque serveur assurant que le taux de refus ne soit pas trop élevé,
					on cherche a "mapper" (i.e. remplacer) la capacite du serveur sans partenaire par la capacite de
					sont partenaire qui est plus peite, cad on donne les operations pour que le taux de refus 
					tf = min(tf1, tf2).
						
					4. On decide le nombre des operations de trop pour chaque serveur en fonction de sa capacite
					On a le taux de refus moyen tfm = (Nbre operations - totalCapacity/2) / (4 * totalCapacity/2)
					(comme 2 servers fait la meme chose a chaque fois, donc la capacite /2)
					D'abord on veut que le tfm < SEUIL, si tfm > SEUIL, on mettre quelque operation dans une liste
					a cote pour faire plus tard (remaining) et recalcule tfm
					Une fois qu'on a la liste a faire des operatoins, on calcule le nombre d'operation pour chaque
					Comme on veut tfm = tf1 = tf2 = ... = tfn
					le Nbre d'operation de trop (overhead) = tfm * 4 * capacite
					Si par exemple c'est 3.4, on prend donc 3 et rajoute 0.4 a` une somme "dangerous" pour 
					redistribuer1 plus tard

					5. Nbre d'operation = capcite + overhead + dangerous

					6. On recupere et test le resultat, si ce n'est pas valide on rajoute a la liste remaining

					7. Si la liste remaining n'est pas vide, on revient a l'etape 4 avec cette liste comme param
				*/

				finalResult = delegateHandleOperationNonSecurise(list, NUMBER_OF_CHECK_REQUIRED);
				break;

			default:
				break;
		}

		return finalResult % 4000;
	}

	private List<OperationTodo> parseStringToOperations(List<String> operations) {
		List<OperationTodo> list = new ArrayList<>();
		for (String op : operations) {
			String[] algo = op.split(" ");
			list.add(new OperationTodo(algo[0], Integer.parseInt(algo[1])));
		}
		return list;
	}

	private int delegateHandleOperationSecurise(List<OperationTodo> list) {
		return delegateHandleOperationNonSecurise(list, 1);
	}

	private int delegateHandleOperationNonSecurise(List<OperationTodo> list, int checkFactor) {

		clearOverHeads();
		if (disconnectedServers.size() > 0 && !handledDisconnected){
			System.out.println("Nombre de serveur deconnecte: "+disconnectedServers.size());
			resetTrackingCapacity();
			handleDisconnected();
			handleMode();
			handledDisconnected = true;
			return handleOperations(list, null);
		}

		List<OperationTodo> remainingList = new ArrayList<>();

		// 4. Traiter les overheads ou prendre une partie des operations si necessaire
		if (list.size() > totalCapacity) {
			double averageRefusePercent = (double) (list.size() - totalCapacity) / (4 * totalCapacity);
			while (averageRefusePercent > TOLERANCE_REFUSE_RATE) {
				OperationTodo remain = list.get(list.size() - 1);
				remainingList.add(remain);
				list.remove(remain);
				averageRefusePercent = (double) (list.size() - totalCapacity) / (4 * totalCapacity);
			}

			calculateOverheadForEach(averageRefusePercent);
		}

		List<List<Future<Response>>> globalResultList = assignTasks(list, checkFactor);

		int temp = getResult(globalResultList, remainingList);
		if (remainingList.size() > 0) {
			// 7. Si il reste des operations, revenir au 4e
			return temp + delegateHandleOperationNonSecurise(remainingList, checkFactor);
		}
		return temp;
	}

	private List<List<Future<Response>>> assignTasks(List<OperationTodo> list, int checkFactor) {
		// 5. Donner les operations aux servers

		int from = 0;
		int i = 0;

		List<List<Future<Response>>> globalResultList = new ArrayList<>();
		for (int j = 0; j < checkFactor; j++) {
			globalResultList.add(new ArrayList<>());
		}
		List<Future<Response>> resultList = globalResultList.get(0);
		List<List<Future<Response>>> checkResultList = null;
		if (globalResultList.size() > 1)
			checkResultList = globalResultList.subList(1, globalResultList.size());

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

			sendTask(todo, calculationServers.get(i), resultList);
			if (checkResultList != null) {
				int j = i;
				for (List<Future<Response>> checkResult : checkResultList) {
					sendTask(todo, calculationServers.get(j + checkFactor - 1), checkResult);
					j++;
				}
			}
			i += checkFactor;

			from = to;
		}
		return globalResultList;
	}

	private void sendTask(List<OperationTodo> todos, CalculationServerInterface cs,
			List<Future<Response>> customResult) {

		if (todos.size() > 0) {
			customResult.add(executorService.submit(() -> { // lambda Java 8 feature
				int res = 0;
				boolean disconnect = false;

				do {
					try {
						res = cs.calculateOperations(todos, account);
					} catch (ConnectException e) {
						System.err.println();
						System.err.println("Server failure");
						System.err.println("Erreur: " + e.getMessage());

						disconnectedServers.add(cs);
						disconnect = true;
						handledDisconnected = false;
						break;
					} catch (RemoteException e) {
						e.printStackTrace();
					} catch (OperationRefusedException e) {
						e.printStackTrace();
					}
				} while (res == -1);

					
				if(disconnect) {
					return new Response(cs, todos, -1);
				}
				return new Response(cs, todos, res);
			}));
		}
	}

	private int getResult(List<List<Future<Response>>> globalResultList, List<OperationTodo> remainingList) {
		int res = 0;
		List<Future<Response>> result = globalResultList.get(0);
		boolean check = globalResultList.size() != 1;
		// 6. Recuperer le resultat et mettre a cote les operations non valide
		for (int i = 0; i < result.size(); i++) {
			try {
				if (!check) {
					res = (res + result.get(i).get().res) % 4000;
				} else {
					Response response = result.get(i).get();
					int temp = response.res;
					List<List<Future<Response>>> checkResultList = globalResultList.subList(1, globalResultList.size());
					if (checkMalicious(temp, checkResultList, i)) {
						res = (res + temp) % 4000;
					} else {
						remainingList.addAll(response.operations);
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

	private boolean checkMalicious(int res, List<List<Future<Response>>> checkResultList, int index) {
		List<Future<Response>> checkResult = checkResultList.get(0);
		try {
			int checkres = checkResult.get(index).get().res;
			if (res == checkres && res != -1 && checkres != -1)
				return true;
		} catch (ExecutionException e) {
			e.printStackTrace();
			return false;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
		return false;
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

	private void handleDisconnected(){
		System.out.println("Handle disconnected");
		for(CalculationServerInterface cs: disconnectedServers){
			calculationServers.remove(cs);
			totalCapacity -= getCapacity(cs);
			Integer temp = countCapacity.get(getCapacity(cs));
			if(temp != null){
				countCapacity.put(getCapacity(cs), temp-1);
			}
			virtualCapacity.put(cs, null);
		}
	}

	private void handleMode(){
		System.out.println("Handle mode");
		if(mode == "non-securise"){
			if (calculationServers.size() >= NUMBER_OF_CHECK_REQUIRED) {
				detectLonelyServers(NUMBER_OF_CHECK_REQUIRED);
				updateTrackingCapacity(NUMBER_OF_CHECK_REQUIRED);
	
			} else {
				System.out.println("Il n'y a pas suffisamment de serveur de calcul");
				System.out.println("Switch au mode securise automatiquement");
				mode = "securise";
			}
		}
		System.out.println("MODE: "+mode);
		System.out.println("Nombre de serveur utilise: "+calculationServers.size());
	}

	private void detectLonelyServers(int checkFactor) {
		// 1. Detecter les serveurs lonely

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

		// 2. Isoler un serveur si le nombre total est impair
		isolateIdleServerIfThereIs(lonelyServers, checkFactor);
		// 3. Map les serveurs lonely au partner de capacity la plus petite
		mapLonelyServerToLowestPartner(lonelyServers, checkFactor);

	}

	private void isolateIdleServerIfThereIs(List<CalculationServerInterface> lonelyServers,
			int checkFactor) {

		// 2. Isoler un serveur si le nombre total est impair
		if (calculationServers.size() % checkFactor != 0) {
			idle = lonelyServers.get(0);
			lonelyServers.remove(idle);
		}
		if (idle != null)
			calculationServers.remove(idle);
	}

	private void mapLonelyServerToLowestPartner(List<CalculationServerInterface> lonelyServers, int checkFactor) {

		// 3. Map les serveurs lonely au partner de capacity la plus petite

		// Sort ascending la liste des lonely en fonction de capacite
		Collections.sort(lonelyServers, new CalculationServerComparator());
		for (int i = 0; i < lonelyServers.size(); i += checkFactor) {
			int lowestCapacity = getCapacity(lonelyServers.get(i));
			totalCapacity += lowestCapacity * checkFactor;

			for (int j = i + 1; j < (i + checkFactor); j++) {
				virtualCapacity.put(lonelyServers.get(j), lowestCapacity);
				System.out.println("Map " + getCapacity(lonelyServers.get(j)) + " to " + lowestCapacity);
			}
			Integer currentValue = countCapacity.get(lowestCapacity);
			if (currentValue == null)
				currentValue = 0;
			countCapacity.put(lowestCapacity, currentValue + checkFactor);
		}
		// Sort descending pour donner les operations aux servers de gros capacite first
		Collections.sort(calculationServers, new CalculationServerComparator(false));
	}

	private void syncCapacity() {
		// Mettre a jour la capacite reelle de chaque serveur, si le cache est non-valide, appel RMI
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
		// retourner la capacite virutelle actuellement utilise. Si non-trouve -> sync
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

	private void resetTrackingCapacity() {
		System.out.println("Reset state");

		if(idle != null) {
			calculationServers.add(idle);
			idle = null;
		}
		totalCapacity = defaultTotalCapacity;
		virtualCapacity.clear();
		for (Map.Entry<CalculationServerInterface, Integer> entry : cacheCapacity.entrySet()) {
			virtualCapacity.put(entry.getKey(), entry.getValue());
		}
		countCapacity.clear();
		countCapacity.putAll(cacheCountCapacity);
	}

	private void updateTrackingCapacity(int checkFactor) {
		totalCapacity = totalCapacity / checkFactor;
		for (Map.Entry<Integer, Integer> entry : countCapacity.entrySet()) {
			countCapacity.put(entry.getKey(), entry.getValue() / checkFactor);
		}
	}
}
