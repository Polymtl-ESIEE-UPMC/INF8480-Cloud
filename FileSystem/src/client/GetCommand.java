package client;

import shared.Account;

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;

import shared.AuthServerInterface;
import shared.FileServerInterface;
import shared.MD5CheckSum;

public class GetCommand extends Command{

    public void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException{
        try{
            String md5 = MD5CheckSum.generateChecksum(fileName);
            fileServer.getFile(account, fileName, md5);
        } catch (NoSuchAlgorithmException e){
            System.err.println(e.getMessage());
        }
    }
}