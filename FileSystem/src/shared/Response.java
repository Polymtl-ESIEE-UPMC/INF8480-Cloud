package shared;
import java.io.Serializable;

public class Response implements Serializable{

    private static final long serialVersionUID = 1L;
    
    public int code;
    public String msg;
    public Object object;

    public Response(int code, String msg){
        this.code = code;
        this.msg = msg;
    }
}