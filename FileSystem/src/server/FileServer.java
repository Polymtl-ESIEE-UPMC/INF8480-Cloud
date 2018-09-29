package server;

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

import shared.Account;
import shared.AuthServerInterface;
import shared.Fichier;
import shared.FileServerInterface;
import shared.MD5CheckSum;
import shared.Response;

public class FileServer implements FileServerInterface {
	private static final String FILES_DIR_NAME = "files";

	public static void main(String[] args) {
		File filesDir = new File(FILES_DIR_NAME);
		filesDir.mkdir();
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
			boolean created =  newFile.createNewFile();
			if(created){
				Fichier fichier = new Fichier(fileName);
				fichier.createLock();
				return true;
			}else{
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
			Fichier fichier = new Fichier(file.getName(), true, "test");
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
	public Response lockFile(Account account, String name) throws RemoteException {
		if (!authServer.verifyAccount(account))
			throw new RemoteException("Ce compte n'existe pas ou le mot de passe est invalide");
		String filePath = FILES_DIR_NAME + "/" + name;
		File file = new File(filePath);
		if (file.exists()) {
			return (new Fichier(file.getName())).lock_fichier(account);
		} else {
			throw new RemoteException("Ce fichier n'existe pas sur le serveur!");
		}
	}

	@Override
	public Response unlockFile(Account account, String name) throws RemoteException {
		if (!authServer.verifyAccount(account))
			throw new RemoteException("Ce compte n'existe pas ou le mot de passe est invalide");
		String filePath = FILES_DIR_NAME + "/" + name;
		File file = new File(filePath);
		if (file.exists()) {
			return (new Fichier(file.getName())).unlock_fichier(account);
		} else {
			throw new RemoteException("Ce fichier n'existe pas sur le serveur!");
		}
	}

	@Override
	public Response pushFile(Account account, String name, byte[] fileContent) throws RemoteException {
		if (!authServer.verifyAccount(account))
			throw new RemoteException("Ce compte n'existe pas ou le mot de passe est invalide");
		String filePath = FILES_DIR_NAME + "/" + name;
		File file = new File(filePath);
		if (file.exists()) {
			Fichier fichier = new Fichier(file.getName());
			if (fichier.lockState()){
				if (fichier.lockByUser.equals(account.userName)){
					try {
						FileOutputStream stream = new FileOutputStream(filePath);
						try {
							stream.write(fileContent);
							System.out.println("Le fichier serveur a été mis à jour avec la version locale.");
						} catch (IOException e) {
							System.err.println(e.getMessage());
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
					}
					System.out.println(unlockFile(account, name).msg);
					return new Response(1, "SUCCES: Le fichier est push sur le serveur");
				}else{
					throw new RemoteException("Locked by someone else!");	
				}
			}else{
				throw new RemoteException("Please lock file first!");	
			}
		} else {
			System.out.println("Le fichier n'existe pas sur le serveur, auto creer un fichier");
			if(createFile(account, name)){
				System.out.println(lockFile(account, name).msg);
				return pushFile(account, name, fileContent);
			}else{
				throw new RemoteException("auto creer fichier echoue, push non realise");
			}			
		}
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
