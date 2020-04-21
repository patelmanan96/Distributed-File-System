package neu.cs6650.server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import neu.cs6650.utils.Constants;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public class FileServer {

  private static Logger logger = LogManager.getLogger(FileServer.class);

  public static void main(String[] args) throws Exception {
    Configurator.setLevel(logger.getName(), Level.ALL);
    Scanner sc = new Scanner(System.in);

    System.out.print("Enter the port number which is not already used [7000 - 7004] : ");
    int pNo = Integer.parseInt(sc.next());

    Registry registry = LocateRegistry.createRegistry(pNo);
    logger.info("Registry created at port : {}", pNo);

    registry.bind("FileServer", new DistributedFileServerImpl(pNo));

    logger.info("Binded at registry port {} with name {}", pNo, Constants.SERVER_NAME);
  }
}
