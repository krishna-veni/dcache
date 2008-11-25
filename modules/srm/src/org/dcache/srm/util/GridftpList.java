/*
 * GridftpList.java
 *
 * Created on April 27, 2005, 1:13 PM
 */

package org.dcache.srm.util;

/**
 *
 * @author  timur
 */
public class GridftpList {
    
     
    /** Creates a new instance of GridftpList */
    public GridftpList() {
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)  throws Exception{
        if(args==null || args.length < 1 || 
        args[0].equalsIgnoreCase("-h")    ||
        args[0].equalsIgnoreCase("-help") ||
        args[0].equalsIgnoreCase("--h")   ||
        args[0].equalsIgnoreCase("--help")) {
            System.err.println(
            "usage:\n" +
            "       gridftplist < gridftp directory url> [<server passive (true or false)> \n"+
            "  example:" +
            "       gridftplist gsiftp://host1:2811//dir1/dir-to-list ");
            
            System.exit(1);
            return;
        }
        String directory = args[0];
        boolean serverPassive = true;
        if(args.length >1 ) {
            serverPassive = args[1].equalsIgnoreCase("true");
        }
        
        org.globus.util.GlobusURL directory_url = new org.globus.util.GlobusURL(directory);

        org.dcache.srm.Logger logger =   new org.dcache.srm.Logger()
        {
            public synchronized void log(String s)
            {
                //System.out.println(new java.util.Date().toString()+": "+ s);
            }
            public synchronized void elog(String s)
            {
                System.err.println(new java.util.Date().toString()+": "+ s);
            }
            public synchronized void elog(Throwable t)
            {
                t.printStackTrace();
            }
        };

        if( ! directory_url.getProtocol().equals("gsiftp") &&
              ! directory_url.getProtocol().equals("gridftp") ) {
                  System.err.println("wrong protocol : "+
                  directory_url.getProtocol());
                 System.exit(1);
                    return;
        }
        
        GridftpClient client = new GridftpClient(directory_url.getHost(),
            directory_url.getPort(),0,null,logger);
            client.setStreamsNum(1);
            
            System.out.println( client.list(directory_url.getPath(),serverPassive));
            //for(java.util.Iterator i = paths.iterator(); i.hasNext();) {
            //    String next = (String)i.next();
            //    System.out.println(next);
            //}
            
           client.close();
    }
    
    
}
