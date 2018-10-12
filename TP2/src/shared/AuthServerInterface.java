package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

//Interface pour les fonctions du serveur d'authentification
public interface AuthServerInterface extends Remote {
	boolean newAccount(Account account) throws RemoteException;
	boolean newRepartiteur(Account account) throws RemoteException;
	boolean registerCalculationServer(CalculationServerInfo serverDescription) throws RemoteException;
	boolean verifyAccount(Account account) throws RemoteException;
	boolean verifyRepartiteur(Account account) throws RemoteException;
	String getRepartiteurIp() throws RemoteException;
	List<CalculationServerInfo> getCalculationServers() throws RemoteException;
}
