package shared;

import java.io.Serializable;

public class CalculationServerInfo implements Serializable{
    public String ip;
    public int port;
    public int capacity;
    
    public CalculationServerInfo(String ip, int port, int capacity){
        this.ip = ip;
        this.port = port;
        this.capacity = capacity;
    }

    @Override
    public String toString(){
        return ip + ":"+ port + " et capacity : " + capacity;
    }
} 