package neu.cs6650.utils;

import java.io.Serializable;

//The Response class contains information about the response sent from server to client
public class Response implements Serializable {

  private static final long serialVersionUID = 8949718079867932728L;

  // Type of the request to which server is responding
  private String type;

  // return value of the request
  private String returnValue;

  // Message describing what happened on the server side
  private String message;

  private byte[] downloadedFile;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public String toString() {
    return "Response [type=" + type + ", returnValue=" + returnValue + ", message=" + message + "]";
  }

  public void setDownloadedFile(byte[] downloadedFile) {
    this.downloadedFile = downloadedFile;
  }

  public byte[] getDownloadedFile() {
    return downloadedFile;
  }
}