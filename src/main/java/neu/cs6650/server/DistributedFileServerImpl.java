package neu.cs6650.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
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

  private static Set<Integer> serverPorts = new HashSet<>(
      Arrays.asList(7000, 7001, 7002, 7003, 7004));
  private static Logger logger = LogManager.getLogger(DistributedFileServerImpl.class);
  private Map<Integer, String> fileNameAndNumber;
  private int serverId;
  private String directory;
  private long paxosId = System.currentTimeMillis();
  private int localServerFileCount;
  private Response renameResponse = new Response();
  Set<Integer> lockedFileIds = new HashSet<Integer>();

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

    logger.info("Trying with paxos id {} to get consensus for FileServer at port {} ", paxosId,
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
      logger.error("Failed to Accept Request for FileServer at port {}", port);
    }
  }

  private void sendDeleteAcceptRequest(int port, String fileName) {
    try {
      ((DistributedFileServer) LocateRegistry
          .getRegistry(port).lookup("FileServer"))
          .acceptRequest(Operation.DELETE_FILE, fileName, null);
    } catch (Exception e) {
      logger.error("Failed to Accept Request for FileServer at port {} ", port);
    }
  }

  private void sendRenameAcceptRequest(int port, byte[] data, String fileName) {
    try {
      ((DistributedFileServer) LocateRegistry
          .getRegistry(port).lookup("FileServer"))
          .acceptRequest(Operation.RENAME_FILE, fileName, data);
    } catch (Exception e) {
      logger.error("Failed to Accept Request for FileServer at port {} ", port);
    }
  }


  @Override
  public void acceptRequest(Operation operation, String fileName, byte[] data) throws IOException {
    if (operation == Operation.UPLOAD_FILE) {
      this.writeToFile(fileName, data);
    } else if (operation == Operation.DELETE_FILE) {
      this.deleteFileWithName(fileName);
    } else {
      this.renameFileWithName(fileName, data);
    }
  }


  private void deleteFileWithName(String fileName) throws IOException {
    File f = new File(this.directory, fileName);
    if (f.delete()) {
      this.fileNameAndNumber.entrySet().removeIf(entry -> entry.getValue().equals(fileName));
      logger.info("Successfully deleted file at FileServer {} with name {}", serverId, fileName);
    } else {
      throw new IOException("Unable to Delete File with name : " + fileName);
    }
  }

  private void renameFileWithName(String fileName, byte[] data) throws FileNotFoundException {

    File file = new File(this.directory, fileName);
    String str = new String(data);
    String newName = str.split(",")[0];
    long duration = Long.parseLong(str.split(",")[1]);
    int fileId = Integer.parseInt(str.split(",")[2]);
    FileChannel fc;
    RandomAccessFile randomAccessFile = null;

    boolean fileLocked = true;
    try {
      randomAccessFile = new RandomAccessFile(fileName, "rw");
    } catch (FileNotFoundException e) {
      logger.error("Cannot find file for renaming.");
      renameResponse.setMessage("Cannot find file for renaming.");
    }

    fc = randomAccessFile.getChannel();

    try {
      FileLock fileLock = fc.tryLock();
      if (null != fileLock) {
        logger.info("Renaming {} to {}", fileName, newName);
        fileLocked = false;
        Thread.sleep(duration);
      } else {
        logger.info("File is locked for renaming");
      }
    } catch (OverlappingFileLockException | IOException ex) {
      logger.error("File locked for renaming. Try again later.");
      renameResponse.setMessage("File locked for renaming. Try again later.");
    } catch (InterruptedException e) {
      logger.error("Interrupted exception occurred");
      renameResponse.setMessage("Interrupted exception occurred.");
    }
    if (!fileLocked) {
      try {
        fc.close();
        logger.info("Releasing lock on file.");
        this.lockedFileIds.remove(fileId);
      } catch (IOException e) {
        logger.error("IO exception occurred");
        renameResponse.setMessage("IO exception occurred.");
      }
      File fileWithNewName = new File(file.getParent(), newName);
      boolean success = file.renameTo(fileWithNewName);
      if (!success) {
        logger.error("Something went wrong while renaming");
        renameResponse.setMessage("Something went wrong while renaming file.");
      } else {
        this.fileNameAndNumber.remove(fileId);
        this.fileNameAndNumber.put(fileId, newName);
        this.lockedFileIds.add(fileId);
        renameResponse.setMessage("Rename successful.");
      }
    } else {
      try {
        fc.close();
        logger.info("Releasing lock on file.");
      } catch (IOException e) {
        logger.error("Error occurred while closing FileChannel.");
        renameResponse.setMessage("Something went wrong while renaming file.");
      }
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
    while (ports == null && retries < Constants.RETRY_COUNT) {
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
    logger.info("Delete request for server FileStore at port {}", serverId);
    Set<Integer> ports = null;
    Response resp = new Response();
    int retries = 0;
    // Retries till it does not get consensus
    if (this.lockedFileIds.contains(fileId)) {
      resp.setMessage("File locked, cannot delete. Try again later.");
      return resp;
    }
    while (ports == null && retries < Constants.RETRY_COUNT) {
      ports = this.sendPrepare();
      retries++;
    }
    if (ports == null) {
      resp.setMessage("DELETE FAILED!");
      return resp;
    }
    String fileName = this.fileNameAndNumber.get(Integer.parseInt(fileId));
    if (fileName == null) {
      logger.error("Invalid File Id to delete : {}", fileId);
      resp.setMessage("DELETE FAILED! Invalid File Id Provided");
      return resp;
    }
    ports.forEach(port -> this.sendDeleteAcceptRequest(port, fileName));
    try {
      this.acceptRequest(Operation.DELETE_FILE, fileName, null);
      resp.setMessage("DELETE SUCCESS!");
    } catch (IOException e) {
      logger.error("DELETE failed due to: {}", e.getMessage());
      resp.setMessage("DELETE FAILED!");
    }
    return resp;
  }

  @Override
  public Response renameFile(String fileId, String newFileName, Long duration) {
    logger.info("Rename request for server FileStore at port {}", serverId);
    Set<Integer> ports = null;
    int retries = 0;
    String fileName = this.fileNameAndNumber.get(Integer.parseInt(fileId));

    while (ports == null && retries < Constants.RETRY_COUNT) {
      ports = this.sendPrepare();
      retries++;
    }

    if (ports == null) {
      renameResponse.setMessage("RENAME FAILED!");
      return renameResponse;
    }
    String str = newFileName.concat(",").concat(String.valueOf(duration)).concat(",")
        .concat(fileId);
    byte[] data = str.getBytes();
    ports.forEach(port -> this.sendRenameAcceptRequest(port, data, fileName));
    try {
      this.acceptRequest(Operation.RENAME_FILE, fileName, data);
    } catch (IOException e) {
      logger.error("Rename failed due to: {}", e.getMessage());
      renameResponse.setMessage("RENAME FAILED!");
    }
    this.lockedFileIds.remove(str.split(",")[2]);
    return renameResponse;
  }
}
