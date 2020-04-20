package neu.cs6650.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DistributedFileServerImpl extends UnicastRemoteObject implements
    DistributedFileServer {

  private static final Logger logger = Logger.getLogger(DistributedFileServerImpl.class.getName());
  static Set<Integer> serverPorts = new HashSet<Integer>(
      Arrays.asList(7000, 7001, 7002, 7003, 7004));
  Map<Integer, String> fileNameAndNumber;
  private int serverId;
  private String directory;
  private long paxosId = System.currentTimeMillis();
  private int localServerFileCount;

  public DistributedFileServerImpl(int serverPort) throws RemoteException {
    serverPorts.remove(serverPort);
    serverId = serverPort;
    fileNameAndNumber = new HashMap<>();
    directory = String.valueOf(serverId);
    File newDir = new File(directory);
    this.delete(newDir);
    if (!newDir.mkdir()) {
      logger.log(Level.SEVERE, "Unable to create directory");
      throw new RuntimeException();
    }
    localServerFileCount = 0;
  }

  private void delete(File f) {
    if (f.isDirectory()) {
      for (File c : f.listFiles()) {
        delete(c);
      }
    }
    if (!f.delete()) {
      logger.log(Level.SEVERE, "Failed to delete file : {0}", f);
    }
  }

  @Override
  public long prepare(long id) {
    logger.log(Level.INFO, "Proposal Id at port {0} is {1} and current server paxos Id is {2}",
        new Object[]{serverId, id, paxosId});

    if (this.paxosId > id) {
      throw new PromiseException("Requester Id lower than Acceptor Id");
    }
    logger.log(Level.INFO, "New paxos id at port {0} is {1}",
        new Object[]{serverId, paxosId});
    this.paxosId = id;

    return this.paxosId;
  }

  private Set<Integer> sendPrepare() {

    logger.log(Level.INFO, "Trying with paxos id {0} to get consensus for KVStore at port "
        + "{1} ", new Object[]{paxosId, serverId});

    Set<Integer> promisedPorts = new HashSet<>();
    int upServers = 0;

    for (int port : serverPorts) {
      try {
        DistributedFileServer dfs = (DistributedFileServer) LocateRegistry.getRegistry(port)
            .lookup("FileServer");
        long response = dfs.prepare(this.paxosId);
        promisedPorts.add(port);
        logger.log(Level.INFO, "Response received from Server at port {0} with id {1}",
            new Object[]{port, response});
        upServers++;
      } catch (RemoteException | NotBoundException e) {
        logger.log(Level.SEVERE, "Server at port {0} is down", port);
      } catch (PromiseException p) {
        upServers++;
        logger.log(Level.SEVERE, p.getMessage());
      }
    }

    int promisedNumber = promisedPorts.size() + 1;
    int liveServers = upServers + 1;
    double upPercent = ((double) (promisedNumber) / liveServers);

    if (upPercent > 0.5) {
      logger.log(Level.INFO, "Successful consensus for KVStore at port {0} ", serverId);
      return promisedPorts;
    } else {
      logger.log(Level.SEVERE, "Unable to get consensus for KVStore at port {0} ", serverId);
      // Increase the id in case of consensus failure
      this.paxosId = new Date().getTime();
      return null;
    }
  }

  private void sendUploadAcceptRequest(int port, byte[] data, String fileName) {
    try {
      ((DistributedFileServer) LocateRegistry
          .getRegistry(port).lookup("FileServer"))
          .acceptRequest(Operation.UPLOAD_FILE, fileName, data);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Failed to Accept Request for KVStore at port {0} ", port);
    }
  }

  private void sendDeleteAcceptRequest(int port, byte[] data, String fileName) {
    try {
      ((DistributedFileServer) LocateRegistry
          .getRegistry(port).lookup("FileServer"))
          .acceptRequest(Operation.DELETE_FILE, fileName, data);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Failed to Accept Request for KVStore at port {0} ", port);
    }
  }

  private void sendRenameAcceptRequest(int port, byte[] data, String fileName) {
    try {
      ((DistributedFileServer) LocateRegistry
          .getRegistry(port).lookup("FileServer"))
          .acceptRequest(Operation.RENAME_FILE, fileName, data);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Failed to Accept Request for KVStore at port {0} ", port);
    }
  }


  @Override
  public void acceptRequest(Operation operation, String fileName, byte[] data) {
    if (operation == Operation.UPLOAD_FILE) {
      this.writeToFile(fileName, data);
    } else if (operation == Operation.DELETE_FILE) {
      this.deleteFileWithName(fileName);
    } else {
      // rename logic
    }
  }


  private void deleteFileWithName(String fileName) {
    File f = new File(this.directory, fileName);
    if (f.delete()) {
      //log
    } else {
      //log
    }
  }

  private void writeToFile(String fileName, byte[] data) {
    File f = new File(this.directory, fileName);
    FileOutputStream fos = null;
    try {
      if (!f.createNewFile()) {
        // log
        return;
      }
      fos = new FileOutputStream(f);
      fos.write(data);
      this.fileNameAndNumber.put(localServerFileCount++, fileName);
      fos.flush();
      logger.log(Level.INFO, "Upload succeeded for server FileStore at port {0}", serverId);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Upload failure for server FileStore at port {0} due to : {1}",
          new Object[]{serverId, e.getMessage()});
    } finally {
      try {
        if (fos != null) {
          fos.close();
        }
      } catch (IOException e) {
        logger.log(Level.SEVERE, "Unable to close stream");
      }
    }
  }

  @Override
  public Map<Integer, String> getAllFilesOnServer() {
    return this.fileNameAndNumber;
  }

  @Override
  public void uploadFile(byte[] data, String fileName) throws RemoteException {
    logger.log(Level.INFO, "Upload request for server FileStore at port {0}", serverId);
    Set<Integer> ports = null;
    // Retries till it does not get consensus
    while (ports == null) {
      ports = this.sendPrepare();
    }
    ports.forEach(port -> this.sendUploadAcceptRequest(port, data, fileName));
    this.acceptRequest(Operation.UPLOAD_FILE, fileName, data);
  }

  @Override
  public byte[] downloadFile(String fileId) {
    logger.log(Level.INFO, "File download request for id {0} and name {1}",
        new Object[]{fileId, fileNameAndNumber.get(Integer.parseInt(fileId))});
    File toFetch = new File(directory, this.fileNameAndNumber.get(Integer.parseInt(fileId)));
    if (!toFetch.exists()) {
      logger.log(Level.SEVERE, "File Does Not exist on the server");
      return new byte[]{};
    }
    byte[] downloadedFile = new byte[(int) toFetch.length()];
    FileInputStream fin = null;
    try {
      fin = new FileInputStream(toFetch);
      fin.read(downloadedFile);
      fin.close();
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error While reading file");
    }
    logger.log(Level.INFO, "File download succeeded for id {0} and name {1}",
        new Object[]{fileId, fileNameAndNumber.get(Integer.parseInt(fileId))});
    return downloadedFile;
  }

  @Override
  public void deleteFile(String fileId) {

  }

  @Override
  public void renameFile(String fileId, String newFileName, Long duration) {

  }
}
