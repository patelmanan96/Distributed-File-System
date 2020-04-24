package neu.cs6650.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.Scanner;
import neu.cs6650.loadbalancer.ILoadBalancer;
import neu.cs6650.server.DistributedFileServer;
import neu.cs6650.utils.Constants;
import neu.cs6650.utils.Response;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public class Client implements IClient {

  private static DistributedFileServer fileServer;
  private ILoadBalancer loadBalancer;

  private String address;
  private int port;
  private Map<Integer, String> fileList;
  private Scanner reader;
  private Logger logger = LogManager.getLogger(this.getClass());

  public Client(String address) {
    Configurator.setLevel(logger.getName(), Level.ALL);
    this.address = address;
    this.reader = new Scanner(new InputStreamReader(System.in));
  }

  public static void main(String[] args) {
    String ip;
    if (args.length < 1) {
      ip = Constants.IP;
    } else {
      ip = args[0];
    }
    Client client = new Client(ip);
    client.start();

  }

  @Override
  public void start() {

    while (true) {
      try {
        Registry registry = LocateRegistry.getRegistry(Constants.DEFAULT_LB_PORT);
        this.loadBalancer = (ILoadBalancer) registry.lookup(Constants.RR_LOAD_BALANCER);

        this.port = this.loadBalancer.getServerPort();
        fileServer = (DistributedFileServer) Naming
            .lookup("//" + address + ":" + port + "/" + Constants.SERVER_NAME);
        logger.info("Remote connection established. Host: {} Port: {}", address, port);

        int command = 0;

        while (command != Constants.EXIT) {
          try {
            System.out.println(getMenu());
            System.out.println("Enter your choice:");
            // Reads the request command from the user
            command = reader.nextInt();
            reader.nextLine();
            try {
              String response = this.executeCommand(command);
              logger.info("Response: {}", response);
            } catch (FileNotFoundException e) {
              logger.error("Could not find file. {}", e.getMessage());
            } catch (IOException e) {
              logger.error("Error occurred while reading/writing to file. {}", e.getMessage());
            } catch (IllegalArgumentException e) {
              logger.error(e.getMessage());
            }
          } catch (Exception e) {
            logger.error(e);
            logger.info("Attempting to re-establish connection.");
            break;
          }

        }

        if (command == Constants.EXIT) {
          break;
        }
      } catch (Exception e) {
        logger.error(e);
      }
    }
  }

  /**
   * Method to fetch the menu options.
   *
   * @return String
   */
  private String getMenu() {
    return "1. List all files" + "\n"
        + "2. Upload file" + "\n"
        + "3. Download file" + "\n"
        + "4. Delete file" + "\n"
        + "5. Rename file" + "\n"
        + "6. Exit" + "\n";
  }

  /**
   * Method to execute commands entered by the user.
   *
   * @param command integer representing command id.
   * @return String response.
   */
  private String executeCommand(int command)
      throws RemoteException, FileNotFoundException, IOException {
    Response resp;
    switch (command) {
      case 1: {
        this.fileList = fileServer.getAllFilesOnServer();
        System.out.println(fileList);
        return "File list fetched successfully.";
      }
      case 2: {
        System.out.println("Enter path of the file to be uploaded: ");
        String filePath = this.reader.nextLine().trim();
        byte[] file = this.getFileFromUser(filePath);
        resp = fileServer.uploadFile(file, this.getFileNameFromPath(filePath));
        return resp.getMessage();
      }

      case 3: {
        System.out.println("Enter fileId: ");
        String fileId = this.reader.nextLine().trim();
        resp = fileServer.downloadFile(fileId);
        String savedPath = saveDownloadedFile(fileId, resp.getDownloadedFile());
        return resp.getMessage() + "Saved file at: " + savedPath;
      }

      case 4: {
        System.out.println("Enter fileId: ");
        String fileId = this.reader.nextLine();
        resp = fileServer.deleteFile(fileId);
        return resp.getMessage();
      }

      case 5: {
        System.out.println("Enter fileId: ");
        String fileId = this.reader.nextLine();
        System.out.println("Enter new name of file: ");
        String newFileName = this.reader.nextLine();
        System.out.println("Enter duration: ");
        long duration = this.reader.nextLong();
        resp = fileServer.renameFile(fileId, newFileName, duration);
        return resp.getMessage();
      }

      case 6: {
        return "Exiting...";
      }

      default: {
        throw new IllegalArgumentException("Invalid choice.");
      }
    }
  }

  private String getFileNameFromPath(String path) {
    if (path.lastIndexOf('/') != -1) {
      return path.substring(path.lastIndexOf('/') + 1);
    } else {
      return path.substring(path.lastIndexOf('\\') + 1);
    }
  }

  /**
   * Method to save a downloaded file.
   *
   * @param fileId         id of the file.
   * @param downloadedFile downloaded file as a byte[] array.
   * @return returns download path of the saved file.
   */
  private String saveDownloadedFile(String fileId, byte[] downloadedFile)
      throws FileNotFoundException, IOException {
    String serverFilePath = this.fileList.get(Integer.parseInt(fileId));
    String fileName = this.getFileNameFromPath(serverFilePath);
    System.out.println("Enter path to save file: ");
    String savePath = this.reader.nextLine();

    savePath += savePath.lastIndexOf('/') < (savePath.length() - 1) ? (savePath + "/") : "";
    savePath += fileName;

    File file = new File(savePath);
    FileOutputStream fos;
    fos = new FileOutputStream(file);
    fos.write(downloadedFile);

    return savePath;
  }

  /**
   * Method to fetch file to be uploaded to the server from a user.
   *
   * @return file to be uploaded as a byte array.
   */
  private byte[] getFileFromUser(String filePath) throws FileNotFoundException {
    File file = new File(filePath);
    FileInputStream fin = null;
    // create FileInputStream object
    fin = new FileInputStream(file);
    // close the streams using close method
    byte[] buffer = new byte[(int) file.length()];
    try {
      if (fin != null) {
        fin.read(buffer);
        fin.close();
      }
    } catch (IOException ioe) {
      System.out.println("Error while closing stream: " + ioe);
    }
    return buffer;
  }
} 

