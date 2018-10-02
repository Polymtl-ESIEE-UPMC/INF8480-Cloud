package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

//Interface pour les fonctions du serveur de fichiers

public interface FileServerInterface extends Remote {
	boolean createFile(Account account, String fileName) throws RemoteException;
	List<Fichier> listFiles(Account account) throws RemoteException;
	Fichier getFile(Account account, String fileName, String checksum) throws RemoteException;
	Fichier lockFile(Account account, String fileName, String checksum) throws RemoteException;
	boolean pushFile(Account account, String fileName, byte[] fileContent) throws RemoteException;
	List<Fichier> syncLocalDirectory(Account account) throws RemoteException;
}
