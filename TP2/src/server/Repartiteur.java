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
import shared.OperationTodo;
import shared.RepartiteurInterface;
import shared.Response;
import shared.Account;

public class Repartiteur implements RepartiteurInterface {

	private class CalculationServerComparator implements Comparator<CalculationServerInterface> {
		private String mode;

		public CalculationServerComparator(){
			mode = "ascending";
		}

		public CalculationServerComparator(String m){
			if(m == "descending") mode = m;
			else System.out.println("ERROR: mode non regconize, ascending par default");
		}

		@Override
		public int compare(CalculationServerInterface o1, CalculationServerInterface o2) {
			if(mode == "descending") return Integer.compare(getCapacity(o2), getCapacity(o1));
			return Integer.compare(getCapacity(o1), getCapacity(o2));
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
	}

	private void run() {
		try {
			RepartiteurInterface stub = (RepartiteurInterface) UnicastRemoteObject.exportObject(this, 5005);
			Registry registry = LocateRegistry.createRegistry(5005);

			registry.rebind("repartiteur", stub);

			if(authServer == null){
				return;
			}

			checkExistingRepartiteur();

			if (account.userName == null || account.password == null) {
				System.err.println("Le fichier d'informations du répartiteur n'a pas le format attendu.");
				return;
			}

			boolean success = authServer.loginRepartiteur(account);
			if (!success) {
				System.err.println("Erreur lors du login du répartiteur :");
				return;
			}

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
			
			executorService = Executors.newFixedThreadPool(calculationServers.size());

			System.out.println("Repartiteur ready. MODE: " + mode);
		} catch (ConnectException e) {
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.err.println("Erreur lors de la récupération des serveurs de calcul :\n" + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
		}
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
		resetTrackingCapacity();

		List<OperationTodo> list = parseStringToOperations(operations);
		int finalResult = 0;

		System.out.println("BEGIN ===============================================");
		switch (mode) {
		case "non-securise":
		/*
		*	Le mode non-securise consiste les etapes 4 5 7 de l'algo securise
		*/
			finalResult = delegateHandleOperationNonSecurise(list);
			break;

		case "securise":

		/*
		*	Algorithm de repartiteur mode securise

		!!! REMARQUE: Algorithm est extendable pour le test entre 3...n serveurs en Overriding 
		la fonction checkMalicious pour comparer plus de 2 resultats a chaque fois de maniere 
		prefere, le reste est gere automatiquement

		Il y a globalement 7 etapes: (le mot server calculation et serveur sont interchangeable)
			1. Detecter les serveurs lonely
			2. Isoler un serveur si le nombre total est impair
			3. Map les serveurs lonely au partner de capacity la plus petite
			4. Traiter les overheads ou prendre une partie des operations si necessaire		<-------
			5. Donner les operations aux servers													|
			6. Recuperer le resultat et mettre a cote les operations non valide						|
			7. Si il reste des operations, revenir au 4e	-----------------------------------------

		Le detail de chaque etape:
			1. Detecter les serveurs qui n'ont pas de partenaire de meme capacite
			e.g. Si on veut tester le resultat entre 2 serveur, et il existe 3 serveurs de capacite 4
			la 3e est un serveur "lonely"
			
			2. Si le nombre total des serveurs sont impair, il y aura surement un serveur qui n'aura pas
			de partenaire, meme si avec une capacite different, on cherche alors a isoler ce serveur pour
			ce calcul, on isole alors le serveur la capacite la plus petite parmi les lonely

			3. Comme on va comparer le resultat de 2 serveurs, ces 2 serveurs devraient faire les memes
			operations, donc leur capacite doit etre egal. Car en 4e etape, on va calculer le nombre des
			operations maximal pour chaque serveur pour que le taux de refus ne soit pas trop eleve. Donc
			ici on cherche a "mapper" (i.e. remplacer) la capacite du serveur lonely par la capacite de
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
			redistribuer plus tard

			5. Nbre d'operation = capcite + overhead + dangerous

			6. On recupere et test le resultat, si ce n'est pas valide on rajoute a la liste remaining

			7. Si la liste remaining n'est pas vide, on revient a l'etape 4 avec cette liste comme param
		*/
			
			if (calculationServers.size() > 1) {
				CalculationServerInterface idle = detectLonelyServers(NUMBER_OF_CHECK_REQUIRED);
				updateTrackingCapacity(NUMBER_OF_CHECK_REQUIRED);

				System.out.println("Current total capacity: "+totalCapacity);

				finalResult = delegateHandleOperationSecurise(list, NUMBER_OF_CHECK_REQUIRED);

				if (idle != null)
					calculationServers.add(idle); // Undo modification

			} else {
				System.out.println("Il n'y a pas suffisamment de serveur de calcul");
				System.out.println("Switch au mode non-securise automatiquement");
				finalResult = delegateHandleOperationNonSecurise(list);
			}
			break;

		default:
			throw new RemoteException("Erreur: mode non reconnu");
		}
		System.out.println("FINISH ===============================================");
		System.out.println("");

		return (int) finalResult%4000;
	}

	private List<OperationTodo> parseStringToOperations(List<String> operations) {
		List<OperationTodo> list = new ArrayList<>();
		for (String op : operations) {
			String[] algo = op.split(" ");
			list.add(new OperationTodo(algo[0], Integer.parseInt(algo[1])));
		}
		return list;
	}

	private int delegateHandleOperationNonSecurise(List<OperationTodo> list) {
		return delegateHandleOperationSecurise(list, 1);
	}

	private int delegateHandleOperationSecurise(List<OperationTodo> list, int checkFactor) {

		clearOverHeads();

		List<OperationTodo> remainingList = new ArrayList<>();

		System.out.println("4. Traiter les overheads ou prendre une partie des operations si necessaire");
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
			System.out.println("7. Si il reste des operations, revenir au 4e");
			return temp + delegateHandleOperationSecurise(remainingList, checkFactor);
		}
		return temp;
	}

	private List<List<Future<Response>>> assignTasks(List<OperationTodo> list, int checkFactor) {
		System.out.println("5. Donner les operations aux servers");

		int from = 0;
		int i = 0;
		
		List<List<Future<Response>>> globalResultList = new ArrayList<>();
		for (int j=0; j<checkFactor; j++){
			globalResultList.add(new ArrayList<>());
		}
		List<Future<Response>> resultList = globalResultList.get(0);
		List<List<Future<Response>>> checkResultList = null;
		if (globalResultList.size() > 1) checkResultList = globalResultList.subList(1, globalResultList.size());

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
				for (List<Future<Response>> checkResult : checkResultList){
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

		if(todos.size() > 0){
			customResult.add(executorService.submit(() -> { // lambda Java 8 feature
				int res = 0;
	
				do {
					try {
						res = cs.calculateOperations(todos);
					} catch (RemoteException e) {
	
						e.printStackTrace();
					}
				} while (res == -1);
	
				return new Response(cs, todos, res);
			}));
		}
	}

	private int getResult(List<List<Future<Response>>> globalResultList, List<OperationTodo> remainingList) {
		int res = 0;
		List<Future<Response>> result = globalResultList.get(0);
		boolean check = globalResultList.size() == 1 ? false : true;
		if(check) System.out.println("6. Recuperer le resultat et mettre a cote les operations non valide");
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

	private boolean checkMalicious(int res, List<List<Future<Response>>> checkResultList, int index){
		List<Future<Response>> checkResult = checkResultList.get(0);
		try{
			if(res == checkResult.get(index).get().res) return true;
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

	private CalculationServerInterface detectLonelyServers(int checkFactor) {
		// 1. Detecter les serveurs lonely

		System.out.println("1. Detecter les serveurs lonely");
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

		//*************************************************************** */

		// 2. Isoler un serveur si le nombre total est impair
		CalculationServerInterface idle = isolateIdleServerIfThereIs(lonelyServers, checkFactor);
		// 3. Map les serveurs lonely au partner de capacity la plus petite		
		mapLonelyServerToLowestPartner(lonelyServers, checkFactor);

		return idle;
	}

	private CalculationServerInterface isolateIdleServerIfThereIs(List<CalculationServerInterface> lonelyServers,
			int checkFactor) {

		System.out.println("2. Isoler un serveur si le nombre total est impair");
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

		System.out.println("3. Map les serveurs lonely au partner de capacity la plus petite");

		// Sort ascending la liste des lonely en fonction de capacite
		Collections.sort(lonelyServers, new CalculationServerComparator());
		for (int i = 0; i < lonelyServers.size(); i += checkFactor) {
			int lowestCapacity = getCapacity(lonelyServers.get(i));
			totalCapacity += lowestCapacity * checkFactor;

			for(int j=i+1; j < (i+checkFactor); j++){
				virtualCapacity.put(lonelyServers.get(j), lowestCapacity);
				System.out.println("Map "+getCapacity(lonelyServers.get(j))+" to "+lowestCapacity);
			}
			Integer currentValue = countCapacity.get(lowestCapacity);
			if (currentValue == null)
				currentValue = 0;
			countCapacity.put(lowestCapacity, currentValue + checkFactor);
		}
		// Sort descending pour donner les operations aux servers de gros capacite first
		Collections.sort(calculationServers, new CalculationServerComparator("descending"));
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

		totalCapacity = defaultTotalCapacity;
		virtualCapacity.clear();
		for (Map.Entry<CalculationServerInterface, Integer> entry : cacheCapacity.entrySet()) {
			virtualCapacity.put(entry.getKey(), entry.getValue());
		}
		countCapacity.clear();
		for (Map.Entry<Integer, Integer> entry : cacheCountCapacity.entrySet()) {
			countCapacity.put(entry.getKey(), entry.getValue());
		}
	}

	private void updateTrackingCapacity(int checkFactor) {
		totalCapacity = totalCapacity / checkFactor;
		for (Map.Entry<Integer, Integer> entry : countCapacity.entrySet()) {
			countCapacity.put(entry.getKey(), entry.getValue() / checkFactor);
		}
	}

}
