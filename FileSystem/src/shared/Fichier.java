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

}