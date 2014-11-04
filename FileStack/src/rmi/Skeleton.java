package rmi;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;


/** RMI skeleton

    <p>
    A skeleton encapsulates a multithreaded TCP server. The server's clients are
    intended to be RMI stubs created using the <code>Stub</code> class.

    <p>
    The skeleton class is parametrized by a type variable. This type variable
    should be instantiated with an interface. The skeleton will accept from the
    stub requests for calls to the methods of this interface. It will then
    forward those requests to an object. The object is specified when the
    skeleton is constructed, and must implement the remote interface. Each
    method in the interface should be marked as throwing
    <code>RMIException</code>, in addition to any other exceptions that the user
    desires.

    <p>
    Exceptions may occur at the top level in the listening and service threads.
    The skeleton's response to these exceptions can be customized by deriving
    a class from <code>Skeleton</code> and overriding <code>listen_error</code>
    or <code>service_error</code>.
*/
public class Skeleton<T>
{
	
	
	public Class<T> c = null;
	public T sserver = null;
	public InetSocketAddress aaddress = null;
	public ServerSocket listeningSocket = null;
	public boolean online;
	int port;
	
    /** Creates a <code>Skeleton</code> with no initial server address. The
        address will be determined by the system when <code>start</code> is
        called. Equivalent to using <code>Skeleton(null)</code>.

        <p>
        This constructor is for skeletons that will not be used for
        bootstrapping RMI - those that therefore do not require a well-known
        port.

        @param c An object representing the class of the interface for which the
                 skeleton server is to handle method call requests.
        @param server An object implementing said interface. Requests for method
                      calls are forwarded by the skeleton to this object.
        @throws Error If <code>c</code> does not represent a remote interface -
                      an interface whose methods are all marked as throwing
                      <code>RMIException</code>.
        @throws NullPointerException If either of <code>c</code> or
                                     <code>server</code> is <code>null</code>.
     */
	
	
	
	
    public Skeleton(Class<T> c, T server)
    {
        if(c == null || server == null)
        {
        	throw new NullPointerException();
        }
        if(c.isInterface() && isRemote(c))
    	{   
        	try
        	{
        		this.c = c;
            	this.sserver = server;
            
        	}
        	catch(Exception e)
        	{
        		e.printStackTrace();
        	}
    		
    	}
        else 
        {
        	throw new Error();
        }
    }

    /** Creates a <code>Skeleton</code> with the given initial server address.

        <p>
        This constructor should be used when the port number is significant.

        @param c An object representing the class of the interface for which the
                 skeleton server is to handle method call requests.
        @param server An object implementing said interface. Requests for method
                      calls are forwarded by the skeleton to this object.
        @param address The address at which the skeleton is to run. If
                       <code>null</code>, the address will be chosen by the
                       system when <code>start</code> is called.
        @throws Error If <code>c</code> does not represent a remote interface -
                      an interface whose methods are all marked as throwing
                      <code>RMIException</code>.
        @throws NullPointerException If either of <code>c</code> or
                                     <code>server</code> is <code>null</code>.
     */
    public Skeleton(Class<T> c, T server, InetSocketAddress address)
    {
    	if(c == null || server == null || address == null)
        {
        	throw new NullPointerException();
        }
    	if(c.isInterface() && isRemote(c))
    	{
        	this.c = c;
            this.sserver = server;
            aaddress = address;
            port = aaddress.getPort();
    	}
        else 
        {
        	throw new Error();
        }
    }

    
    
    public boolean isRemote(Class<T> c)
    {
    	for(Method method : c.getMethods())
    	{
    		boolean remote = false;
    		for(Class<?> e : method.getExceptionTypes())
    		{
    			if(e.getName().contains("RMIException"))
    				remote = true;
    		}
    		if(!remote)
    			return false;
    	}
    	return true;
    }
    
    
    /** Called when the listening thread exits.

        <p>
        The listening thread may exit due to a top-level exception, or due to a
        call to <code>stop</code>.

        <p>
        When this method is called, the calling thread owns the lock on the
        <code>Skeleton</code> object. Care must be taken to avoid deadlocks when
        calling <code>start</code> or <code>stop</code> from different threads
        during this call.

        <p>
        The default implementation does nothing.

        @param cause The exception that stopped the skeleton, or
                     <code>null</code> if the skeleton stopped normally.
     */
    protected void stopped(Throwable cause)
    {
    }

    /** Called when an exception occurs at the top level in the listening
        thread.

        <p>
        The intent of this method is to allow the user to report exceptions in
        the listening thread to another thread, by a mechanism of the user's
        choosing. The user may also ignore the exceptions. The default
        implementation simply stops the server. The user should not use this
        method to stop the skeleton. The exception will again be provided as the
        argument to <code>stopped</code>, which will be called later.

        @param exception The exception that occurred.
        @return <code>true</code> if the server is to resume accepting
                connections, <code>false</code> if the server is to shut down.
     */
    protected boolean listen_error(Exception exception)
    {
        return false;
    }

    /** Called when an exception occurs at the top level in a service thread.

        <p>
        The default implementation does nothing.

        @param exception The exception that occurred.
     */
    protected void service_error(RMIException exception)
    {
    }

    /** Starts the skeleton server.

        <p>
        A thread is created to listen for connection requests, and the method
        returns immediately. Additional threads are created when connections are
        accepted. The network address used for the server is determined by which
        constructor was used to create the <code>Skeleton</code> object.

        @throws RMIException When the listening socket cannot be created or
                             bound, when the listening thread cannot be created,
                             or when the server has already been started and has
                             not since stopped.
     */
    public synchronized void start() throws RMIException
    {
        try
        {	
        	if(aaddress == null)
        	{
        		listeningSocket = new ServerSocket(0);
            	port=listeningSocket.getLocalPort();
        	}
        	else
        		listeningSocket = new ServerSocket(port);

        	aaddress = new InetSocketAddress(port);
        	new Thread(new listeningService()).start();	
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        }
    }
    
    private class listeningService implements Runnable, Serializable
    {
    	public void run()
    	{
    		online = true;
    		try
    		{
    			while(online)
            	{
            		Socket ClientSocket = listeningSocket.accept();
        			SomeClient newClient = new SomeClient(ClientSocket);
        			Thread ClientThread = new Thread(newClient);
        			ClientThread.start();
            	}
    		}
    		catch(Exception e)
    		{
    		}
    	}
    }
    
    private class SomeClient implements Runnable, Serializable
	{
    	Socket ClientSocket = null;
	    ObjectInputStream in = null;
		ObjectOutputStream out = null;
		String methodName;
		Object[] params;
		Class<?>[] paramTypes;
		Object result;
		
		private SomeClient(Socket ClientSocket){
			this.ClientSocket = ClientSocket;
		}//end of SomeClient constructor
				

		
		public void run()
		{
			try
			{
				in = new ObjectInputStream(ClientSocket.getInputStream());
				out = new ObjectOutputStream(ClientSocket.getOutputStream());
				methodName = (String) in.readObject();
				params = (Object[]) in.readObject();
				paramTypes = (Class<?>[]) in.readObject();
				
				Method method = c.getMethod(methodName, paramTypes);
				try
				{
					result = method.invoke(sserver, params);
				}
				catch(InvocationTargetException e)
				{
					result = e.getCause();
				}
				try
				{
					out.writeObject(result);
					out.close();
					in.close();
					ClientSocket.close();
					
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
			
			catch(Exception e)
			{
			}
	
		 }//end of run thread
		
	}//end of class SomeClient

    /** Stops the skeleton server, if it is already running.

        <p>
        The listening thread terminates. Threads created to service connections
        may continue running until their invocations of the <code>service</code>
        method return. The server stops at some later time; the method
        <code>stopped</code> is called at that point. The server may then be
        restarted.
     */
    public synchronized void stop()
    {
        online = false;
        try
        {
        	listeningSocket.close();
        	stopped(null);
        }
        catch(Exception e)
		{
				e.printStackTrace();
		}
    }
}
