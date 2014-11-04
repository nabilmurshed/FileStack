package naming;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import storage.Storage;
import common.Path;

public class Node {
	
	public HashMap<String, Node> children;
	public boolean isFile;
	public Path myPath;
	public Storage storageStub;
	public RWLock lock;
	ArrayList<Path> result = new ArrayList<Path>();

	
	public Node(boolean isFile, Path myPath, Storage storageStub)
	{
		
		children = new HashMap<String, Node>();
		this.isFile = isFile;
		this.myPath = myPath;
		this.storageStub = storageStub;
		lock = new RWLock();
		
	}
	
	/**
	 	
		<p>Goes through the path components and uses them
		to go through the tree and fine the object the 
		path is pointing to. Throws FileNotFoundException
		if path points to a non-existent object.

        @param path Path to the file or directory whose node is to be obtained.
        @return <code>Node</code> The Node object corresponding to the the Path;
        @throws FileNotFoundException If the Path refers to a non-existent Node.

	 */
	public Node getPathNode(Path p) throws FileNotFoundException
	{
		Node nodeIter = this;
		Iterator<String> strIter = p.iterator();
		String nextComponent;
		while(strIter.hasNext())
		{
			nextComponent = strIter.next();
			if(nodeIter.children.containsKey(nextComponent))
				nodeIter = nodeIter.children.get(nextComponent);
			else
				throw new FileNotFoundException();
		}
		return nodeIter;
	}
	
	/**
		<p>Goes recursively into all the nodes and children nodes of the given path.
		   Adds all these paths to the returning ArrayList.

    	@param Path path of the ancestral file whose all descendants are to be returned.
    	@return <code>ArrayList<Path></code> An ArrayList of the paths of all the Nodes
    				that reside inside the directory. If the Path refers to a file, Return
    				a singleton ArrayList of just that file's path.
    	@throws FileNotFoundException If the Path refers to a File or Directory Node.
	 */
	public ArrayList<Path> getFilesUnder() throws FileNotFoundException
	{
		if(isFile)
		{
			result.add(myPath);
			return result;
		}
		else
		{
			aux(this);
			return result;
		}
	}
	
	//A helper function that helps the getFilesUnder() method to recurse
	private void aux(Node n)
	{
		if(isFile)
		{
			result.add(myPath);
			return;
		}
		else
		{
			result.add(myPath);
			for(Node nn : n.children.values())
				aux(nn);
		}
	}	
}
