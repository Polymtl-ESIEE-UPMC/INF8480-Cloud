package shared;

import java.util.List;

public class Response{
    public CalculationServerInterface cs;
    public List<OperationTodo> operations;
    public int res;

    public Response(CalculationServerInterface cs, List<OperationTodo> operations, int res){
        this.cs = cs;
        this.operations = operations;
        this.res = res;
    }
}