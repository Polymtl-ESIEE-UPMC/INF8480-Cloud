package shared;

import java.io.Serializable;

public class Fichier implements Serializable{

    public String name;
    public byte[] content;
    public boolean locked;
    public String lockUser;

    public Fichier(String name, byte[] content){
        this.name = name;
        this.content = content;
        this.locked = false;
        this.lockUser = "";
    }

    public Fichier(String name, boolean locked, String lockUser){
        this.name = name;
        this.content = new byte[0];
        this.locked = locked;
        this.lockUser = lockUser;
    }

    @Override
    public String toString() {
        return String.format("* %-20s%s", name, (locked) ? "vérouillé par : " + lockUser : "non vérouillé");
    }
}