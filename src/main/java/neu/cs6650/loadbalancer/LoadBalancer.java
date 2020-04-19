package neu.cs6650.loadbalancer;


import java.util.Random;
import neu.cs6650.utils.Constants;

public class LoadBalancer implements ILoadBalancer {

  @Override
  public int getServerPort() {
    int randomInt = new Random().nextInt(Constants.PORTS.length);
    return Constants.PORTS[randomInt];
  }
}
