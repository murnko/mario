package libsvm;
public class svm_node implements java.io.Serializable
{
	public int index = 0;
	public double value = 0;
	
	public void SetValue(int index, double value){
		this.index = index;
		this.value = value;
		
	}
}

