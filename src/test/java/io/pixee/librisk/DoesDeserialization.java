package io.pixee.librisk;

import java.io.IOException;
import java.io.ObjectInputStream;

public class DoesDeserialization {

  public void doesReadObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.readObject();
  }

  private static void doesDefaultReadObject(ObjectInputStream ois)
      throws IOException, ClassNotFoundException {
    System.out.println("another line");
    ois.defaultReadObject();
  }

  private void doesKryo(com.esotericsoftware.kryo.Kryo kryo) {
    String map = kryo.readObject(null, String.class);
  }
}
