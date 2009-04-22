package org.dcache.pool.repository.meta.file;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.dcache.pool.repository.StickyRecord;
import org.dcache.pool.repository.EntryState;
import org.dcache.pool.repository.MetaDataRecord;
import org.dcache.pool.repository.v3.RepositoryException;
import org.dcache.pool.repository.v3.SiFileCorruptedException;
import org.dcache.pool.repository.v3.entry.CacheRepositoryEntryState;

import com.sun.corba.se.impl.io.OptionalDataException;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.PnfsId;
import diskCacheV111.vehicles.StorageInfo;

public class CacheRepositoryEntryImpl implements MetaDataRecord
{
    private final CacheRepositoryEntryState _state;
    private final PnfsId _pnfsId;
    private int _linkCount = 0;
    private StorageInfo _storageInfo = null;
    private long _creationTime = System.currentTimeMillis();
    private long _lastAccess;
    private long _size;

    /**
     * control file
     */
    private final File _controlFile;

    /**
     * serialized storage info file
     */
    private final File _siFile;

    /**
     * data file
     */
    private final File _dataFile;



    public CacheRepositoryEntryImpl(PnfsId pnfsId, File controlFile, File dataFile, File siFile ) throws IOException, RepositoryException {

        _pnfsId = pnfsId;
        _controlFile = controlFile;
        _siFile = siFile;
        _dataFile = dataFile;

        _state = new CacheRepositoryEntryState(_controlFile);

        try {
            _storageInfo =  readStorageInfo(siFile);
            if( _storageInfo == null ) {
                throw new SiFileCorruptedException("bad SI file for");
            }

            _creationTime = _siFile.lastModified();
        }catch(FileNotFoundException fnf) {
            /*
             * it's not an error state.
             */
        }

        _lastAccess = _dataFile.lastModified();
        _size = _dataFile.length();

        if (_lastAccess == 0) {
            _lastAccess = _creationTime;
        }
    }

    public synchronized void incrementLinkCount() throws CacheException {
        _linkCount++;
    }

    public synchronized void decrementLinkCount() throws CacheException {

        if (_linkCount <= 0)
            throw new IllegalStateException("Link count is already  zero");
        _linkCount--;
    }

    public synchronized int getLinkCount() {
        return _linkCount;
    }

    public synchronized long getCreationTime() {
        return _creationTime;
    }

    public synchronized File getDataFile() throws CacheException {
        return _dataFile;
    }

    public synchronized long getLastAccessTime() {
        return _lastAccess;
    }

    public synchronized PnfsId getPnfsId() {
        return _pnfsId;
    }

    public synchronized void setSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative entry size is not allowed");
        }
        _size = size;
    }

    public synchronized long getSize() {
        return _size;
    }

    public synchronized StorageInfo getStorageInfo() throws CacheException {
        return _storageInfo;
    }

    public synchronized boolean isSticky() {
        return _state.isSticky();
    }

    public synchronized EntryState getState()
    {
        return _state.getState();
    }

    public synchronized void setState(EntryState state)
        throws CacheException
    {
        try {
            _state.setState(state);
        } catch (IOException e) {
            throw new CacheException(e.getMessage());
        }
    }

    public synchronized List<StickyRecord> removeExpiredStickyFlags()
    {
        return _state.removeExpiredStickyFlags();
    }

    public synchronized boolean setSticky(String owner, long expire, boolean overwrite) throws CacheException {
        try {
            if (_state.setSticky(owner, expire, overwrite)) {
                return true;
            }
            return false;

        } catch (IllegalStateException e) {
            throw new CacheException(e.getMessage());
        } catch (IOException e) {
            throw new CacheException(e.getMessage());
        }
    }
    public synchronized void setStorageInfo(StorageInfo storageInfo) throws CacheException {

        ObjectOutputStream objectOut = null;
        File siFileTemp = null;
        try {

            siFileTemp = File.createTempFile(_siFile.getName(), null, _siFile.getParentFile() );

            objectOut = new ObjectOutputStream( new FileOutputStream( siFileTemp) );

            objectOut.writeObject( storageInfo ) ;


        } catch (IOException ioe) {
            // TODO: disk io error code here
            if( siFileTemp != null && siFileTemp.exists() ){
                siFileTemp.delete();
            }
            throw new CacheException(10,_pnfsId+" "+ioe.getMessage() );
        }finally {
            if( objectOut != null ) {
                try {
                    objectOut.close();
                } catch (IOException ignore) {
                }
            }
        }

        if( ! siFileTemp.renameTo(_siFile) ) {
            // TODO: disk io error code here
            throw new CacheException(10,_pnfsId+" rename failed" );
        }

        _storageInfo = storageInfo;
    }

    public synchronized void touch() throws CacheException {

        try{
            if( ! _dataFile.exists() )_dataFile.createNewFile() ;
        }catch(IOException ee){
            throw new
                CacheException("Io Error creating : "+_dataFile ) ;
        }

        _lastAccess = System.currentTimeMillis();
        _dataFile.setLastModified(_lastAccess);
    }


    private static StorageInfo readStorageInfo(File objIn) throws IOException
    {
        try {
            ObjectInputStream in =
                new ObjectInputStream(new BufferedInputStream(new FileInputStream(objIn)));
            try {
                return (StorageInfo) in.readObject();
            } finally {
                try {
                    in.close();
                } catch (IOException we) {
                    // close on read can be ignored
                }
            }
        } catch (ClassNotFoundException cnf) {

        } catch (InvalidClassException ife) {
            // valid exception if siFIle is broken
        } catch( StreamCorruptedException sce ) {
            // valid exception if siFIle is broken
        } catch (OptionalDataException ode) {
            // valid exception if siFIle is broken
        } catch (EOFException eof){
            // object file size mismatch
        }
        return null;
    }

    public synchronized List<StickyRecord> stickyRecords() {
        return _state.stickyRecords();
    }

    public synchronized String toString(){

        return _pnfsId.toString()+
            " <"+_state.toString()+"-"+
            "(0)"+
            "["+_linkCount+"]> "+
            getSize()+
            " si={"+(_storageInfo==null?"<unknown>":_storageInfo.getStorageClass())+"}" ;
    }

}
