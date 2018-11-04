package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

//Interface pour les fonctions du serveur de calculs

public interface CalculationServerInterface extends Remote {
    public int calculateOperations(List<OperationTodo> operation, Account account) throws RemoteException, OperationRefusedException;
    public int getCapacity() throws RemoteException;
}
