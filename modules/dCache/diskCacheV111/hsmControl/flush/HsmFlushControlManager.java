// $Id: HsmFlushControlManager.java,v 1.6 2006-07-31 16:35:50 patrick Exp $

package diskCacheV111.hsmControl.flush ;

import java.util.* ;
import java.io.* ;
import java.text.* ;
import java.lang.reflect.* ;

import dmg.util.* ;
import dmg.cells.nucleus.* ;

import diskCacheV111.vehicles.* ;
import diskCacheV111.util.* ;
import diskCacheV111.pools.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HsmFlushControlManager  extends CellAdapter {

    private static final Logger _log =
        LoggerFactory.getLogger(HsmFlushControlManager.class);

    private CellNucleus _nucleus    = null ;
    private Args        _args       = null ;
    private int         _requests   = 0 ;
    private int         _failed     = 0 ;
    private File        _database   = null ;
    private String      _status     = "init" ;
    private boolean     _logEnabled = true ;
    private PoolCollector   _poolCollector           = null ;
    private Set             _poolGroupList           = new HashSet() ;
    private long            _getPoolCollectionTicker = 2L * 60L * 1000L ;
    private long            _timerInterval           =      30L * 1000L ;
    private FlushController _flushController         = new FlushController() ;
    private EventDispatcher _eventDispatcher         = new EventDispatcher() ;
    private SimpleDateFormat formatter   = new SimpleDateFormat ("MM.dd hh:mm:ss");
    private QueueWatch      _queueWatch  = null ;

    private Map    _properties        = new HashMap() ;
    private Object _propertyLock      = new Object() ;
    private long   _propertiesUpdated = 0L ;

    /**
      *   Usage : ... [options] <pgroup0> [<pgroup1>[...]]
      *   Options :
      *              -scheduler=<driver>
      *              -poolCollectionUpdate=<timeInMinutes>
      *              -gainControlUpdate=<timeInMinutes>
      *              -timer=<timeInSeconds>
      *
      *                 Options are forwarded to the driver as well.
      */
    public HsmFlushControlManager( String name , String  args ) throws Exception {
       super( name , args , false ) ;
       _nucleus = getNucleus() ;
       _args    = getArgs() ;
       try{
          if( _args.argc() < 1 )
            throw new
            IllegalArgumentException("Usage : ... <pgroup0> [<pgroup1>[...]]") ;

          for( int i = 0 , n = _args.argc() ; i <  n ; i++ ){
              _poolGroupList.add(_args.argv(0));
              _args.shift();
           }
           String tmp = _args.getOpt("scheduler") ;
           if( ( tmp != null ) && ( ! tmp.equals("") ) ){
               _eventDispatcher.loadHandler( tmp , false  , _args ) ;
           }

           _queueWatch = new QueueWatch() ;

           tmp = _args.getOpt("poolCollectionUpdate") ;
           if( ( tmp != null ) && ( ! tmp.equals("") ) ){
               try{
                  _getPoolCollectionTicker = Long.parseLong(tmp) * 60L * 1000L ;
               }catch(Exception ee ){
                  _log.warn("Illegal value for poolCollectionUpdate : "+tmp+" (chosing "+_getPoolCollectionTicker+" millis)");
               }
           }
           tmp = _args.getOpt("gainControlUpdate") ;
           if( ( tmp != null ) && ( ! tmp.equals("") ) ){
              long gainControlTicker = 0L ;
               try{
                  _queueWatch.setGainControlTicker( gainControlTicker = Long.parseLong(tmp) * 60L * 1000L ) ;
               }catch(Exception ee ){
                  _log.warn("Illegal value for gainControlUpdate : "+tmp+" (chosing "+gainControlTicker+" millis)");
               }
           }
           tmp = _args.getOpt("timer") ;
           if( ( tmp != null ) && ( ! tmp.equals("") ) ){
               try{
                  _timerInterval = Long.parseLong(tmp) * 1000L ;
               }catch(Exception ee ){
                  _log.warn("Illegal value for timer : "+tmp+" (chosing "+_timerInterval+" millis)");
               }
           }

       }catch(Exception e){
          start() ;
          kill() ;
          throw e ;
       }
       useInterpreter( true );
       _nucleus.newThread( _queueWatch , "queueWatch").start() ;

       start();

       _poolCollector = new PoolCollector() ;

    }

    private class QueueWatch implements Runnable {
       private long _gainControlTicker   = 1L * 60L * 1000L ;
       private long next_getPoolCollection ;
       private long next_sendGainControl   ;
       private long next_timerEvent ;

       private synchronized void setGainControlTicker( long gainControl ){
          _gainControlTicker = gainControl ;
          triggerGainControl() ;
       }
       public void run(){
          _log.info("QueueWatch started" ) ;

          try{
             _log.info("QueueWatch : waiting awhile before starting");
             Thread.currentThread().sleep(10000);
          }catch(InterruptedException ie ){
             _log.warn("QueueWatch: interrupted during initial wait. Stopping");
             return ;
          }
          _status = "Running Collector" ;
          _poolCollector.runCollector( _poolGroupList ) ;

          long now = System.currentTimeMillis() ;

          next_getPoolCollection = now + 0 ;
          next_sendGainControl   = now + _gainControlTicker ;
          next_timerEvent        = now + _timerInterval ;

          while( ! Thread.currentThread().interrupted() ){
             synchronized( this ){
                _status = "Sleeping" ;
                try{
                   wait(10000);
                }catch(InterruptedException ie ){
                   break ;
                }

                now = System.currentTimeMillis() ;

                if( now > next_getPoolCollection ){
                   _status = "Running Collector" ;
                   _poolCollector.runCollector( _poolGroupList ) ;
                   next_getPoolCollection = now + _getPoolCollectionTicker ;
                }
                if( now > next_sendGainControl ){
                   _status = "Sending Flush Controls" ;
                   _flushController.sendFlushControlMessages(  2L * _gainControlTicker ) ;
                   next_sendGainControl = now + _gainControlTicker ;
                }
                if( now > next_timerEvent ){
                   _eventDispatcher.timer();
                   next_timerEvent = now + _timerInterval ;
                }
             }

             _nucleus.updateWaitQueue() ;

          }
          _log.info( "QueueWatch stopped" ) ;
       }
       public synchronized void triggerGainControl(){
          next_sendGainControl = System.currentTimeMillis() - 10 ;
          notifyAll();
       }
       public synchronized void triggerGetPoolCollection(){
          next_getPoolCollection = System.currentTimeMillis() - 10 ;
          notifyAll();
       }
    }
    private class FlushController {
       //
       // sends 'gain control' messages to all clients we want
       // to control.
       //
       private List    _poolList = null ;
       private boolean _control  = true ;

       private synchronized void updatePoolList( Collection c ){
           _poolList = new ArrayList(c);
       }
       private synchronized void setControl( boolean control ){
          _control = control ;
       }
       private void sendFlushControlMessages( long gainControlInterval ){
          List poolList = null ;
          synchronized( this ){ poolList = _poolList ; }
          if( poolList == null )return ;
          for( Iterator it = poolList.iterator() ; it.hasNext() ; ){
              String poolName = (String)it.next() ;
              PoolFlushGainControlMessage msg =
                  new PoolFlushGainControlMessage(
                        poolName ,
                        _control ? gainControlInterval : 0L ) ;

              try{
                 if(_logEnabled)_log.info("sendFlushControlMessage : sending PoolFlushGainControlMessage to "+poolName);
                 sendMessage( new CellMessage( new CellPath(poolName) , msg ) ) ;
              }catch(NoRouteToCellException ee ){
                 _log.warn("sendFlushControlMessage : couldn't send _poolGroupHash to "+poolName);
              }
          }
       }
    }
    public class HFCFlushInfo implements HsmFlushControlCore.FlushInfo {
       private StorageClassFlushInfo  _flushInfo = null , _previousFlushInfo = null ;
       private boolean _flushingRequested = false ;
       private boolean _flushingPending   = false ;
       private int     _flushingError     = 0 ;
       private Object  _flushingErrorObj  = null ;
       private long    _flushingId        = 0L ;
       private HFCPool _pool              = null ;
       private String  _name              = null ;

       public HFCFlushInfo( HFCPool pool , StorageClassFlushInfo flush ){
          _flushInfo = flush ;
          _pool      = pool ;
          _name      = _flushInfo.getStorageClass()+"@"+_flushInfo.getHsm() ;
       }
       public synchronized String getName(){ return _name ; }
       public HsmFlushControlCore.FlushInfoDetails getDetails(){
           HsmFlushControllerFlushInfoDetails details = new HsmFlushControllerFlushInfoDetails() ;
           details._name = _name ;
           details._isFlushing = isFlushing() ;
           details._flushInfo  = _flushInfo ;
           return details ;
       }
       private synchronized void updateFlushInfo( StorageClassFlushInfo flush ){
           _previousFlushInfo = _flushInfo ;
           _flushInfo = flush ;
       }
       public synchronized boolean isFlushing(){ return _flushingRequested || _flushingPending ; }
       public synchronized void flush( int count ) throws Exception {

           flushStorageClass( _pool._poolName , _name , count ) ;
           _flushingRequested = true ;
           _flushingPending   = false ;
           _flushingError     = 0 ;
       }
       public synchronized void setFlushingAck( long id  ){
           _flushingPending   = true ;
           _flushingId        = id ;
       }
       public synchronized void setFlushingFailed( int errorCode , Object errorObject  ){
           _flushingPending   = false ;
           _flushingRequested = false ;
           _flushingId        = 0L ;
           _flushingError     = errorCode ;
           _flushingErrorObj  = errorObject ;
       }
       public synchronized void setFlushingDone(){
           setFlushingFailed(0,null);
       }

       public synchronized StorageClassFlushInfo getStorageClassFlushInfo(){ return _flushInfo ; }
    }
    private class HFCPool implements HsmFlushControlCore.Pool  {

        private String       _poolName     = null ;
        private HashMap      flushInfos    = new HashMap() ;
        private PoolCellInfo cellInfo      = null ;
        private int          mode          = 0 ;
        private boolean      isActive      = false ;
        private long         lastUpdated   = 0L ;
        private int          answerCount   = 0 ;
        private HsmFlushControlCore.DriverHandle _driverHandle = null ;

        private HFCPool( String poolName ){ _poolName = poolName ; }
        public void setDriverHandle( HsmFlushControlCore.DriverHandle handle ){
            _driverHandle = handle ;
        }
        public String getName(){
           return _poolName ;
        }
        public HsmFlushControlCore.PoolDetails getDetails(){
            HsmFlushControllerPoolDetails details = new HsmFlushControllerPoolDetails() ;
            details._name       = _poolName ;
            details._isActive   = isActive ;
            details._isReadOnly = isReadOnly() ;
            details._cellInfo   = cellInfo ;
            details._flushInfos = new ArrayList() ;
            for( Iterator i = flushInfos.values().iterator() ; i.hasNext() ; ){
               HFCFlushInfo info = (HFCFlushInfo)i.next() ;
               details._flushInfos.add( info.getDetails() ) ;
            }
            return details ;
        }
        public HsmFlushControlCore.DriverHandle getDriverHandle(){
           return _driverHandle ;
        }
        public boolean isActive(){ return isActive ; }
        public void setReadOnly( boolean rdOnly ){
           setPoolReadOnly( _poolName , rdOnly ) ;
        }
        public void queryMode(){
           queryPoolMode(_poolName ) ;
        }
        public boolean isReadOnly(){
            return ( mode & PoolManagerPoolModeMessage.WRITE ) == 0  ;
        }
        public boolean isPoolIoModeKnown(){
            return mode != 0 ;
        }
        public Set getStorageClassNames(){
           return new TreeSet( flushInfos.keySet() ) ;
        }
        public List getFlushInfos(){
           return new ArrayList( flushInfos.values() ) ;
        }
        public PoolCellInfo getCellInfo(){ return cellInfo ; }

        public HsmFlushControlCore.FlushInfo getFlushInfoByStorageClass( String storageClass ){
           return (HsmFlushControlCore.FlushInfo)flushInfos.get( storageClass ) ;
        }
        public String getPoolModeString(){
           StringBuffer sb = new StringBuffer() ;
           if( mode == 0 )return "UU" ;
           String res = "" ;
           res =  ( mode & PoolManagerPoolModeMessage.READ  ) == 0  ? "-" : "R" ;
           res += ( mode & PoolManagerPoolModeMessage.WRITE ) == 0  ? "-" : "W" ;
           return res ;
        }
        public String toString(){
           StringBuffer sb = new StringBuffer() ;
           sb.append(_poolName).append(";IOMode=").append(getPoolModeString()) ;
           sb.append(";A=").append(isActive).
              append(";LU=").
              append( lastUpdated == 0 ?
                      "Never" :
                      ( "" + (  (System.currentTimeMillis()-lastUpdated ) / 1000L ) )  ) ;
           return sb.toString();
        }
    }
    private void flushStorageClass( String poolName , String storageClass , int count ) throws Exception {

           PoolFlushDoFlushMessage msg =
               new PoolFlushDoFlushMessage( poolName , storageClass ) ;
           msg.setMaxFlushCount( count ) ;

           sendMessage( new CellMessage( new CellPath(poolName) , msg ) ) ;


    }
    private void setPoolReadOnly( String poolName , boolean rdOnly ){
       try{
           PoolManagerPoolModeMessage msg =
              new PoolManagerPoolModeMessage(
                     poolName,
                     PoolManagerPoolModeMessage.READ |
                     ( rdOnly ? 0 : PoolManagerPoolModeMessage.WRITE ) ) ;


           sendMessage( new CellMessage( new CellPath("PoolManager") , msg ) ) ;

       }catch(Exception ee ){
           _log.warn("setPoolReadOnly : couldn't sent message to PoolManager"+ee);
       }
    }
    private void queryPoolMode( String poolName ){
       try{
           PoolManagerPoolModeMessage msg =
              new PoolManagerPoolModeMessage(
                     poolName,
                     PoolManagerPoolModeMessage.UNDEFINED  ) ;


           sendMessage( new CellMessage( new CellPath("PoolManager") , msg ) ) ;

       }catch(Exception ee ){
           _log.warn("queryPoolMode : couldn't sent message to PoolManager"+ee);
       }
    }
    private class PoolCollector implements CellMessageAnswerable {

       private boolean _active        = false ;
       private int     _waitingFor    = 0 ;
       private HashMap _poolGroupHash      = new HashMap() ;
       private HashMap _configuredPoolList = new HashMap() ;
       private boolean _poolSetupReady     = false ;

       private PoolCollector(){

       }
       private void queryAllPoolModes(){
          List list ;
          synchronized(this){
             list = new ArrayList( _configuredPoolList.values() ) ;
          }
          for( Iterator i = list.iterator() ; i.hasNext() ; ){
              HFCPool pool =(HFCPool)i.next() ;
              pool.queryMode();
          }
       }
       private synchronized boolean isPoolSetupReady(){ return _poolSetupReady ; }
       private boolean isPoolConfigDone(){
           long now = System.currentTimeMillis() ;
           int pools = 0 ;
           int c     = 0 ;
           for( Iterator i = _configuredPoolList.values().iterator() ; i.hasNext() ; pools++ ){
               HFCPool pool = (HFCPool)i.next() ;
               if( pool.answerCount > 1 )return true ;
               if( pool.answerCount > 0 )c ++ ;
           }
           return c == pools  ;
       }
       private synchronized Map getPoolGroupHash(){
          return new HashMap( _poolGroupHash ) ;
       }
       private synchronized Set getConfiguredPoolNames(){
           return new HashSet( _configuredPoolList.keySet() ) ;
       }
       private synchronized List getConfiguredPools(){
           return new ArrayList( _configuredPoolList.values() ) ;
       }
       private synchronized void runCollector( Collection list ){
           if( _active ){
              _log.warn("runCollector : Still running") ;
              return ;
           }
           _active = true ;
           CellPath path = new CellPath( "PoolManager" ) ;
           for( Iterator i = list.iterator() ; i.hasNext() ; ){
               String command = "psux ls pgroup "+i.next() ;
               if(_logEnabled)_log.info("runCollector sending : "+command+" to "+path);
               try{

                   sendMessage( new CellMessage( path , command ) ,
                                true , true ,  this, 30000L ) ;
                   _waitingFor ++ ;
               }catch(Exception ee ){
                   _log.warn( "Coudn't send <"+command+"> to "+path+" "+ee ) ;
               }
          }
       }
       private synchronized void answer(){
          _waitingFor-- ;
          if( _waitingFor == 0 ){
             if(_logEnabled)_log.info("PoolCollector : we are done : "+_poolGroupHash);
             _active = false ;
             HashMap map = new HashMap() ;
             if(_logEnabled)_log.info("Creating ping set");
             for( Iterator it = _poolGroupHash.values().iterator() ; it.hasNext() ; ){
                 Object [] c = (Object [])it.next() ;
                 for( int i = 0 ; i < c.length ; i++ ){
                     String pool = (String)c[i] ;
                     if(_logEnabled)_log.info("Adding pool : "+pool ) ;
                     HFCPool p = (HFCPool)_configuredPoolList.get( pool ) ;
                     map.put( pool , p == null ? new HFCPool(pool) : p ) ;
                 }
             }
             //
             // get the diff
             //
             for( Iterator it = map.keySet().iterator() ; it.hasNext() ; ){
                 String poolName = it.next().toString() ;
                 if( _configuredPoolList.get( poolName ) == null )
                      _eventDispatcher.configuredPoolAdded( poolName ) ;
             }
              for( Iterator it = _configuredPoolList.keySet().iterator() ; it.hasNext() ; ){
                 String poolName = it.next().toString() ;
                 if( map.get( poolName ) == null )
                      _eventDispatcher.configuredPoolRemoved( poolName ) ;
             }

             _flushController.updatePoolList( map.keySet() ) ;
             _configuredPoolList = map ;

             _poolSetupReady = true ;

             _eventDispatcher.poolSetupReady() ;

//             _queueWatch.triggerGainControl() ;
          }
       }
       public synchronized HFCPool getPoolByName( String poolName ){
          return (HFCPool)_configuredPoolList.get(poolName) ;
       }
       public void answerArrived( CellMessage request ,
                                  CellMessage answer    ){

          if(_logEnabled)_log.info("answer Arrived : "+answer ) ;
          Object reply =  answer.getMessageObject() ;
          if( ( reply instanceof Object []    ) &&
              ( ((Object [])reply).length >= 3             ) &&
              ( ((Object [])reply)[0] instanceof String    ) &&
              ( ((Object [])reply)[1] instanceof Object [] )     ){

             Object [] r = (Object [])reply ;
             String poolGroupName = (String)r[0] ;
             _poolGroupHash.put( poolGroupName , r[1] ) ;
             if(_logEnabled)_log.info("PoolCollector : "+((Object [])r[1]).length+" pools arrived for "+poolGroupName);

          }else{
             _log.warn("PoolCollector : invalid reply arrived");
          }
          answer() ;
       }
       public void exceptionArrived( CellMessage request ,
                                     Exception   exception ){
          _log.warn("PoolCollector : exceptionArrived : "+exception);
          answer() ;
       }
       public void answerTimedOut( CellMessage request ){
          _log.warn("PoolCollector : answerTimedOut ");
          answer() ;
       }

    }
    public String hh_pgroup_add = "<pGroup0> [<pgroup1> [...]]" ;
    public String ac_pgroup_add_$_1_99( Args args ){
        for( int i = 0 ; i < args.argc() ; i++ ){
            _poolGroupList.add(args.argv(i)) ;
        }
        return "" ;
    }
    public String hh_pgroup_remove = "<pGroup0> [<pgroup1> [...]]" ;
    public String ac_pgroup_remove_$_1_99( Args args ){
        for( int i = 0 ; i < args.argc() ; i++ ){
            _poolGroupList.remove(args.argv(i)) ;
        }
        return "" ;
    }
    public String hh_pgroup_ls = "" ;
    public String ac_pgroup_ls( Args args ){
       StringBuffer sb = new StringBuffer() ;
       for( Iterator it = _poolGroupList.iterator() ; it.hasNext() ; ){
           sb.append(it.next().toString()).append("\n");
       }
       return sb.toString() ;
    }
    public String hh_set_control = "on|off [-interval=<seconds>]" ;
    public String ac_set_control_$_1( Args args ){
       String mode     = args.argv(0) ;
       String iString  = args.getOpt("interval") ;
       long   interval = 0L ;
       if( iString != null ){
          interval = Long.parseLong( iString ) * 1000L ;
          if( interval < 30000L )
             throw new
             IllegalArgumentException("interval must be greater than 30");
       }

       if( mode.equals("on") ){
          _flushController.setControl(true) ;
       }else if( mode.equals("off") ){
          _flushController.setControl(false);
       }else{
          throw new
          IllegalArgumentException ("set control on|off") ;
       }
       if( interval > 0L ){
          _queueWatch.setGainControlTicker( interval ) ;
       }
       return "" ;
    }
    public String hh_set_pool = "<poolName> rdonly|rw" ;
    public String ac_set_pool_$_2( Args args ) throws Exception {
        String poolName = args.argv(0) ;
        String mode     = args.argv(1) ;

        HFCPool pool = _poolCollector.getPoolByName( poolName ) ;
        if( pool == null )
           throw new
           NoSuchElementException("Pool not found : "+poolName ) ;


        if( mode.equals("rdonly") ){
            pool.setReadOnly(true);
        }else if(mode.equals("rw") ){
            pool.setReadOnly(false);
        }else{
            throw new
            IllegalArgumentException("Illegal mode : rdonly|rw");
        }

        return "Pool "+poolName+" set to "+mode ;

    }
    public String hh_query_pool_mode = "<poolName>" ;
    public String ac_query_pool_mode_$_1( Args args ) throws Exception {
        String poolName = args.argv(0) ;

        HFCPool pool = _poolCollector.getPoolByName( poolName ) ;
        if( pool == null )
           throw new
           NoSuchElementException("Pool not found : "+poolName ) ;


        pool.queryMode();

        return "Pool mode query sent to Pool "+poolName ;

    }
    public String hh_flush_pool = "<poolName> <storageClass> [-count=<count>]" ;
    public String ac_flush_pool_$_2( Args args ) throws Exception {
        String poolName = args.argv(0) ;
        String storageClass = args.argv(1) ;
        String countString  = args.getOpt("count");

        int count = ( countString == null ) || countString.equals("") ? 0 : Integer.parseInt(countString) ;

        HFCPool pool = _poolCollector.getPoolByName( poolName ) ;
        if( pool == null )
           throw new
           NoSuchElementException("Pool not found : "+poolName ) ;

        HsmFlushControlCore.FlushInfo info = pool.getFlushInfoByStorageClass( storageClass ) ;
        if( info == null )
           throw new
           NoSuchElementException("StorageClass not found : "+storageClass ) ;

        info.flush( count ) ;

        return "Flush initiated for ("+poolName+","+storageClass+")" ;

    }
    public String hh_ls_pool = "[<poolName>] -l " ;
    public Object ac_ls_pool_$_0_1( Args args ){
        String poolName = args.argc() == 0 ? null : args.argv(0) ;
        boolean detail  = args.hasOption("l") ;
        boolean binary  = args.hasOption("binary") ;

        StringBuffer sb = new StringBuffer() ;
        if( poolName == null ){
            if( binary ){
               ArrayList list = new ArrayList() ;
               for( Iterator i = _eventDispatcher.getConfiguredPools().iterator() ; i.hasNext() ; ){
                   list.add(  ((HFCPool)i.next()).getDetails() ) ;
               }
               return list ;
            }else{
               Set set = new TreeSet( _poolCollector.getConfiguredPoolNames() ) ;
               ArrayList list = new ArrayList() ;
               for( Iterator it = set.iterator() ; it.hasNext() ; ){
                   String name = it.next().toString() ;
                   if( ! detail ){
                      sb.append(name).append("\n");
                   }else{
                      HFCPool pool = _poolCollector.getPoolByName( name ) ;
                      printPoolDetails2( pool , sb ) ;
                   }
               }
            }
        }else{
            HFCPool pool = _poolCollector.getPoolByName( poolName ) ;
            if( pool == null )
              throw new
              NoSuchElementException("Pool not found : "+poolName);

            if( binary ){
                return pool.getDetails() ;
                /*
                HsmFlushControlCore.PoolDetails details = pool.getDetails() ;
                sb.append("PoolDetails\n").
                   append("   Name ").append(details.getName()).append("\n").
                   append("   CellInfo ").append(details.getCellInfo()).append("\n").
                   append("   isActive ").append(details.isActive()).append("\n") ;
                for( Iterator i = details.getFlushInfos().iterator() ; i.hasNext() ; ){
                   HsmFlushControlCore.FlushInfoDetails flush = (HsmFlushControlCore.FlushInfoDetails)i.next() ;
                   sb.append("   StorageClass ").append(flush.getName()).append("\n").
                      append("     isFlushing   ").append(flush.isFlushing()).append("\n").
                      append("     FlushInfo    ").append(flush.getStorageClassFlushInfo()).append("\n");
                }
                */
            }else{
               printPoolDetails2( pool , sb ) ;
            }
        }
        return sb.toString() ;
    }
    private void printPoolDetails2( HFCPool pool , StringBuffer sb ){

       sb.append( pool._poolName ) ;


       sb.append("\n").append(pool.toString()).append(" ");

       if( pool.cellInfo == null ) return ;

       PoolCellInfo cellInfo = pool.cellInfo ;
       PoolCostInfo costInfo = cellInfo.getPoolCostInfo() ;
       PoolCostInfo.PoolSpaceInfo spaceInfo = costInfo.getSpaceInfo() ;
       PoolCostInfo.PoolQueueInfo queueInfo = costInfo.getStoreQueue() ;

       long total    = spaceInfo.getTotalSpace() ;
       long precious = spaceInfo.getPreciousSpace() ;


       sb.append("   ").append("Mode=").append(pool.getPoolModeString()).
                        append(";Total=").append(total).
                        append(";Precious=").append(precious).
                        append(";Frac=").append( (float)precious/(float)total ).
                        append(";Queue={").append(queueInfo.toString()).append("}\n");

       for( Iterator it = pool.flushInfos.values().iterator() ; it.hasNext() ; ){

            HFCFlushInfo info = (HFCFlushInfo)it.next() ;
            StorageClassFlushInfo flush = info.getStorageClassFlushInfo();

            String storeName = flush.getStorageClass()+"@"+flush.getHsm() ;

            long size   = flush.getTotalPendingFileSize() ;
            int  count  = flush.getRequestCount() ;
            int  active = flush.getActiveCount() ;

            sb.append("   ").append(storeName).append("  ").
               append(info._flushingRequested?"R":"-").
               append(info._flushingPending?"P":"-").
               append(info._flushingError!=0?"E":"-").
               append("(").append(info._flushingId).append(")").
               append(";Size=").append(size).
               append(";Count=").append(count).
               append(";Active=").append(active).append("\n");

       }

    }
    private void printFlushInfo( StorageClassFlushInfo flush , StringBuffer sb ){
        sb.append("count=").append(flush.getRequestCount()).
           append(";bytes=").append(flush.getTotalPendingFileSize());
    }
    /*
    public String hh_infos_ls = "" ;
    public String ac_infos_ls( Args args ){
        HashMap map = new HashMap( _infoHash ) ;
        StringBuffer sb = new StringBuffer() ;
        for( Iterator it = map.entrySet().iterator() ; it.hasNext() ; ){
            Map.Entry entry = (Map.Entry)it.next() ;
            String poolName = entry.getKey().toString() ;
            PoolFlushGainControlMessage msg = (PoolFlushGainControlMessage)entry.getValue() ;
            long holdTimer = msg.getHoldTimer() ;
            PoolCellInfo poolInfo = msg.getCellInfo() ;
            StorageClassFlushInfo [] flush = msg.getFlushInfos() ;
            sb.append( poolName ).append("\n");
            sb.append( "    ").append("Hold Timer : ").append(holdTimer).append("\n");
            sb.append( "    ").append("CellInfo   : ").append(poolInfo.toString()).append("\n");
            if( flush != null ){
               for( int j = 0 ; j < flush.length ; j++ ){
                   sb.append("    Flush(").append(j).append(") ").append(flush[j]).append("\n");
               }
            }
        }
        return sb.toString();
    }
    */
    public String toString(){
       return "Req="+_requests+";Err="+_failed+";" ;
    }
    public void getInfo( PrintWriter pw ){
       pw.println("HsmFlushControlManager : [$Id: HsmFlushControlManager.java,v 1.6 2006-07-31 16:35:50 patrick Exp $]" ) ;
       pw.println("Status     : "+_status);
       pw.print("PoolGroups : ");
       for( Iterator it = _poolCollector.getPoolGroupHash().entrySet().iterator() ; it.hasNext() ; ){
          Map.Entry entry = (Map.Entry)it.next() ;
          String groupName = (String)entry.getKey() ;
          Object [] pools  = (Object [])entry.getValue() ;
          pw.println(groupName+"("+pools.length+"),");
       }
       pw.println("");
       pw.println("Driver     : "+(_eventDispatcher._schedulerName==null?"NONE":_eventDispatcher._schedulerName));
       pw.println("Control    : "+( _flushController._control ? "on" : "off" ) ) ;
       pw.println("Update     : "+( _queueWatch._gainControlTicker/1000L )+ " seconds");
       long propertiesUpdated = _propertiesUpdated ;
       if( propertiesUpdated == 0L ){
          pw.println("Update     : "+( _queueWatch._gainControlTicker/1000L )+ " seconds");
          pw.println("Properties : Not queried yet");
       }else{
          Map properties = null ;
          synchronized( _propertyLock ){
              properties = _properties ;
          }
          if( ( properties != null ) && ( properties.size() > 0 ) ){
             pw.println("Properties : (age "+( ( System.currentTimeMillis() - propertiesUpdated ) / 1000L )+" seconds)" );
             for( Iterator i = properties.entrySet().iterator() ; i.hasNext() ; ){
                 Map.Entry entry = (Map.Entry)i.next() ;
                 pw.println("  "+entry.getKey()+"="+entry.getValue() ) ;
             }
          }else{
             pw.println("Properties : None");
          }
       }
    }
    public CellInfo getCellInfo(){

        FlushControlCellInfo info = new FlushControlCellInfo(  super.getCellInfo() ) ;

        info.setParameter( _eventDispatcher._schedulerName==null?"NONE":_eventDispatcher._schedulerName ,
                           _queueWatch._gainControlTicker ,
                           _flushController._control ,
                           new ArrayList( _poolCollector.getPoolGroupHash().keySet() ) ,
                           _status ) ;

        synchronized( _propertyLock ){
            info.setDriverProperties( System.currentTimeMillis() - _propertiesUpdated , _properties ) ;
        }
        //_log.info("DRIVER PROPERTIES : "+info ) ;
        return info ;
    }
    private class EventDispatcher implements HsmFlushControlCore, Runnable  {
       private class Event {

           private static final int INIT                    = 0 ;
           private static final int UNLOAD                  = 1 ;
           private static final int FLUSHING_DONE           = 2 ;
           private static final int POOL_FLUSH_INFO_UPDATED = 3 ;
           private static final int CALL_DRIVER             = 4 ;
           private static final int POOL_SETUP_UPDATED      = 5 ;
           private static final int CONFIGURED_POOL_ADDED   = 6 ;
           private static final int CONFIGURED_POOL_REMOVED = 7 ;
           private static final int POOL_IO_MODE_UPDATED    = 8 ;
           private static final int TIMER                   = 9 ;
           private static final int PROPERTIES_UPDATED      = 10 ;
           private static final int RESET                   = 11 ;

           int type = 0 ;
           Object [] args = null ;
           long timestamp = System.currentTimeMillis() ;

           private Event( int type ){ this.type = type ; }
           private Event( int type , Object obj ){
              this.type = type ;
              args = new Object[1] ;
              args[0] = obj ;
           }
           public String toString(){
             StringBuffer sb = new StringBuffer() ;
             sb.append("Event type : ").append(type);
             if( args != null ){
               for( int i = 0 ; i < args.length;i++)
                  sb.append(";arg(").append(i).append(")=").append(args[i].toString());
             }
             return sb.toString();
           }
       }
       private class Pipe {
           private ArrayList _list = new ArrayList() ;

           public synchronized void push( Event obj ){
              _list.add( obj ) ;
              notifyAll() ;
           }
           public synchronized Event pop() throws InterruptedException {
              while( _list.isEmpty() )wait() ;
              return  (Event)_list.remove(0);
           }
       }
       private Class [] _argumentClasses = {
            dmg.cells.nucleus.CellAdapter.class ,
            diskCacheV111.hsmControl.flush.HsmFlushControlCore.class
       } ;
       private Object [] _argumentObjects = {
            diskCacheV111.hsmControl.flush.HsmFlushControlManager.this ,
            this
       } ;
       private HsmFlushSchedulable _scheduler = null ;
       private String  _schedulerName = null ;
       private String  _printoutName  = null ;
       private Pipe    _pipe          = null ;
       private Thread  _worker        = null ;
       private boolean _initDone      = false ;
       private Args    _driverArgs    = new Args("") ;

       public void run(){
          runIt( _pipe ) ;
       }
       public void runIt( Pipe pipe ) {

           HsmFlushSchedulable s = null ;
           Event   event    = null ;
           boolean done     = false ;
           boolean initDone = false ;
           _log.info(_printoutName+": Worker event loop started for "+_schedulerName );

           while( ( ! Thread.interrupted() ) && ( ! done ) ){

               try{ event = pipe.pop() ; }catch(InterruptedException e ){ break ; }
               synchronized( this ){ s = _scheduler ; if( s == null )break ; }

               try{
                   if( ( ! initDone ) &&
                         _poolCollector.isPoolSetupReady() &&
                         _poolCollector.isPoolConfigDone()                        ){
                       s.init() ;
                       initDone = true ;
                       _poolCollector.queryAllPoolModes() ;
                   }

                   if( ! initDone )continue ;

                   switch( event.type ){

                      case Event.FLUSHING_DONE :
                         HFCFlushInfo info = (HFCFlushInfo)event.args[0] ;
                         s.flushingDone( info._pool._poolName ,
                                         info._flushInfo.getStorageClass()+"@"+
                                         info._flushInfo.getHsm() ,
                                         info ) ;
                      break ;

                      case Event.POOL_FLUSH_INFO_UPDATED :
                      {
                         HFCPool pool  = (HFCPool)event.args[0] ;
                         s.poolFlushInfoUpdated( pool._poolName , (HsmFlushControlCore.Pool)pool ) ;
                      }
                      break ;
                      case Event.POOL_IO_MODE_UPDATED :
                      {
                         HFCPool pool  = (HFCPool)event.args[0] ;
                         s.poolIoModeUpdated( pool._poolName , (HsmFlushControlCore.Pool)pool ) ;
                      }
                      break ;

                      case Event.CONFIGURED_POOL_ADDED :
                      {
                         String poolName  = (String)event.args[0] ;
                         s.configuredPoolAdded( poolName ) ;
                      }
                      break ;

                      case Event.CONFIGURED_POOL_REMOVED :
                      {
                         String poolName  = (String)event.args[0] ;
                         s.configuredPoolRemoved( poolName ) ;
                      }
                      break ;

                      case Event.PROPERTIES_UPDATED :
                      {
                         Map properties  = (Map)event.args[0] ;
                         s.propertiesUpdated( properties ) ;
                         synchronized( _propertyLock ){
                            _propertiesUpdated = System.currentTimeMillis() ;
                            _properties        = properties ;
                         }
                      }
                      break ;

                      case Event.POOL_SETUP_UPDATED :
                         s.poolSetupUpdated() ;
                      break ;

                      case Event.CALL_DRIVER :
                         Args args  = (Args)event.args[0] ;
                         s.command( args ) ;
                      break ;

                      case Event.TIMER :
                         s.timer() ;
                      break ;

                      case Event.RESET :
                         s.reset() ;
                      break ;

                      case Event.UNLOAD :
                         done = true ;
                      break ;

                   }
               }catch(Throwable t ){
                   _log.warn(_printoutName+": Exception reported by "+event.type+" : "+t, t);
               }
           }
           _log.info(_printoutName+": Worker event loop stopped");
           _log.info(_printoutName+": Preparing unload");

           synchronized( this ){

               if( _scheduler == null )return ;

               try{
                   _scheduler.prepareUnload() ;
               }catch(Throwable t ){
                   _log.warn(_printoutName+": Exception in prepareUnload "+t, t);
               }

               _scheduler = null ;
               _worker    = null ;
               _initDone  = false ;

           }
       }
       private synchronized void flushingDone( HFCFlushInfo info ){
          if( _pipe != null )_pipe.push( new Event( Event.FLUSHING_DONE , info ) ) ;
       }
       private synchronized void poolSetupReady(){
          if( _pipe != null )_pipe.push( new Event( Event.POOL_SETUP_UPDATED , null ) ) ;
       }
       /*
       private synchronized void poolSetupReady(){
           if( ( _scheduler != null ) && ( _pipe != null ) && ! _initDone )
                _pipe.push( new Event( Event.INIT ) ) ;
       }
       */
       private synchronized void poolFlushInfoUpdated( HFCPool pool ){
          if( _pipe != null )_pipe.push( new Event( Event.POOL_FLUSH_INFO_UPDATED , pool ) ) ;
       }
       private synchronized void configuredPoolAdded( String pool ){
          if( _pipe != null )_pipe.push( new Event( Event.CONFIGURED_POOL_ADDED , pool ) ) ;
       }
       private synchronized void configuredPoolRemoved( String pool ){
          if( _pipe != null )_pipe.push( new Event( Event.CONFIGURED_POOL_REMOVED , pool ) ) ;
       }
       private synchronized void callDriver( Args args ){
          if( _pipe != null )_pipe.push( new Event( Event.CALL_DRIVER , args ) ) ;
       }
       private synchronized void poolIoModeUpdated( HFCPool pool ){
          if( _pipe != null )_pipe.push( new Event( Event.POOL_IO_MODE_UPDATED , pool ) ) ;
       }
       private synchronized void propertiesUpdated( Map properties ){
          if( _pipe != null )_pipe.push( new Event( Event.PROPERTIES_UPDATED , properties ) ) ;
       }
       private synchronized void timer(){
          if( _pipe != null )_pipe.push( new Event( Event.TIMER ) ) ;
       }
       private synchronized void reset(){
          if( _pipe != null )_pipe.push( new Event( Event.RESET ) ) ;
       }
       private synchronized void loadHandler( String handlerName , boolean doInit , Args args ) throws Exception {

          if( _scheduler != null )
             throw new
             IllegalArgumentException("Handler already registered");

          Class       c   = Class.forName( handlerName ) ;
          Constructor con = c.getConstructor( _argumentClasses ) ;

          _driverArgs    = args ;
          _schedulerName = handlerName ;

          String [] tmp  = handlerName.split("\\.") ;
          _printoutName  = tmp[ tmp.length - 1 ] ;

          _scheduler = (HsmFlushSchedulable)con.newInstance( _argumentObjects ) ;
          _pipe      = new Pipe() ;
          _worker    = _nucleus.newThread( this , "driver" ) ;
          _worker.start() ;

          /*
          if( doInit ){
             _initDone = true ;
             _pipe.push( new Event( Event.INIT ) ) ;
          }
          */

       }
       private synchronized void unloadHandler(){
          if( _pipe == null )
             throw new
             IllegalArgumentException("No handler active");

          Pipe pipe = _pipe ;
          _pipe = null ;

          pipe.push( new Event( Event.UNLOAD ) ) ;


       }
       private synchronized void something(){
          // if( _pipe != null )_pipe.push( new Event( Event.INIT ) ) ;
       }
       //
       // interface HsmFlushControlCore ...
       //
       public Args getDriverArgs(){ return _driverArgs ; }
       public Pool getPoolByName( String poolName ){
          return _poolCollector.getPoolByName( poolName) ;
       }
       public  List getConfiguredPools(){
          return _poolCollector.getConfiguredPools() ;
       }
       public Set getConfiguredPoolNames(){
          return _poolCollector.getConfiguredPoolNames() ;
       }
    }
    public String hh_driver_reset = " # resets driver " ;
    public String ac_driver_reset( Args args )throws Exception {
         _eventDispatcher.callDriver( args ) ;
         return "Command sent to driver" ;
    }
    public String hh_driver_command = " commands send to driver ... " ;
    public String ac_driver_command_$_0_999( Args args )throws Exception {
         _eventDispatcher.callDriver( args ) ;
         return "Command sent to driver" ;
    }
    public String hh_driver_properties = " OPTIONS : -<key>=<value> ..." ;
    public String ac_driver_properties( Args args )throws Exception {
         Map map = new HashMap() ;
         for( int i = 0 , n = args.optc() ; i < n ; i++ ){
            String key   = args.optv(i) ;
            String value = args.getOpt(key) ;
            map.put( key , value ) ;
         }
         _eventDispatcher.propertiesUpdated( map ) ;
         return "Properties sent to driver, check with 'info'" ;
    }
    public String hh_load_driver = "<driveClassName> [driver arguments and options]" ;
    public String ac_load_driver_$_999( Args args ) throws Exception {
        String driverClass = args.argv(0) ;
        args.shift();
        _eventDispatcher.loadHandler( driverClass , true , args ) ;
        return "Loaded : "+driverClass;
    }
    public String hh_unload_driver = "" ;
    public String ac_unload_driver( Args args )throws Exception {
        _eventDispatcher.unloadHandler() ;
        return "Unload scheduled";
    }
    /////////////////////////////////////////////////////////////////////////////////////////
    //
    //      message arrived handler
    //
    private void poolFlushDoFlushMessageArrived( PoolFlushDoFlushMessage msg ){
        if(_logEnabled)_log.info("poolFlushDoFlushMessageArrived : "+msg);
        String poolName  = msg.getPoolName() ;
        synchronized( _poolCollector ){
            HFCPool pool = _poolCollector.getPoolByName( poolName) ;
            if( pool == null ){
               _log.warn("poolFlushDoFlushMessageArrived : message arrived for non configured pool : "+poolName);
               return ;
            }
            String storageClass = msg.getStorageClassName()+"@"+msg.getHsmName() ;
            HFCFlushInfo info = (HFCFlushInfo)pool.flushInfos.get( storageClass ) ;
            if( info == null ){
               _log.warn("poolFlushDoFlushMessageArrived : message arrived for non existing storage class : "+storageClass);
               //
               // the real one doesn't exists anymore, so we simulate one.
               //
               info = new HFCFlushInfo( pool , new StorageClassFlushInfo( msg.getHsmName() , msg.getStorageClassName() ) ) ;
            }
            if( msg.getReturnCode() != 0 ){
               if(_logEnabled)_log.info("Flush failed (msg="+(msg.isFinished()?"Finished":"Ack")+") : "+msg ) ;

               info.setFlushingFailed(msg.getReturnCode(),msg.getErrorObject());
               //
               // maybe we have to call flushingDone here as well.
               //
               return ;
            }
            if( msg.isFinished() ){
                if(_logEnabled)_log.info("Flush finished : "+msg ) ;

                updateFlushCellAndFlushInfos( msg , pool ) ;
                info.setFlushingDone() ;
                _eventDispatcher.flushingDone(info);
            }else{
                info.setFlushingAck(msg.getFlushId());
            }
        }
    }
    private void poolFlushGainControlMessageDidntArrive( String poolName ){
        if(_logEnabled)_log.info("poolFlushGainControlMessageDidntArrive : "+poolName);
        synchronized( _poolCollector ){
            HFCPool pool = _poolCollector.getPoolByName( poolName) ;
            if( pool == null ){
               _log.warn("PoolFlushGainControlMessage : message arrived for non configured pool : "+poolName);
               return ;
            }
            pool.isActive    = false ;
            pool.lastUpdated = System.currentTimeMillis();
            pool.answerCount ++ ;

            _eventDispatcher.poolFlushInfoUpdated( pool  ) ;
        }

    }
    private void updateFlushCellAndFlushInfos( PoolFlushControlInfoMessage msg , HFCPool pool ){

       pool.cellInfo = msg.getCellInfo() ;
       StorageClassFlushInfo [] flush = msg.getFlushInfos() ;
       HashMap map = new HashMap() ;
       if( flush != null ){
          for( int i = 0 ; i < flush.length ; i ++ ){
             String storageClass = flush[i].getStorageClass()+"@"+flush[i].getHsm() ;

             HFCFlushInfo info = (HFCFlushInfo)pool.flushInfos.get( storageClass ) ;
             if( info == null ){
                  map.put( storageClass , new HFCFlushInfo( pool ,  flush[i] ) ) ;
             }else{
                  info.updateFlushInfo( flush[i] ) ;
                  map.put( storageClass , info ) ;
             }
          }
       }
       pool.flushInfos  = map ;
       pool.isActive    = true ;
       pool.lastUpdated = System.currentTimeMillis();
       pool.answerCount ++ ;

    }
    private void poolFlushGainControlMessageArrived( PoolFlushGainControlMessage msg ){
        if(_logEnabled)_log.info("PoolFlushGainControlMessage : "+msg);
        String poolName  = msg.getPoolName() ;
        synchronized( _poolCollector ){
            HFCPool pool = _poolCollector.getPoolByName( poolName) ;
            if( pool == null ){
               _log.warn("PoolFlushGainControlMessage : message arrived for non configured pool : "+poolName);
               return ;
            }

            updateFlushCellAndFlushInfos( msg , pool ) ;

            _eventDispatcher.poolFlushInfoUpdated( pool ) ;
        }
    }
    private void poolModeInfoArrived( PoolManagerPoolModeMessage msg ){
        if(_logEnabled)_log.info("PoolManagerPoolModeMessage : "+msg ) ;
        String poolName  = msg.getPoolName() ;
        synchronized( _poolCollector ){

            HFCPool pool = _poolCollector.getPoolByName( poolName) ;
            if( pool == null ){
               _log.warn("poolModeInfoArrived : message arrived for non configured pool : "+poolName);
               return ;
            }
            pool.mode = msg.getPoolMode() ;

            _eventDispatcher.poolIoModeUpdated( pool ) ;
        }
    }
    private void poolStatusChanged( PoolStatusChangedMessage msg ){
       String poolName = msg.getPoolName() ;
    }
    public void messageArrived( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       _requests ++ ;
       if( obj instanceof PoolFlushGainControlMessage ){

          poolFlushGainControlMessageArrived( (PoolFlushGainControlMessage) obj ) ;

       }else if( obj instanceof PoolFlushDoFlushMessage ){

          poolFlushDoFlushMessageArrived( (PoolFlushDoFlushMessage) obj )  ;

       }else if( obj instanceof PoolManagerPoolModeMessage ){

          poolModeInfoArrived( (PoolManagerPoolModeMessage) obj )  ;

       }else if( obj instanceof PoolStatusChangedMessage ){

          poolStatusChanged( (PoolStatusChangedMessage) obj )  ;

       }else if( obj instanceof NoRouteToCellException ){

          NoRouteToCellException nrtc = (NoRouteToCellException)obj ;
          CellPath        path = nrtc.getDestinationPath() ;
          CellAddressCore core = path.getDestinationAddress() ;
          String          cellName = core.getCellName() ;
          _log.warn( "NoRouteToCell : "+ cellName + " ("+path+")");

          poolFlushGainControlMessageDidntArrive( cellName ) ;

       }else{
          _log.warn("Unknown message arrived ("+msg.getSourcePath()+") : "+
               msg.getMessageObject() ) ;
         _failed ++ ;
       }
    }
}
