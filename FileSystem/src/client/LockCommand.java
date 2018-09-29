package client;

import shared.Account;
import java.rmi.RemoteException;
import shared.AuthServerInterface;
import shared.FileServerInterface;
import shared.Response;

public class LockCommand extends Command{

    public void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException{
        Response res = fileServer.lockFile(account, fileName);
        System.out.println(res.msg);
    }
}