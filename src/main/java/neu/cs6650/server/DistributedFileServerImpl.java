package neu.cs6650.server;

import java.rmi.RemoteException;
import java.util.Map;

public class DistributedFileServerImpl implements DistributedFileServer {

  public Long prepare(Long id) throws RemoteException {
    return null;
  }

  public void acceptRequest(Operation operation, String fileId, byte[] data)
      throws RemoteException {

  }

  public Map<String, String> getAllFilesOnServer() throws RemoteException {
    return null;
  }

  public void uploadFile(byte[] file) throws RemoteException {

  }

  public byte[] downloadFile(String fileId) throws RemoteException {
    return new byte[0];
  }

  public void deleteFile(String fileId) throws RemoteException {

  }

  public void renameFile(String fileId, String newFileName, Long duration) throws RemoteException {

  }
}
