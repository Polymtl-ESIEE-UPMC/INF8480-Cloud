package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

//Interface pour les fonctions du répartiteur

public interface RepartiteurInterface extends Remote {
    public int handleOperations(List<String> operations) throws RemoteException;
    public Integer handleOperations(List<String> operations, String mode) throws RemoteException;
}
