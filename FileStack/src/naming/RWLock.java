package naming;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import rmi.RMIException;
import storage.Command;
import storage.Storage;

public class RWLock
{
	int readers = 0;                                            //Number of current readings
	ArrayList<Integer> writerIndex = new ArrayList<Integer>();  //Contains an array or writer's serial numbers
	boolean initializedStore = false;                           //Indicates whether all the storage servers are up and running so we can start replicating
	Iterator<Storage> storeItr;                                 //An iterator over all the storage servers created during registration
	int consecReads = 0;                                        //Stores the consecutive number of reads without a write
	int globalIndex = 0;                                        //Stores the next issueable serial number
	boolean writing = false;                                    //Indicates if any writing is in progress

	/**
	<p>Waits if any readers are currently reading or if the current
	   writer's turn is for some other writer.
	   Resets the consecutive read's counter

	@param NamingServer ns, an instance of the naming server.
	@param Node n, the node to be shared locked.
	@return <code>void</code>
	@throws InterruptedException occurs when the waiting is interrupted with an interrupt.
 */
	public synchronized void getExclusive(NamingServer ns, Node n) throws InterruptedException
	{
		int myIndex = globalIndex;
		globalIndex++;
		writerIndex.add(myIndex);
		
		//Waits if any readers are currently reading or if the current writer's
		//turn is for some other writer.
		while( !(readers == 0 && myIndex==writerIndex.get(0)) || writing)
		{
			wait();
		}
		writing = true;
		writerIndex.remove(0);
		consecReads = 0;
		
		if(n!=null && n.isFile && !ns.replicas.isEmpty())
		{
			Set<Storage> storeSet = ns.replicas.get(n.myPath);
			Iterator<Storage> sItr = storeSet.iterator();
			
			//Stores in the next storage server if copy does not already exist
			while(sItr.hasNext())
			{
				Storage store = sItr.next();
				Command c = ns.storecommandMap.get(store);
				try
				{
					c.delete(n.myPath);
				} catch (RMIException e) {e.printStackTrace();}
			}
			ns.replicas.remove(n.myPath);
		}
	
	}

	/**
	<p>Waits until reader's turn is before the next writers turn and no writing is going on.
	   Does not wait if there are no writers waiting for their turn. Replication happens when
	   20 consecutive read requests were seen without before a write request. File is replicated
	   to the next available server using the storage server Iterator.

	@param NamingServer ns, an instance of the naming server.
	@param Node n, the node to be shared locked.
	@return <code>void</code>
	@throws InterruptedException occurs when the waiting is interrupted with an interrupt.
 */
	public synchronized void getShared(NamingServer ns, Node n) throws InterruptedException
	{
		//Run once in the begining to get an iterator over storage servers. will be used during replication
		if(!initializedStore)
		{
			storeItr = ns.Stores.iterator();
			initializedStore = true;
		}
			
		int myIndex = globalIndex;
		globalIndex++;
		
		//wait until it your turn to read or there are no writes at all. Also make sure no writing is in progress
		while( !writerIndex.isEmpty() && !(myIndex<writerIndex.get(0)) || writing)
		{
			wait();
		}
		readers++;
		
		//If leaf node was passed, think about replication
		if(n!= null && n.isFile)
		{
			consecReads++;
			
			//If 20 read requests were processed without seeing a write request
			if(consecReads > 20)
			{
				if(storeItr.hasNext())
				{
					Storage s = storeItr.next();
					if(s.equals(n.storageStub))
						s = storeItr.next();
					Command c = ns.storecommandMap.get(s);
					try 
					{
						c.copy(n.myPath, n.storageStub);
					} catch (FileNotFoundException e) {e.printStackTrace();} 
					catch (RMIException e) {e.printStackTrace();}
					catch (IOException e) {e.printStackTrace();}
					
					//Adding the information about the replica files.
					Set<Storage> newset;
					if(ns.replicas.get(n.myPath)==null)
					{
						newset = new HashSet<Storage>();
						newset.add(s);
					}
					else
					{
						newset = ns.replicas.get(n.myPath);
						newset.add(s);
					}
					
			        ns.replicas.remove(n.myPath);
			        ns.replicas.put(n.myPath, newset);
				}
				consecReads = 0;
			}//end of replication
		}
	}


	/**
	<p>Release exclusive lock by chaging global status of writing to false and notify waiting threads.

	@return <code>void</code>
 */
	public synchronized void releaseShared()
	{
		if(readers>0)
			readers--;
		notifyAll();
	}

	/**
	<p>Release exclusive lock by chaging global status of writing to false and notify waiting threads.

	@return <code>void</code>
 */
	public synchronized void releaseExclusive()
	{
		writing = false;
		notifyAll();
	}
}