package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FileServerInterface extends Remote {
	void createFile(Account account, String fileName) throws RemoteException;
	String[] listFiles(Account account) throws RemoteException;
	//https://stackoverflow.com/questions/304268/getting-a-files-md5-checksum-in-java
	byte[] getFile(Account account, String name, String checksum) throws RemoteException;
	String lockFile(Account account, String name, String checksum) throws RemoteException;
	String pushFile(Account account, String name, byte[] fileContent) throws RemoteException;
	byte[] syncLocalDirectory(Account account) throws RemoteException;
}
