package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

//Interface pour les fonctions du serveur d'authentification
public interface AuthServerInterface extends Remote {
	boolean newAccount(String login, String password) throws RemoteException;
	boolean verifyAccount(Account account) throws RemoteException;
}
