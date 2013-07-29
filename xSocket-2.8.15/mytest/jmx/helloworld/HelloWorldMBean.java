package jmx.helloworld;

public interface HelloWorldMBean {
	public String getName();

	public void setName(String name);

	public void printHello();

	public void printHello(String whoName);
}
