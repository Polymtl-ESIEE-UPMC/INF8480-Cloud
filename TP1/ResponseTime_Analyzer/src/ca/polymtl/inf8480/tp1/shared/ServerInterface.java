package ca.polymtl.inf8480.tp1.shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerInterface extends Remote {
	void empty(byte[] a, byte[] b) throws RemoteException;
	int execute(int a, int b) throws RemoteException;
}
