package neu.cs6650.server;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;


public interface DistributedFileServer extends Remote {

  /**
   * Used to get promise from acceptors
   *
   * @param id to propose
   * @return same id if successful else null
   * @throws RemoteException upon failure
   */
  long prepare(long id) throws RemoteException;

  /**
   * Perform the operation promised
   *
   * @param operation Accept a file, delete a file, rename a file
   * @param fileName to perform the operation on
   * @param data file data, null in case of delete, new file name in case of rename and entire file
   * in case of accept
   * @throws RemoteException upon failure
   */
  void acceptRequest(Operation operation, String fileName, byte[] data) throws RemoteException;

  /**
   * Fetches all files from the neu.cs6650.server
   *
   * @return map of id and names
   * @throws RemoteException upon failure
   */
  Map<Integer, String> getAllFilesOnServer() throws RemoteException;

  /**
   * Upload a file from the neu.cs6650.client
   *
   * @param fileName to be set
   * @param fileContent to be uploaded from client
   * @throws RemoteException upon failure
   */
  void uploadFile(byte[] fileContent, String fileName) throws RemoteException;

  /**
   * Download a file
   *
   * @param fileName to be downloaded
   * @return the file in byte
   * @throws RemoteException upon failure
   */
  byte[] downloadFile(String fileName) throws RemoteException;

  /**
   * Delete file from neu.cs6650.server
   *
   * @param fileId of file to be deleted
   * @throws RemoteException upon failure
   */
  void deleteFile(String fileId) throws RemoteException;

  /**
   * Rename file on the neu.cs6650.server
   *
   * @param fileId      of file to be deleted
   * @param newFileName of the file to be renamed
   * @throws RemoteException upon failure
   */
  void renameFile(String fileId, String newFileName, Long duration) throws RemoteException;
}
