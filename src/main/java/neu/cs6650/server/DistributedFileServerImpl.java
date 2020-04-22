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
import neu.cs6650.utils.Constants;
import neu.cs6650.utils.Response;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public class DistributedFileServerImpl extends UnicastRemoteObject implements
    DistributedFileServer {

  private static Logger logger = LogManager.getLogger(DistributedFileServerImpl.class);
  static Set<Integer> serverPorts = new HashSet<>(
      Arrays.asList(7000, 7001, 7002, 7003, 7004));
  Map<Integer, String> fileNameAndNumber;
  private int serverId;
  private String directory;
  private long paxosId = System.currentTimeMillis();
  private int localServerFileCount;

  public DistributedFileServerImpl(int serverPort) throws RemoteException {
    Configurator.setLevel(logger.getName(), Level.ALL);
    serverPorts.remove(serverPort);
    serverId = serverPort;
    fileNameAndNumber = new HashMap<>();
    directory = String.valueOf(serverId);
    File newDir = new File(directory);
    this.delete(newDir);
    if (!newDir.mkdir()) {
      logger.error("Unable to create directory");
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
      logger.error("Failed to delete file : {}", f);
    }
  }

  @Override
  public long prepare(long id) {
    logger.info("Proposal Id at port {} is {} and current server paxos Id is {}", serverId, id,
        paxosId);

    if (this.paxosId > id) {
      throw new PromiseException("Requester Id lower than Acceptor Id");
    }
    logger.info("New paxos id at port {} is {}", serverId, paxosId);
    this.paxosId = id;
    return this.paxosId;
  }

  private Set<Integer> sendPrepare() {

    logger.info("Trying with paxos id {} to get consensus for KVStore at port {} ", paxosId,
        serverId);

    Set<Integer> promisedPorts = new HashSet<>();
    int upServers = 0;

    for (int port : serverPorts) {
      try {
        DistributedFileServer dfs = (DistributedFileServer) LocateRegistry.getRegistry(port)
            .lookup(Constants.SERVER_NAME);
        long response = dfs.prepare(this.paxosId);
        promisedPorts.add(port);
        logger.info("Response received from Server at port {} with id {}", port, response);
        upServers++;
      } catch (RemoteException | NotBoundException e) {
        logger.error("Server at port {} is down", port);
      } catch (PromiseException p) {
        upServers++;
        logger.error(p.getMessage());
      }
    }

    int numPromises = promisedPorts.size() + 1;
    int liveServers = upServers + 1;
    double upPercent = ((double) (numPromises) / liveServers);

    if (upPercent > 0.5) {
      logger.info("Consensus SUCCESS: {} / {} ACCEPTORS promised. Majority reached for {} ",
          numPromises, liveServers, serverId);

      return promisedPorts;
    } else {
      logger.info("Consensus FAILED: {} / {} ACCEPTORS promised. Majority wasn't reached for {} ",
          numPromises, liveServers, serverId);
      // Increase the id in case of consensus failure
      this.paxosId = new Date().getTime();
      return null;
    }
  }

  private void sendUploadAcceptRequest(int port, byte[] data, String fileName) {
    try {
      ((DistributedFileServer) LocateRegistry
          .getRegistry(port).lookup(Constants.SERVER_NAME))
          .acceptRequest(Operation.UPLOAD_FILE, fileName, data);
    } catch (Exception e) {
      logger.error("Failed to Accept Request for KVStore at port {}", port);
    }
  }

  private void sendDeleteAcceptRequest(int port, byte[] data, String fileName) {
    try {
      ((DistributedFileServer) LocateRegistry
          .getRegistry(port).lookup("FileServer"))
          .acceptRequest(Operation.DELETE_FILE, fileName, data);
    } catch (Exception e) {
      logger.error("Failed to Accept Request for KVStore at port {} ", port);
    }
  }

  private void sendRenameAcceptRequest(int port, byte[] data, String fileName) {
    try {
      ((DistributedFileServer) LocateRegistry
          .getRegistry(port).lookup("FileServer"))
          .acceptRequest(Operation.RENAME_FILE, fileName, data);
    } catch (Exception e) {
      logger.error("Failed to Accept Request for KVStore at port {} ", port);
    }
  }


  @Override
  public void acceptRequest(Operation operation, String fileName, byte[] data) throws IOException {
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

  private void writeToFile(String fileName, byte[] data) throws IOException {
    File f = new File(this.directory, fileName);
    FileOutputStream fos = null;
    if (!f.createNewFile()) {
      // log
      return;
    }
    fos = new FileOutputStream(f);
    fos.write(data);
    this.fileNameAndNumber.put(localServerFileCount++, fileName);
    fos.flush();
    logger.info("Upload succeeded for server FileStore at port {}", serverId);
     /*catch (IOException e) {
      logger.error("Upload failure for server FileStore at port {} due to : {}", serverId,
          e.getMessage());
    }*/
    try {
      fos.close();
    } catch (IOException e) {
      logger.error("Unable to close stream");
    }
  }

  @Override
  public Map<Integer, String> getAllFilesOnServer() {
    return this.fileNameAndNumber;
  }

  @Override
  public Response uploadFile(byte[] data, String fileName) throws RemoteException {
    logger.info("Upload request for server FileStore at port {}", serverId);
    Set<Integer> ports = null;
    Response resp = new Response();
    int retries = 0;
    // Retries till it does not get consensus
    while (retries < Constants.RETRY_COUNT) {
      ports = this.sendPrepare();
      retries++;
    }
    if (ports == null) {
      resp.setMessage("UPLOAD FAILED!");
      return resp;
    }
    ports.forEach(port -> this.sendUploadAcceptRequest(port, data, fileName));
    try {
      this.acceptRequest(Operation.UPLOAD_FILE, fileName, data);
      resp.setMessage("UPLOAD SUCCESS!");
    } catch (IOException e) {
      logger.error("Upload failed due to: {}", e.getMessage());
      resp.setMessage("UPLOAD FAILED!");
    }
    return resp;
  }

  @Override
  public Response downloadFile(String fileId) {
    logger.info("File download request for id {} and name {}", fileId,
        fileNameAndNumber.get(Integer.parseInt(fileId)));
    Response resp = new Response();
    File toFetch = new File(directory, this.fileNameAndNumber.get(Integer.parseInt(fileId)));
    if (!toFetch.exists()) {
      logger.error("File Does Not exist on the server");
      resp.setMessage("DOWNLOAD FAILED!. File Does Not exist on the server");
      resp.setDownloadedFile(new byte[]{});
      return resp;
    }
    byte[] downloadedFile = new byte[(int) toFetch.length()];
    FileInputStream fin;
    try {
      fin = new FileInputStream(toFetch);
      fin.read(downloadedFile);
      fin.close();
    } catch (Exception e) {
      logger.error("Error While reading file");
    }
    logger.info("File download succeeded for id {} and name {}", fileId,
        fileNameAndNumber.get(Integer.parseInt(fileId)));

    resp.setMessage("DOWNLOAD SUCCESS!" + fileNameAndNumber.get(Integer.parseInt(fileId)));
    resp.setDownloadedFile(downloadedFile);
    return resp;
  }

  @Override
  public Response deleteFile(String fileId) {
    Response resp = new Response();
    if (true) {
      resp.setMessage("DELETE SUCCESS!");
    } else {
      resp.setMessage("DELETE FAILED!");
    }
    return resp;
  }

  @Override
  public Response renameFile(String fileId, String newFileName, Long duration) {
    Response resp = new Response();
    if (true) {
      resp.setMessage("RENAME SUCCESS! Renamed to: " + newFileName);
    } else {
      resp.setMessage("RENAME FAILED!");
    }
    return resp;
  }
}
