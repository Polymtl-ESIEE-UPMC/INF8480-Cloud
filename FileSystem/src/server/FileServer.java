package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.AccessException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;

import shared.Account;
import shared.AuthServerInterface;
import shared.Fichier;
import shared.FileServerInterface;
import shared.MD5CheckSum;

public class FileServer implements FileServerInterface {
	private static final String FILES_DIR_NAME = "files";
	private static final String LOCKS_DIR_NAME = "locks";

	public static void main(String[] args) {
		File filesDir = new File(FILES_DIR_NAME);
		filesDir.mkdir();
		File locksDir = new File(LOCKS_DIR_NAME);
		locksDir.mkdir();
		FileServer server = new FileServer();
		server.run();
	}

	private AuthServerInterface authServer = null;

	public FileServer() {
		super();

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}
		authServer = loadAuthServer("127.0.0.1");
	}

	private AuthServerInterface loadAuthServer(String hostname) {
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

	private void run() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		try {
			FileServerInterface stub = (FileServerInterface) UnicastRemoteObject.exportObject(this, 0);

			Registry registry = LocateRegistry.getRegistry();
			registry.rebind("fileServer", stub);
			System.out.println("Server ready.");
		} catch (ConnectException e) {
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}

	// mettre filepath
	private static List<String> readAllText(String filePath) {
		List<String> text = new ArrayList<>();

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

	private boolean modifyLock(String fileName, String username, boolean locked) {
		try {
			String lockPath = LOCKS_DIR_NAME + "/." + fileName + "_lock";
			FileWriter fileWriter = new FileWriter(lockPath);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
			bufferedWriter.write(locked + "");
			bufferedWriter.newLine();
			if (locked) {
				bufferedWriter.write(username);
				bufferedWriter.newLine();
			}
			bufferedWriter.close();
			return true;
		} catch (IOException e) {
			System.out.println("Erreur lors de la manipulation du fichier lock de " + fileName);
			e.printStackTrace();
		}

		return false;
	}

	private static boolean isLocked(String fileName) {
		String filePath = LOCKS_DIR_NAME + "/." + fileName + "_lock";
		File file = new File(filePath);
		if (file.exists()) {
			return readAllText(filePath).get(0).equals("true");
		} else {
			return false;
		}
	}

	private static String fileOwner(String fileName) {
		String filePath = LOCKS_DIR_NAME + "/." + fileName + "_lock";
		File file = new File(filePath);
		if (file.exists()) {
			return readAllText(filePath).get(1);
		} else {
			return null;
		}
	}

	/*
	 * Méthodes accessibles par RMI.
	 */

	@Override
	public boolean createFile(Account account, String fileName) throws RemoteException {
		if (!authServer.verifyAccount(account))
			throw new RemoteException("Ce compte n'existe pas ou le mot de passe est invalide!");
		String filePath = FILES_DIR_NAME + "/" + fileName;
		File newFile = new File(filePath);
		if (newFile.exists()) {
			// le fichier existe déjà
			return false;
		}
		try {
			boolean created = newFile.createNewFile();
			if (created) {
				modifyLock(fileName, account.userName, false);
				return true;
			} else {
				return false;
			}
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public List<Fichier> listFiles(Account account) throws RemoteException {
		if (!authServer.verifyAccount(account))
			throw new RemoteException("Ce compte n'existe pas ou le mot de passe est invalide");
		List<Fichier> filesList = new ArrayList<>();
		final File filesFolder = new File(FILES_DIR_NAME);
		for (final File file : filesFolder.listFiles()) {
			// TODO: change with actual values
			boolean locked = isLocked(file.getName());
			String lockUser = "";
			if (locked) {
				lockUser = fileOwner(file.getName());
			}
			Fichier fichier = new Fichier(file.getName(), locked, lockUser);
			filesList.add(fichier);
		}
		return filesList;
	}

	@Override
	public Fichier getFile(Account account, String fileName, String checksum) throws RemoteException {
		if (!authServer.verifyAccount(account))
			throw new RemoteException("Ce compte n'existe pas ou le mot de passe est invalide");
		Fichier fichier = null;
		String filePath = FILES_DIR_NAME + "/" + fileName;
		File file = new File(filePath);
		if (file.exists()) {
			if (checksum == null || !MD5CheckSum.generateChecksum(filePath).equals(checksum)) {
				// envoyer le fichier
				try {
					byte[] fileContent = Files.readAllBytes(file.toPath());
					fichier = new Fichier(file.getName(), fileContent);
				} catch (IOException e) {
					System.err.println("Could not read " + file.getName() + " contents.");
				}
			}
		} else {
			throw new RemoteException("Ce fichier n'existe pas sur le serveur!");
		}
		return fichier;
	}

	@Override
	public Fichier lockFile(Account account, String fileName, String checksum) throws RemoteException {
		if (!authServer.verifyAccount(account))
			throw new RemoteException("Ce compte n'existe pas ou le mot de passe est invalide");

		boolean locked = isLocked(fileName);
		String lockUser = "";
		if (locked) {
			lockUser = fileOwner(fileName);
			throw new RemoteException("Le fichier est deja verouillé par " + lockUser);
		} else {
			modifyLock(fileName, account.userName, true);
			Fichier fichier = getFile(account, fileName, checksum);
			return fichier;
		}
	}

	@Override
	public boolean pushFile(Account account, String fileName, byte[] fileContent) throws RemoteException {
		if (!authServer.verifyAccount(account))
			throw new RemoteException("Ce compte n'existe pas ou le mot de passe est invalide");

		String filePath = FILES_DIR_NAME + "/" + fileName;
		File file = new File(filePath);
		if (file.exists()) {
			if (isLocked(fileName)) {
				String fileOwner = fileOwner(fileName);
				if (fileOwner(fileName).equals(account.userName)) {
					try {
						FileOutputStream stream = new FileOutputStream(filePath);
						try {
							stream.write(fileContent);
							System.out.println("Le fichier " + fileName
									+ " a été mis à jour avec la version locale de " + account.userName);
						} catch (IOException e) {
							System.err.println(e.getMessage());
							return false;
						} finally {
							try {
								if (stream != null)
									stream.close();
							} catch (IOException e) {
								System.err.println("Un problème inconnu est survenu : " + e.getMessage());
							}
						}
					} catch (FileNotFoundException e) {
						System.err.println(e.getMessage());
						return false;
					}
					modifyLock(fileName, "", false);
					return true;
				} else {
					throw new RemoteException("Le fichier est verouillé par: " + fileOwner);
				}
			} else {
				throw new RemoteException("opération refusée : vous devez verrouiller d'abord le fichier.");
			}
		}
		return false;
	}

	@Override
	public List<Fichier> syncLocalDirectory(Account account) throws RemoteException {
		if (!authServer.verifyAccount(account))
			throw new RemoteException("Ce compte n'existe pas ou le mot de passe est invalide");
		List<Fichier> filesList = new ArrayList<>();
		final File filesFolder = new File(FILES_DIR_NAME);
		for (final File file : filesFolder.listFiles()) {
			try {
				byte[] fileContent = Files.readAllBytes(file.toPath());
				Fichier fichier = new Fichier(file.getName(), fileContent);
				filesList.add(fichier);
			} catch (IOException e) {
				System.err.println("Could not read " + file.getName() + " contents.");
			}
		}
		return filesList;
	}
}
