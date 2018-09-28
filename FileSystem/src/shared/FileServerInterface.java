package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface FileServerInterface extends Remote {
	boolean createFile(Account account, String fileName) throws RemoteException;
	List<Fichier> listFiles(Account account) throws RemoteException;
	//https://stackoverflow.com/questions/304268/getting-a-files-md5-checksum-in-java
	Fichier getFile(Account account, String fileName, String checksum) throws RemoteException;
	boolean lockFile(Account account, String fileName) throws RemoteException;
	boolean unlockFile(Account account, String fileName) throws RemoteException;
	boolean pushFile(Account account, String fileName, byte[] fileContent) throws RemoteException;
	List<Fichier> syncLocalDirectory(Account account) throws RemoteException;
}
