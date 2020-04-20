package neu.cs6650.loadbalancer;


import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Random;
import neu.cs6650.utils.Constants;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * This class represents a Random Loadbalancer. This loadbalancer selects a server port from a given
 * list at random.
 */
public class RandomLoadBalancer extends UnicastRemoteObject implements ILoadBalancer {

  private static Logger logger = LogManager.getLogger(RandomLoadBalancer.class);

  public RandomLoadBalancer() throws RemoteException {

  }

  @Override
  public int getServerPort() {
    int randomInt = new Random().nextInt(Constants.PORTS.length);
    return Constants.PORTS[randomInt];
  }

  public static void main(String[] args) {
    Configurator.setLevel(logger.getName(), Level.ALL);
    logger.info("Round-Robin Load balancer started...");

    int portNumber;
    if (args.length < 1) {
      logger.warn("No port provided, using default port: {}", Constants.DEFAULT_LB_PORT);
      portNumber = Constants.DEFAULT_LB_PORT;
    } else {
      portNumber = Integer.parseInt(args[0]);
    }

    try {
      ILoadBalancer loadBalancer = new RandomLoadBalancer();
      Registry registry = LocateRegistry.createRegistry(portNumber);
      registry.bind(Constants.RANDOM_LOAD_BALANCER, loadBalancer);
      logger.info("Object binding is done");

    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }
}