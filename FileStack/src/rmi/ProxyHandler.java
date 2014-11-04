package rmi;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ProxyHandler<T> implements InvocationHandler, Serializable {

	public Class<T> c;
	InetSocketAddress aaddress;
	
	public ProxyHandler(Class<T> c, InetSocketAddress aaddress)
	{
		this.c = c;
		this.aaddress = aaddress;
	}
	
	
	private boolean checkequality(ProxyHandler<T> a, ProxyHandler<T> b)
	{
		if(a.aaddress.toString().equals(b.aaddress.toString()) && a.getClass().equals(b.getClass()))
		{
			return true;//if()check for interfaces if other didnt work
		}
		return false;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable 
	{
		Object result = null;
		if(method.getName().contains("equals"))
		{
			if(args[0]==null)
				return false;
			else
			{
				ProxyHandler<T> ph = (ProxyHandler<T>) Proxy.getInvocationHandler(args[0]);
				return checkequality(this, ph);
				//return (this.aaddress==ph.aaddress && this.getClass()==ph.getClass());
			}
				
		}
		else if(method.getName().contains("hashCode"))
		{
			return this.hashCode2();
		}
		else if(method.getName().contains("toString"))
		{
			return "Address: "+this.aaddress.toString()+"    ;     Class: "+this.getClass().getName()+"\n";
		}
		else
		{
			
			ObjectInputStream in;
			ObjectOutputStream out;
			Socket proxySocket = new Socket();
			try
			{
				proxySocket.connect(aaddress);
				
				//Initializing input and output streams
				out = new ObjectOutputStream(proxySocket.getOutputStream());
				in = new ObjectInputStream(proxySocket.getInputStream());
				
				//Sending method, parameters and parameter types to the skeleton
				out.writeObject(method.getName());
				out.writeObject(args);
				out.writeObject(method.getParameterTypes());

				//Reading the result sent in by the skeleton
				result = in.readObject();
				
				
			}
			catch(IOException e)
			{
				throw new RMIException(method.getName());
			}
			
			if(result instanceof Exception)
				throw (Throwable) result;
			else
			{
				out.close();
				in.close();
				proxySocket.close();
				return result;
			}
		}
		
			
		
	}

	public int hashCode2() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((aaddress == null) ? 0 : aaddress.hashCode());
		result = prime * result + ((c == null) ? 0 : c.hashCode());
		return result;
	}
}
