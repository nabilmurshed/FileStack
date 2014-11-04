package naming;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import rmi.*;
import common.*;
import storage.*;

/** Naming server.

    <p>
    Each instance of the filesystem is centered on a single naming server. The
    naming server maintains the filesystem directory tree. It does not store any
    file data - this is done by separate storage servers. The primary purpose of
    the naming server is to map each file name (path) to the storage server
    which hosts the file's contents.

    <p>
    The naming server provides two interfaces, <code>Service</code> and
    <code>Registration</code>, which are accessible through RMI. Storage servers
    use the <code>Registration</code> interface to inform the naming server of
    their existence. Clients use the <code>Service</code> interface to perform
    most filesystem operations. The documentation accompanying these interfaces
    provides details on the methods supported.

    <p>
    Stubs for accessing the naming server must typically be created by directly
    specifying the remote network address. To make this possible, the client and
    registration interfaces are available at well-known ports defined in
    <code>NamingStubs</code>.
 */
public class NamingServer implements Service, Registration
{
	
	
	Node root;
	
	//A hashmap between the Storage and Command for each file
	HashMap<Storage, Command> storecommandMap;
	Skeleton<Service> servSkeleton;
	Skeleton<Registration> regSkeleton;
	
	//replicas contains the paths of replicated files and a set
	//of their respective storage servers.
	ConcurrentHashMap<Path, Set<Storage>> replicas;
	
	//List of all storages
	ArrayList<Storage> Stores = new ArrayList<Storage>();
    /** Creates the naming server object.

        <p>
        The naming server is not started.
     */
    public NamingServer()
    {
    	root = new Node(false, new Path("/"),null);
    	storecommandMap = new HashMap<Storage, Command>();
    	servSkeleton = new Skeleton<Service>(Service.class,this,new InetSocketAddress(NamingStubs.SERVICE_PORT));
        regSkeleton = new Skeleton<Registration>(Registration.class,this,new InetSocketAddress(NamingStubs.REGISTRATION_PORT));
        replicas = new ConcurrentHashMap<Path, Set<Storage>>();
    }

    /** Starts the naming server.

        <p>
        After this method is called, it is possible to access the client and
        registration interfaces of the naming server remotely.

        @throws RMIException If either of the two skeletons, for the client or
                             registration server interfaces, could not be
                             started. The user should not attempt to start the
                             server again if an exception occurs.
     */
    public synchronized void start() throws RMIException
    {
        regSkeleton.start();
        servSkeleton.start();
    }

    /** Stops the naming server.

        <p>
        This method waits for both the client and registration interface
        skeletons to stop. It attempts to interrupt as many of the threads that
        are executing naming server code as possible. After this method is
        called, the naming server is no longer accessible remotely. The naming
        server should not be restarted.
     */
    public void stop()
    {
        regSkeleton.stop();
        servSkeleton.stop();
        stopped(null);
    }

    /** Indicates that the server has completely shut down.

        <p>
        This method should be overridden for error reporting and application
        exit purposes. The default implementation does nothing.

        @param cause The cause for the shutdown, or <code>null</code> if the
                     shutdown was by explicit user request.
     */
    protected void stopped(Throwable cause)
    {
    }

    // The following methods are documented in Service.java.
    @Override
    public boolean isDirectory(Path path) throws FileNotFoundException, NullPointerException
    {
    	if(path == null)
    		throw new NullPointerException();
		return !root.getPathNode(path).isFile;
    }

    // The following methods are documented in Service.java.
    @Override
    public String[] list(Path directory) throws FileNotFoundException
    {
        Node pathNode = root.getPathNode(directory);
        
        if(pathNode.isFile)
        	throw new FileNotFoundException();
        ArrayList<String> contents = new ArrayList<String>();
        
        for(String component : pathNode.children.keySet())
        	contents.add(component);
        String[] dummyCast = new String[0];
        return contents.toArray(dummyCast);
    }

    // The following methods are documented in Service.java.
    @Override
    public boolean createFile(Path file)
        throws RMIException, FileNotFoundException
    {
    	if(file == null)
    		throw new NullPointerException();
    	if(storecommandMap.isEmpty())
    		return false;

    	Storage nextstore = storecommandMap.keySet().iterator().next();
    	
    	if(!insertNode(nextstore, file))
    		return false;
    	Command command = storecommandMap.get(nextstore);
    	try
    	{
    		command.create(file);
    	}
    	catch(RMIException e)
    	{
    		return false;
    	}
    	
    	return true;
    }
    
    //Auxiliary function that helps createFile function add a path to the tree
    public boolean insertNode(Storage s, Path p) throws FileNotFoundException
    {
    	if(p.isRoot())
    		return false;
    	Path parentPath = p.parent();
    	Node parentNode = null;
    	
        try
        {
        	parentNode = root.getPathNode(parentPath);
        }
        catch(FileNotFoundException fnfe)
        {
        	throw fnfe;
        }
       
        if(parentNode == null || parentNode.isFile)
        	throw new FileNotFoundException();
        	
    	if(parentNode.children.containsKey(p.last()))
    		return false;
    	
    	Node newFile = new Node(true, p, s);
        parentNode.children.put(p.last(), newFile);
        
        return true;
    }

    // The following methods are documented in Service.java.
    @Override
    public boolean createDirectory(Path directory) throws FileNotFoundException
    {
        if(directory == null)
        	throw new NullPointerException();
        if(directory.isRoot())
        	return false;
        Node parentNode = null;
        parentNode = root.getPathNode(directory.parent());
        if(parentNode == null || parentNode.isFile)
        	throw new FileNotFoundException();
        //false if directory already exists
        if(parentNode.children.get(directory.last()) != null)
        	return false;
        Node newFile = new Node(false, directory, null);
    	parentNode.children.put(directory.last(), newFile);
    	return true;
        
        
    }

    // The following methods are documented in Service.java.
    @Override
    public boolean delete(Path path) throws FileNotFoundException
    {
    	if(path == null)
    		throw new NullPointerException();
    	
        boolean success = true;
        ArrayList<Path> allPathNodes = new ArrayList<Path>();
        
        if(path.isRoot())
        	return false;
        
        Node parent = root.getPathNode(path.parent());
        if(parent == null)
        	throw new FileNotFoundException();
        
        Node pn = root.getPathNode(path);
        if(pn==null)
			throw new FileNotFoundException();
        
        if(parent.children.containsKey(path.last()))
        {
        	if(isDirectory(path))           //If file to be deleted is Directory
        	{
        		Set<Storage> storagesToDeleteFrom = Collections.newSetFromMap
        				(new ConcurrentHashMap<Storage, Boolean>());
        		allPathNodes = pn.getFilesUnder();
        		
        		/*	If a directory is deleted, the files under it will also be deleted
        			This block checks in all storage servers whether and such files exists
        			of which the deleting directory may be a parent directory
        		*/
    			for(Path subpath : replicas.keySet())
        			if(subpath.isSubpath(path))
        				allPathNodes.add(subpath);
    			
    			if(!replicas.isEmpty())        //If the global list that contains replicas is not empty
    			{
    				for(Path p : allPathNodes)
            		{
            			storagesToDeleteFrom.addAll(replicas.get(p));
            		}
            		for(Storage s : storagesToDeleteFrom) {
            			Command c = storecommandMap.get(s);
            			Command cOriginal = storecommandMap.get(pn.storageStub);
            			try 
            			{
    							success = cOriginal.delete(path) && c.delete(path);
    					}
            			catch (RMIException e) 
    					{
    						return false;
    					}
                		replicas.remove(path);
            		}
            		parent.children.remove(path.last());
            		return success;
    			}
    			else                  // If no replicas exist for any file just delete original file
    			{
    				Command cOriginal = storecommandMap.get(pn.storageStub);
    				try 
        			{
						if(!cOriginal.delete(path))
							success = false;
					}
        			catch (RMIException e) 
					{
						return false;
					}
    				parent.children.remove(path.last());
    				return success;	
    			}
        		
        	}
        	else          //If the Path being deleted is a file
        	{
        		parent.children.remove(path.last());
        		if(!replicas.isEmpty())                     //If the global list that contains replicas is not empty
        		{
        			Set<Storage> backupstores = replicas.get(path);
            		if(backupstores.isEmpty())
            			return true;
            		
            		for(Storage s : backupstores)
            		{
            			Command c = storecommandMap.get(s);
            			Command cOriginal = storecommandMap.get(pn.storageStub);
            			try
            			{
            				success = c.delete(path) && cOriginal.delete(path);
            			}
            			catch(RMIException e)
            			{
            				return false;
            			}
            			replicas.remove(path);
            		}
        		}
        		else                                        // If no replicas exist for any file just delete original file
        		{
        			Command cOriginal = storecommandMap.get(pn.storageStub);
        			try
        			{
        				success = cOriginal.delete(path);
        			}
        			catch(RMIException e)
        			{
        				return false;
        			}
        		}
        	}
        }
        else
    		throw new FileNotFoundException();
		return success;
    }
    
    
    // The following methods are documented in Service.java.
    @Override
    public Storage getStorage(Path file) throws FileNotFoundException
    {
        if(file == null)
        	throw new NullPointerException();
        if(!root.getPathNode(file).isFile)
        	throw new FileNotFoundException();
        return root.getPathNode(file).storageStub;
    }

    // The method register is documented in Registration.java.
    @Override
    public Path[] register(Storage client_stub, Command command_stub,
                           Path[] files)
    {
    	
    	if(client_stub == null || command_stub == null || files == null)
    		throw new NullPointerException();
    	
    	if(storecommandMap.get(client_stub)!=null)
    		throw new IllegalStateException();
    	
    	storecommandMap.put(client_stub, command_stub);
        ArrayList<Path> duplicates = new ArrayList<Path>();
        for(Path p : files)
        {
        	if(!p.isRoot())
        	{
        		try
            	{
            		if(!addToTree(client_stub, p))
            			duplicates.add(p);
            	}
            	catch(Exception fnfe)
            	{
            		duplicates.add(p);
            	} 
        	}
        	       	
        }
        if(!Stores.contains(client_stub))
        	Stores.add(client_stub);
        Path[] dummyCast = new Path[0];
        return duplicates.toArray(dummyCast);
    }
    
    //Auxiliary function that helps register function add a path to the tree 
    public boolean addToTree(Storage s, Path p) throws FileNotFoundException
    {
    	Node current = this.root;
    	Iterator<String> itr = p.iterator();
    	String component;
    	while(itr.hasNext())
    	{
    		component = itr.next();
    		if(current.children.containsKey(component) && !current.children.get(component).isFile)
    			current = current.children.get(component);
    		else
    		{
    			while(itr.hasNext())
    			{
    				current.children.put(component, new Node(false, new Path(current.myPath, component) , s));
    				current = current.children.get(component);
    				component = itr.next();
    			}
    			
    			if(current.children.get(component) == null)
    			{
    				current.children.put(component, new Node(true, p, s));
    				return true;
    			}
    		}
    	}
    	return false;
    }

	@Override
	/**
	<p>When a file has to be locked, the locker shared locks all the files down the
	   tree until the given path's node and then lock it with an exclusive lock if
	   requested of shared locks it otherwise.

	@param Path path of the File or Directory to be locked.
	@param boolean exclusive to indicate a write lock.
	@return <code>void<Path></code>
	@throws FileNotFoundException If the Path refers to a File or Directory Node.
	@throws RMIException If an error occurs due to network issues.
 */
	public void lock(Path path, boolean exclusive) throws RMIException,
			FileNotFoundException 
	{
		if(path == null)
			throw new NullPointerException();
	
		try
		{
			root.getPathNode(path);
		}
		catch(Exception e)
		{
			throw new FileNotFoundException();
		}
		Iterator<String> itr = path.iterator();
		String nextComp;
		Node current = root;
		
		if(path.isRoot())
		{
			if(exclusive)
			{
				try 
				{
					current.lock.getExclusive(this,current);
				} catch (InterruptedException e) {e.printStackTrace();}
			}	
			else               //If not exclusive
			{
				try 
				{
					current.lock.getShared(this,current);
				} catch (InterruptedException e) {e.printStackTrace();}
			}		
		} else                //If not root
			try 
			{
				current.lock.getShared(this,null);
			} catch (InterruptedException e1) {e1.printStackTrace();}
		while(itr.hasNext())
		{
			nextComp = itr.next();
			if(current.children.containsKey(nextComp))
			{
				current = current.children.get(nextComp);
				if(current.myPath.equals(path))
				{
					if(exclusive)
						try {
							current.lock.getExclusive(this,current);
						} catch (InterruptedException e) {e.printStackTrace();}
					else
						try 
						{
							current.lock.getShared(this, current);
						} catch (InterruptedException e) {e.printStackTrace();}
				} else              //If not path's Node
					try 
					{
						current.lock.getShared(this,current);
					} catch (InterruptedException e) {e.printStackTrace();}
			}	
			else                   //if children does not contain the next component
			{
				unlock(path, exclusive);
				throw new FileNotFoundException();
			}
		}//end of while loop
		return;	
	}//end of lock function

	@Override
	/**
	<p>When a file has to be unlocked, the unlocker unlocks all the files down the
	   tree including the given path's node.

	@param Path path of the File or Directory to be unlocked.
	@param boolean exclusive to indicate an exclusive unlock or not.
	@return <code>void<Path></code>
	@throws RMIException If an error occurs due to network issues.
 */
	public void unlock(Path path, boolean exclusive) throws RMIException 
	{
		if(path == null)
			throw new NullPointerException();
		try
		{
			root.getPathNode(path);
		}
		catch(Exception e)
		{
			throw new IllegalArgumentException();
		}
		
		Iterator<String> itr = path.iterator();
		String nextComp;
		Node current = root;
		
		if(path.isRoot())
		{
			if(exclusive)
				current.lock.releaseExclusive();
			else
				current.lock.releaseShared();
		}
		else                                   //If not root
			current.lock.releaseShared();
		while(itr.hasNext())
		{
			nextComp = itr.next();
			if(current.children.containsKey(nextComp))
			{
				current = current.children.get(nextComp);
				if(current.myPath.equals(path))
				{
					if(exclusive)
						current.lock.releaseExclusive();
					else                      //if not exclusive
						current.lock.releaseShared();
				}
				else                          //if it is an intermediate node
					current.lock.releaseShared();
			}	
			else                              //if children does not contain the next component
			{
				try
				{
					lock(path, exclusive);
				}
				catch(FileNotFoundException fnfe)
				{}
			}
		}//end of while loop iterating through nodes
		return;	
	}//end of unlock function
}

