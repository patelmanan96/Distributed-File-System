package neu.cs6650.loadbalancer;


import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import neu.cs6650.utils.Constants;

/**
 * This class represents a Random Loadbalancer. This loadbalancer selects a server port from a given
 * list at random.
 */
public class RandomLoadBalancer extends UnicastRemoteObject implements ILoadBalancer {

  private static Logger logger = Logger.getLogger(RandomLoadBalancer.class.getName());;

  public RandomLoadBalancer() throws RemoteException {

  }

  @Override
  public int getServerPort() {
    List<Integer> liveServers = this.getLiveServers();
    if (liveServers.isEmpty()) {
      throw new IllegalStateException("No live servers detected.");
    }
    int randomInt = new Random().nextInt(liveServers.size());
    return liveServers.get(randomInt);
  }

  private List<Integer> getLiveServers() {
    List<Integer> foundServers = new ArrayList<>();
    for (int p : Constants.PORTS) {
      try {
        Registry discoveredRegistry = LocateRegistry.getRegistry(Constants.IP, p);
        if (discoveredRegistry.list().length > 0) {
          foundServers.add(p);
        }
      } catch (Exception e) {
        System.out.println("Waiting for other servers...");
      }
    }
    return foundServers;
  }

  public static void main(String[] args) {
    logger.info("Round-Robin Load balancer started...");

    int portNumber;
    if (args.length < 1) {
      logger.log(Level.WARNING, "No port provided, using default port: {0}",
          Constants.DEFAULT_LB_PORT);
      portNumber = Constants.DEFAULT_LB_PORT;
    } else {
      portNumber = Integer.parseInt(args[0]);
    }

    try {
      ILoadBalancer loadBalancer = new RandomLoadBalancer();
      Registry registry = LocateRegistry.createRegistry(portNumber);
      registry.bind(Constants.RANDOM_LOAD_BALANCER, loadBalancer);
      logger.log(Level.INFO, "Object binding is done");

    } catch (Exception e) {
      logger.log(Level.SEVERE, e.getMessage());
    }
  }
}
