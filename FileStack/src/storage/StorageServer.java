package storage;

import java.io.*;
import java.net.*;

import common.*;
import rmi.*;
import naming.*;

/** Storage server.

    <p>
    Storage servers respond to client file access requests. The files accessible
    through a storage server are those accessible under a given directory of the
    local filesystem.
 */
public class StorageServer implements Storage, Command
{
	
	File root;
	Skeleton<Storage> clientSkeleton;
	Skeleton<Command> commandSkeleton;
	
    /** Creates a storage server, given a directory on the local filesystem.

        @param root Directory on the local filesystem. The contents of this
                    directory will be accessible through the storage server.
        @throws NullPointerException If <code>root</code> is <code>null</code>.
    */
    public StorageServer(File root,int client_port, int command_port )
    {
        if(root == null)
        	throw new NullPointerException();
        
        this.root = root;
        InetSocketAddress clientAddress;
    	InetSocketAddress commandAddress;
        
        if(client_port > 0)
        {
    		clientAddress = new InetSocketAddress(client_port);
    		clientSkeleton = new Skeleton<Storage>(Storage.class, this, clientAddress);
    	}
        else
    		clientSkeleton = new Skeleton<Storage>(Storage.class, this);
        
    	// initializes the command port only if it is a valid port
    	if(command_port > 0)
    	{
        	commandAddress = new InetSocketAddress(command_port);
        	commandSkeleton = new Skeleton<Command>(
        			Command.class, this, commandAddress);
    	}
    	else
    		commandSkeleton = new Skeleton<Command>(Command.class, this);
    }
    
    public StorageServer(File root)
    {
    	this(root,0,0);
    }

    /** Starts the storage server and registers it with the given naming
        server.

        @param hostname The externally-routable hostname of the local host on
                        which the storage server is running. This is used to
                        ensure that the stub which is provided to the naming
                        server by the <code>start</code> method carries the
                        externally visible hostname or address of this storage
                        server.
        @param naming_server Remote interface for the naming server with which
                             the storage server is to register.
        @throws UnknownHostException If a stub cannot be created for the storage
                                     server because a valid address has not been
                                     assigned.
        @throws FileNotFoundException If the directory with which the server was
                                      created does not exist or is in fact a
                                      file.
        @throws RMIException If the storage server cannot be started, or if it
                             cannot be registered.
     */
    public synchronized void start(String hostname, Registration naming_server)
        throws RMIException, UnknownHostException, FileNotFoundException
    {
        if(!root.exists() || root.isFile())
        	throw new FileNotFoundException();
        
        clientSkeleton.start();
        commandSkeleton.start();
        
        Storage clientStub = (Storage) Stub.create(Storage.class, clientSkeleton, hostname);
        Command cmmdStub = (Command) Stub.create(Command.class, commandSkeleton, hostname);
        
        Path[] files = Path.list(root);
    	Path[] duplicateFiles = naming_server.register(
    			clientStub, cmmdStub, files);
    	
    	// delete all duplicate files
    	for(Path p : duplicateFiles)
    	{
    		p.toFile(root).delete();
        	// prune all empty directories
        	deleteEmpty(new File(p.toFile(root).getParent()));
    	}
        
    }
    
    
    public synchronized void deleteEmpty(File parent)
    {
    	while(!parent.equals(root))
    	{
    		// Delete if the parent does not have any children
    		if(parent.list().length == 0) {
    			parent.delete();
    		} else {
    			break;
    		}
    		parent = new File(parent.getParent());
    	}
    }
    

    /** Stops the storage server.

        <p>
        The server should not be restarted.
     */
    public void stop()
    {
        clientSkeleton.stop();
        commandSkeleton.stop();
        stopped(null);
    }

    /** Called when the storage server has shut down.

        @param cause The cause for the shutdown, if any, or <code>null</code> if
                     the server was shut down by the user's request.
     */
    protected void stopped(Throwable cause)
    {
    }

    // The following methods are documented in Storage.java.
    @Override
    public synchronized long size(Path file) throws FileNotFoundException
    {
        File f = file.toFile(root);
        if(!f.exists() || !f.isFile())
        	throw new FileNotFoundException();
        return f.length();
    }

    @Override
    public synchronized byte[] read(Path file, long offset, int length)
        throws FileNotFoundException, IOException
    {
        File f = file.toFile(root);

        //Checks for exceptions
        if(!f.exists() || !f.isFile())
        	throw new FileNotFoundException();
        
        if( offset<0 || length<0 || (offset+length>f.length()) )
        	throw new IndexOutOfBoundsException();
        
        InputStream in = new FileInputStream(f);
        byte[] out = new byte[length];
        in.read(out, (int) offset, length);
        return out;
    }

    @Override
    public synchronized void write(Path file, long offset, byte[] data)
        throws FileNotFoundException, IOException
    {
    	File f = file.toFile(root);

    	//Checks for exceptions
        if(!f.exists() || !f.isFile())
        	throw new FileNotFoundException();
        
        if( offset<0 )
        	throw new IndexOutOfBoundsException();
        
        InputStream in = new FileInputStream(f);
        long len = Math.min(offset, f.length());
        byte[] offsets = new byte[(int)len];
        in.read(offsets);
        
        FileOutputStream out = new FileOutputStream(f);
        out.write(offsets, 0 , (int)len);
        
        int diff = (int)(offset - f.length());
        
        if(diff > 0)
        	for(int i = 0; i<diff ; i++)
        		out.write(0);
        
        out.write(data);
    }

    // The following methods are documented in Command.java.
    @Override
    public synchronized boolean create(Path file)
    {
    	//Checks for exceptions
        if(file == null)
        	throw new NullPointerException();
        
        if(file.isRoot())
        	return false;
        
        File parent = file.parent().toFile(root);
        if(parent.isFile())
        	delete(file.parent());
        try
        {
        	parent.mkdirs();
        	File f = file.toFile(root);
        	return f.createNewFile();
        }
        catch(IOException e)
        {
        	return false;
        }
        
    }

    @Override
    public synchronized boolean delete(Path path)
    {
        if(path.isRoot() )
        	return false;
        
        File f = path.toFile(root);
        if(!f.exists())
        	return false;
        if(f.isFile())
        	return f.delete();
        else
        	return pruneDir(f);
    }
    
    //Auxiliary function for delete that recursive goes down a path and deletes any empty director aka pruning
    public boolean pruneDir(File f)
    {
    	if(!f.isFile())
    	{
    		File[] subDirs = f.listFiles();
    		for(File ff : subDirs)
    			if(!pruneDir(ff))
    				return false;
    	}
    	return f.delete();
    }

	@Override
	public boolean copy(Path file, Storage server) throws RMIException,
			FileNotFoundException, IOException 
	{
		File f = file.toFile(root);
        if(f.exists())
        	delete(file);
       
        //copies bytes from the given file
        create(file);
        long fileSize = server.size(file);
        long offset = 0;
        while(offset < fileSize)
        {
        	int bytesToCopy = (int)Math.min(Integer.MAX_VALUE, fileSize - offset);
        	byte[] data = server.read(file, offset, bytesToCopy);
        	write(file,offset,data);
        	offset += bytesToCopy;
        }
        return true;
	}
}
