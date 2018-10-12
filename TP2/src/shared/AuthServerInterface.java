package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

//Interface pour les fonctions du serveur d'authentification
public interface AuthServerInterface extends Remote {
	public boolean newAccount(Account account) throws RemoteException;
	public boolean newRepartiteur(Account account) throws RemoteException;
	public boolean loginRepartiteur(Account account) throws RemoteException;
	public boolean registerCalculationServer(CalculationServerInfo serverDescription) throws RemoteException;
	public boolean verifyAccount(Account account) throws RemoteException;
	public boolean verifyRepartiteur(Account account) throws RemoteException;
	public String getRepartiteurIp() throws RemoteException;
	public List<CalculationServerInfo> getCalculationServers() throws RemoteException;
}
