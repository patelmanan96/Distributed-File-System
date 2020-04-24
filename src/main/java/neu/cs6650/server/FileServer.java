package neu.cs6650.server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import neu.cs6650.utils.Constants;

public class FileServer {

  private static Logger logger = Logger.getLogger(FileServer.class.getName());

  public static void main(String[] args) throws Exception {
    Scanner sc = new Scanner(System.in);

    System.out.print("Enter the port number which is not already used [7000 - 7004] : ");
    int pNo = Integer.parseInt(sc.next());

    Registry registry = LocateRegistry.createRegistry(pNo);
    logger.log(Level.INFO, "Registry created at port : {0}", pNo);

    registry.bind("FileServer", new DistributedFileServerImpl(pNo));

    logger.log(Level.INFO, "Bound at registry port {0} with name {1}", new Object[]{pNo,
        Constants.SERVER_NAME});
  }
}
