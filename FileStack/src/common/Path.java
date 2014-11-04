package common;

import java.io.*;
import java.util.*;

/** Distributed filesystem paths.

    <p>
    Objects of type <code>Path</code> are used by all filesystem interfaces.
    Path objects are immutable.

    <p>
    The string representation of paths is a forward-slash-delimeted sequence of
    path components. The root directory is represented as a single forward
    slash.

    <p>
    The colon (<code>:</code>) and forward slash (<code>/</code>) characters are
    not permitted within path components. The forward slash is the delimeter,
    and the colon is reserved as a delimeter for application use.
 */
public class Path implements Iterable<String>, Serializable
{
	ArrayList<String> components;
	
    /** Creates a new path which represents the root directory. */
    public Path()
    {
    	components = new ArrayList<String>();
    }

    /** Creates a new path by appending the given component to an existing path.

        @param path The existing path.
        @param component The new component.
        @throws IllegalArgumentException If <code>component</code> includes the
                                         separator, a colon, or
                                         <code>component</code> is the empty
                                         string.
    */
    public Path(Path path, String component)
    {
    	//throws exception if empty or contains ':' or '/'
        if( (component.length()==0) || component.contains("/") || component.contains(":"))
        	throw new IllegalArgumentException();
        
        //Initializes, copies the old components and then adds the new component
        components = new ArrayList<String>();
        components.addAll(path.components);
        components.add(component);       
    }

    /** Creates a new path from a path string.

        <p>
        The string is a sequence of components delimited with forward slashes.
        Empty components are dropped. The string must begin with a forward
        slash.

        @param path The path string.
        @throws IllegalArgumentException If the path string does not begin with
                                         a forward slash, or if the path
                                         contains a colon character.
     */
    public Path(String path)
    {
    	//Throws exception of not begins with '/' or contains ':'
        if(path == "" || path.charAt(0)!='/' || path.contains(":") )
        	throw new IllegalArgumentException();
        
        //Initializes, splits according to delimiter '/'
        //Adds the components one by one if not ""
        components = new ArrayList<String>();
        for(String s : path.split("/"))
        {
        	if(s.length()!=0)
        		components.add(s);
        }
    }
    
    //A helper constructor that makes it easier to
    //create a path when only components are given
    private Path(ArrayList<String> s)
    {
    	this.components = s;
    }

    /** Returns an iterator over the components of the path.

        <p>
        The iterator cannot be used to modify the path object - the
        <code>remove</code> method is not supported.

        @return The iterator.
     */
    @Override
    public Iterator<String> iterator()
    {
    	//Creating a new Iterator class and overriding
    	//because components.iterator() has remove()
    	//that is not allowed to be used
    	class newIterator implements Iterator<String> {
    		Iterator<String> i;

    		public newIterator()       {i = components.iterator();}

			public boolean hasNext()   {return i.hasNext();}

			public String next()       {return i.next();}

			// Make sure the remove operation is reported as not supported.
			public void remove()       {throw new UnsupportedOperationException("Remove not implemented");}
    	}
    	return new newIterator();
    }

    /** Lists the paths of all files in a directory tree on the local
        filesystem.

        @param directory The root directory of the directory tree.
        @return An array of relative paths, one for each file in the directory
                tree.
        @throws FileNotFoundException If the root directory does not exist.
        @throws IllegalArgumentException If <code>directory</code> exists but
                                         does not refer to a directory.
     */
    public static Path[] list(File directory) throws FileNotFoundException
    {
    	//will pass the variables to a recursive helper function
    	ArrayList<Path> list = new ArrayList<Path>();
        pathBuild(directory, new Path(),list);
        
        //Dummy array to help casting
        Path[] dummyCast = new Path[0];
        return list.toArray(dummyCast); 
    }
    
    private static void pathBuild(File dirc, Path parentPath, ArrayList<Path> list) throws FileNotFoundException
    {
    	//Throws exceptions if conditions are not met
    	if(!dirc.exists())
        	throw new FileNotFoundException();
        if(!dirc.isDirectory())
        	throw new IllegalArgumentException();
        
        File[] files = dirc.listFiles();
        
        //Recursively calls or adds path to list
        for(File f : files)
        {
        	if(f.isFile())
        		list.add(new Path(parentPath,f.getName()));
        	else
        		pathBuild(f, new Path(parentPath,f.getName()),list);
        }
    }

    /** Determines whether the path represents the root directory.

        @return <code>true</code> if the path does represent the root directory,
                and <code>false</code> if it does not.
     */
    public boolean isRoot()
    {
        return components.isEmpty();
    }

    /** Returns the path to the parent of this path.

        @throws IllegalArgumentException If the path represents the root
                                         directory, and therefore has no parent.
     */
    
    
    public Path parent()
    {
    	//Exception because root has no parent
        if(this.isRoot())
        	throw new IllegalArgumentException();
        
        //Copies all components into new ArrayList and removes the tailing component
        //Using this new ArrayList, creates a new path object and returns
        ArrayList<String> parentPath = new ArrayList<String>();
        parentPath.addAll(components);
        parentPath.remove(parentPath.size()-1);
        return new Path(parentPath);
    }

    /** Returns the last component in the path.

        @throws IllegalArgumentException If the path represents the root
                                         directory, and therefore has no last
                                         component.
     */
    public String last()
    {
        if(this.isRoot())
        	throw new IllegalArgumentException();
        
        //Simply gets the last element from ArrayList
        return components.get(components.size()-1);
    }

    /** Determines if the given path is a subpath of this path.

        <p>
        The other path is a subpath of this path if is a prefix of this path.
        Note that by this definition, each path is a subpath of itself.

        @param other The path to be tested.
        @return <code>true</code> If and only if the other path is a subpath of
                this path.
     */
    public boolean isSubpath(Path other)
    {
    	Iterator<String> iThis = components.iterator();
        Iterator<String> iThat = ((Path)other).iterator();
        if( components.size() < ((Path)other).components.size() )
        	return false;
        
        //checks if all components of Other is the same
        //and is in the same order from the beginning
        while(iThat.hasNext())
        {
        	if(! iThis.next().equals(iThat.next()) )
        		return false;
        }
        return true;
    }

    /** Converts the path to <code>File</code> object.

        @param root The resulting <code>File</code> object is created relative
                    to this directory.
        @return The <code>File</code> object.
     */
    public File toFile(File root)
    {
    	//Converts to a File object using the name
        return new File(root.getPath().concat(this.toString()));
    }

    /** Compares two paths for equality.

        <p>
        Two paths are equal if they share all the same components.

        @param other The other path.
        @return <code>true</code> if and only if the two paths are equal.
     */
    @Override
    public boolean equals(Object other)
    {
        Iterator<String> iThis = components.iterator();
        Iterator<String> iThat = ((Path)other).iterator();
        if( components.size() != ((Path)other).components.size() )
        	return false;
        
        //Checks if both the ArrayLists are identical
        while(iThis.hasNext())
        {
        	if(! iThis.next().equals(iThat.next()) )
        		return false;
        }
        return true;
    }

    /** Returns the hash code of the path. */
    @Override
    //Java's implementation of a hascode function
    public int hashCode()
    {
    	final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((components == null) ? 0 : components.hashCode());
		return result;
    }

	/** Converts the path to a string.

        <p>
        The string may later be used as an argument to the
        <code>Path(String)</code> constructor.

        @return The string representation of the path.
     */
    @Override
    public String toString()
    {
    	if(this.isRoot())
    		return "/";
    	else
    	{
    		String result="";
    		
    		//For each component, concats a '/' before it
    		for(String s : components)
    		{
    			result = result.concat("/");
    			result = result.concat(s);
    		}
    		return result;
    	}
    }
}
