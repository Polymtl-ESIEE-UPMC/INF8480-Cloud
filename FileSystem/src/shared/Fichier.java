package shared;

import java.io.Serializable;
import java.io.*;

public class Fichier implements Serializable{
    private static final String FILES_DIR_NAME = "files";

    public String name;
    public byte[] content;
    public String lock;
    public String lockByUser = "";

    public Fichier(String name, byte[] content){
        this.name = name;
        this.content = content;
        this.lock = FILES_DIR_NAME+"/."+name+"_lockstate";
    }

    public Fichier(String name, boolean legacy, String legacy1){
        this.name = name;
        this.content = new byte[0];
        this.lock = FILES_DIR_NAME+"/."+name+"_lockstate";
    }

    public Fichier(String name){
        this.name = name;
        this.content = new byte[0];
        this.lock = FILES_DIR_NAME+"/."+name+"_lockstate";
    }

    @Override
    public String toString() {
        return String.format("* %-20s%s", name, lockState() ? "vérouillé par : " + lockByUser : "non vérouillé");
    }

    public boolean lockState(){
        try {
            FileReader fileReader = new FileReader(lock);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String[] state = new String[2];
            for(int i=0; i<2; i++) {
                state[i] = bufferedReader.readLine();
            }   
            bufferedReader.close();
            lockByUser = state[1];
            return Boolean.parseBoolean(state[0]);         
        }
        catch(FileNotFoundException e) {
            System.out.println("Unable to open file '" + lock + "'");                
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean changeLockState(String state, String username){
        try {
            FileWriter fileWriter = new FileWriter(lock);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(state);
            bufferedWriter.newLine();
            bufferedWriter.write(username);
            bufferedWriter.close();
            return true;
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean lock_fichier(Account account){
        if(!lockState()){
            lockByUser = account.userName;
            return changeLockState(String.valueOf(true), lockByUser);
        }
        return false;
    }

    public boolean unlock_fichier(Account account){
        if(lockByUser.equals(account.userName)){
            lockByUser = "";
            return changeLockState(String.valueOf(false), lockByUser);
        }
        return false;
    }
}