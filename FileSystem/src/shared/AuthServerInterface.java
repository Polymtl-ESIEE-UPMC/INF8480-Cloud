package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuthServerInterface extends Remote {
	boolean newAccount(String login, String password) throws RemoteException;
	boolean verifyAccount(String login, String password) throws RemoteException;
}
