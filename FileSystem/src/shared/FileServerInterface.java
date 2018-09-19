package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FileServerInterface extends Remote {
	void createFile(String fileName) throws RemoteException;
	String[] listFiles() throws RemoteException;
	//https://stackoverflow.com/questions/304268/getting-a-files-md5-checksum-in-java
	byte[] getFile(String name, String checksum) throws RemoteException;
	String lockFile(String name, String checksum) throws RemoteException;
	String pushFile(String name, byte[] fileContent) throws RemoteException;
	byte[] syncLocalDirectory() throws RemoteException;
}
